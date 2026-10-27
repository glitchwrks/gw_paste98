/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.sun.source.doctree.BlockTagTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

/**
 * Second Java 1.3 API check, from the JDK's own documentation. Check13 uses
 * animal-sniffer's java13 signature, which also lists non-public members and
 * so let a constructor that only became public API in Java 1.4
 * (FileOutputStream(File, boolean)) through. This check reads the @since tags
 * in the build JDK's src.zip, resolves every JDK method, constructor and
 * field the jar uses to the class that declares it (using the build JDK's
 * rt.jar), and fails if that member, or its class, is documented as added
 * after Java 1.3. Calls on members with no public or protected declaration
 * are reported too.
 *
 * Usage: Since13 JAR src.zip rt.jar
 */
public final class Since13 {

    /** "java/io/FileOutputStream" -> since of the type, and member keys -> since. */
    static final Map<String, String> TYPE_SINCE = new HashMap<String, String>();
    /** "owner#name(Simple,Simple[])" or "owner#field" -> since ("" if untagged). */
    static final Map<String, String> MEMBER_SINCE = new HashMap<String, String>();
    /** "owner#name/arity" -> list of since values of the overloads. */
    static final Map<String, List<String>> BY_ARITY = new HashMap<String, List<String>>();
    /** Members declared public or protected in source, keyed as MEMBER_SINCE. */
    static final Set<String> VISIBLE = new java.util.HashSet<String>();

    private static ZipFile rt;
    private static final Map<String, ClassNode> RT_CACHE = new HashMap<String, ClassNode>();

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: Since13 JAR src.zip rt.jar");
            System.exit(2);
        }
        long t0 = System.currentTimeMillis();
        readSources(new File(args[1]));
        rt = new ZipFile(args[2]);
        Map<String, Set<String>> problems = new TreeMap<String, Set<String>>();
        int refs = 0;
        ZipFile jar = new ZipFile(args[0]);
        try {
            Set<String> own = new java.util.HashSet<String>();
            for (Enumeration<? extends ZipEntry> e = jar.entries(); e.hasMoreElements();) {
                String n = e.nextElement().getName();
                if (n.endsWith(".class")) {
                    own.add(n.substring(0, n.length() - 6));
                }
            }
            for (Enumeration<? extends ZipEntry> e = jar.entries(); e.hasMoreElements();) {
                ZipEntry entry = e.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                ClassNode cn = read(jar.getInputStream(entry));
                checkType(cn.superName, cn.name, own, problems);
                for (String itf : cn.interfaces) {
                    checkType(itf, cn.name, own, problems);
                }
                for (MethodNode m : cn.methods) {
                    for (AbstractInsnNode x = m.instructions.getFirst(); x != null; x = x.getNext()) {
                        if (x instanceof MethodInsnNode) {
                            MethodInsnNode mi = (MethodInsnNode) x;
                            if (isJdk(mi.owner, own)) {
                                refs++;
                                checkMethod(mi.owner, mi.name, mi.desc, cn.name, problems);
                            }
                        } else if (x instanceof FieldInsnNode) {
                            FieldInsnNode fi = (FieldInsnNode) x;
                            if (isJdk(fi.owner, own)) {
                                refs++;
                                checkField(fi.owner, fi.name, cn.name, problems);
                            }
                        } else if (x.getOpcode() == Opcodes.NEW || x.getOpcode() == Opcodes.CHECKCAST
                                || x.getOpcode() == Opcodes.INSTANCEOF || x.getOpcode() == Opcodes.ANEWARRAY) {
                            String t = ((org.objectweb.asm.tree.TypeInsnNode) x).desc;
                            if (!t.startsWith("[")) {
                                checkType(t, cn.name, own, problems);
                            }
                        }
                    }
                }
            }
        } finally {
            jar.close();
            rt.close();
        }
        for (Map.Entry<String, Set<String>> p : problems.entrySet()) {
            System.err.println("Since13: " + p.getKey() + " (used by " + p.getValue().size() + ": "
                    + p.getValue().iterator().next() + ")");
        }
        if (!problems.isEmpty()) {
            System.err.println("Since13: FAILED: " + problems.size() + " JDK APIs newer than Java 1.3");
            System.exit(1);
        }
        System.out.println("Since13: " + new File(args[0]).getName() + ": " + refs
                + " JDK references, none documented as newer than Java 1.3 (" + MEMBER_SINCE.size()
                + " JDK members read in " + (System.currentTimeMillis() - t0) + " ms)");
    }

    static boolean isJdk(String owner, Set<String> own) {
        return !owner.startsWith("[") && !own.contains(owner)
                && (owner.startsWith("java/") || owner.startsWith("javax/") || owner.startsWith("org/omg/")
                        || owner.startsWith("org/w3c/") || owner.startsWith("org/xml/"));
    }

    // ---- checks ------------------------------------------------------------

    static void checkType(String type, String from, Set<String> own, Map<String, Set<String>> problems) {
        if (type == null || !isJdk(type, own)) {
            return;
        }
        String s = TYPE_SINCE.get(type);
        if (newerThan13(s)) {
            add(problems, "class " + type + " is @since " + s, from);
        }
    }

    static void checkMethod(String owner, String name, String desc, String from, Map<String, Set<String>> problems)
            throws IOException {
        Map<String, Set<String>> mine = new TreeMap<String, Set<String>>();
        checkMethodIn(owner, name, desc, from, mine);
        if (!mine.isEmpty() && !name.equals("<init>") && !name.equals("<clinit>")) {
            // A newer re-declaration (e.g. JComponent.requestFocus(), 1.4) of a method a
            // superclass already had in 1.3 (Component.requestFocus()) still resolves on 1.3.
            String declaring = findDeclaring(owner, name, desc);
            ClassNode cn = declaring == null ? null : rtClass(declaring);
            String sup = cn == null ? null : cn.superName;
            String older = sup == null ? null : findDeclaring(sup, name, desc);
            if (older != null) {
                Map<String, Set<String>> again = new TreeMap<String, Set<String>>();
                checkMethodIn(older, name, desc, from, again);
                if (again.isEmpty()) {
                    return;
                }
            }
        }
        for (Map.Entry<String, Set<String>> e : mine.entrySet()) {
            for (String f : e.getValue()) {
                add(problems, e.getKey(), f);
            }
        }
    }

    static void checkMethodIn(String owner, String name, String desc, String from, Map<String, Set<String>> problems)
            throws IOException {
        String declaring = name.equals("<init>") || name.equals("<clinit>") ? owner : findDeclaring(owner, name, desc);
        if (declaring == null) {
            add(problems, "no declaration of " + owner + "." + name + desc + " in the JDK", from);
            return;
        }
        String typeSince = TYPE_SINCE.get(declaring);
        if (newerThan13(typeSince)) {
            add(problems, "class " + declaring + " is @since " + typeSince + " (" + name + ")", from);
            return;
        }
        if (!TYPE_SINCE.containsKey(declaring)) {
            return; // not a documented public type (e.g. sun.*-backed internals): nothing to compare
        }
        String simple = name.equals("<init>") ? simpleName(declaring) : name;
        String key = declaring + "#" + simple + "(" + simpleParams(desc) + ")";
        String since;
        boolean visible;
        if (MEMBER_SINCE.containsKey(key)) {
            since = MEMBER_SINCE.get(key);
            visible = VISIBLE.contains(key);
        } else {
            // Generic parameters erase differently from their source names: match by arity.
            List<String> overloads = BY_ARITY.get(declaring + "#" + simple + "/" + Type.getArgumentTypes(desc).length);
            if (overloads == null) {
                if (name.equals("<init>") && Type.getArgumentTypes(desc).length == 0) {
                    return; // implicit default constructor
                }
                if (isEnumOrSynthetic(name)) {
                    return;
                }
                add(problems, "no documented " + declaring + "." + name + desc + " (not public API in this JDK)",
                        from);
                return;
            }
            since = null;
            for (String s : overloads) {
                if (newerThan13(s)) {
                    since = s;
                }
            }
            visible = true;
            if (since != null) {
                add(problems, declaring + "." + name + desc + ": an overload is @since " + since + " (check)", from);
            }
            return;
        }
        if (!visible) {
            add(problems, declaring + "." + name + desc + " is not public or protected", from);
        } else if (newerThan13(since)) {
            add(problems, declaring + "." + name + desc + " is @since " + since, from);
        }
    }

    static boolean isEnumOrSynthetic(String name) {
        return name.startsWith("access$") || name.equals("values") || name.equals("valueOf");
    }

    static void checkField(String owner, String name, String from, Map<String, Set<String>> problems)
            throws IOException {
        String declaring = findFieldDeclaring(owner, name);
        if (declaring == null || !TYPE_SINCE.containsKey(declaring)) {
            return;
        }
        String typeSince = TYPE_SINCE.get(declaring);
        if (newerThan13(typeSince)) {
            add(problems, "class " + declaring + " is @since " + typeSince + " (field " + name + ")", from);
            return;
        }
        String key = declaring + "#" + name;
        if (MEMBER_SINCE.containsKey(key)) {
            if (!VISIBLE.contains(key)) {
                add(problems, "field " + declaring + "." + name + " is not public or protected", from);
            } else if (newerThan13(MEMBER_SINCE.get(key))) {
                add(problems, "field " + declaring + "." + name + " is @since " + MEMBER_SINCE.get(key), from);
            }
        }
    }

    static void add(Map<String, Set<String>> problems, String what, String from) {
        Set<String> s = problems.get(what);
        if (s == null) {
            s = new TreeSet<String>();
            problems.put(what, s);
        }
        s.add(from);
    }

    /** True for "1.4", "1.4.2", "1.5", "5.0", "6", "JDK1.4" and later; false for 1.3 and earlier or untagged. */
    static boolean newerThan13(String since) {
        if (since == null || since.length() == 0) {
            return false;
        }
        Matcher m = Pattern.compile("(\\d+)(?:\\.(\\d+))?").matcher(since);
        if (!m.find()) {
            return false;
        }
        int a = Integer.parseInt(m.group(1));
        if (a == 1) {
            return m.group(2) != null && Integer.parseInt(m.group(2)) >= 4;
        }
        return a >= 4;
    }

    // ---- class hierarchy from rt.jar --------------------------------------------

    static ClassNode rtClass(String name) throws IOException {
        if (RT_CACHE.containsKey(name)) {
            return RT_CACHE.get(name);
        }
        ZipEntry e = rt.getEntry(name + ".class");
        ClassNode cn = e == null ? null : read(rt.getInputStream(e));
        RT_CACHE.put(name, cn);
        return cn;
    }

    /** The class or interface that declares name+desc, searching superclasses and then interfaces. */
    static String findDeclaring(String owner, String name, String desc) throws IOException {
        for (String c = owner; c != null;) {
            ClassNode cn = rtClass(c);
            if (cn == null) {
                return null;
            }
            for (MethodNode m : cn.methods) {
                if (m.name.equals(name) && m.desc.equals(desc)) {
                    return c;
                }
            }
            c = cn.superName;
        }
        List<String> queue = new ArrayList<String>();
        for (String c = owner; c != null;) {
            ClassNode cn = rtClass(c);
            if (cn == null) {
                break;
            }
            queue.addAll(cn.interfaces);
            c = cn.superName;
        }
        while (!queue.isEmpty()) {
            String i = queue.remove(0);
            ClassNode cn = rtClass(i);
            if (cn == null) {
                continue;
            }
            for (MethodNode m : cn.methods) {
                if (m.name.equals(name) && m.desc.equals(desc)) {
                    return i;
                }
            }
            queue.addAll(cn.interfaces);
        }
        return null;
    }

    static String findFieldDeclaring(String owner, String name) throws IOException {
        List<String> queue = new ArrayList<String>();
        queue.add(owner);
        while (!queue.isEmpty()) {
            String c = queue.remove(0);
            ClassNode cn = rtClass(c);
            if (cn == null) {
                continue;
            }
            for (FieldNode f : cn.fields) {
                if (f.name.equals(name)) {
                    return c;
                }
            }
            queue.addAll(cn.interfaces);
            if (cn.superName != null) {
                queue.add(cn.superName);
            }
        }
        return null;
    }

    static ClassNode read(InputStream in) throws IOException {
        try {
            ClassNode cn = new ClassNode();
            new ClassReader(in).accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return cn;
        } finally {
            in.close();
        }
    }

    static String simpleName(String internal) {
        String s = internal.substring(internal.lastIndexOf('/') + 1);
        return s.substring(s.lastIndexOf('$') + 1);
    }

    /** "(Ljava/io/File;Z)V" -> "File,boolean". */
    static String simpleParams(String desc) {
        StringBuilder sb = new StringBuilder();
        for (Type t : Type.getArgumentTypes(desc)) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            int dims = 0;
            while (t.getSort() == Type.ARRAY) {
                dims = t.getDimensions();
                t = t.getElementType();
            }
            sb.append(t.getSort() == Type.OBJECT ? simpleName(t.getInternalName()) : t.getClassName());
            for (int i = 0; i < dims; i++) {
                sb.append("[]");
            }
        }
        return sb.toString();
    }

    // ---- @since from the JDK sources ---------------------------------------------

    static void readSources(File srcZip) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        ZipFile z = new ZipFile(srcZip);
        try {
            List<SimpleJavaFileObject> files = new ArrayList<SimpleJavaFileObject>();
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements();) {
                ZipEntry entry = e.nextElement();
                String n = entry.getName();
                if (!n.endsWith(".java") || !(n.startsWith("java/") || n.startsWith("javax/"))) {
                    continue;
                }
                final String text = new String(readAll(z.getInputStream(entry)), "UTF-8");
                files.add(new SimpleJavaFileObject(URI.create("string:///" + n), javax.tools.JavaFileObject.Kind.SOURCE) {
                    public CharSequence getCharContent(boolean ignore) {
                        return text;
                    }
                });
            }
            JavacTask task = (JavacTask) javac.getTask(null, null, null, java.util.Arrays.asList("-proc:none"), null,
                    files);
            final DocTrees docs = DocTrees.instance(task);
            for (final CompilationUnitTree unit : task.parse()) {
                final String pkg = unit.getPackageName() == null ? "" : unit.getPackageName().toString().replace('.', '/');
                new TreePathScanner<Void, String>() {
                    public Void visitClass(ClassTree c, String outer) {
                        String name = outer == null ? pkg + "/" + c.getSimpleName() : outer + "$" + c.getSimpleName();
                        boolean isInterface = c.getKind() == Tree.Kind.INTERFACE
                                || c.getKind() == Tree.Kind.ANNOTATION_TYPE;
                        if (outer == null && !c.getModifiers().getFlags().contains(Modifier.PUBLIC)) {
                            return null; // package-private top-level class: not API
                        }
                        // scan(TreePath) of a nested class clears the current path when it returns
                        TreePath here = getCurrentPath();
                        TYPE_SINCE.put(name, since(docs, here));
                        for (Tree member : c.getMembers()) {
                            TreePath p = new TreePath(here, member);
                            if (member instanceof MethodTree) {
                                MethodTree m = (MethodTree) member;
                                String simple = m.getName().contentEquals("<init>") ? c.getSimpleName().toString()
                                        : m.getName().toString();
                                StringBuilder ps = new StringBuilder();
                                for (VariableTree v : m.getParameters()) {
                                    if (ps.length() > 0) {
                                        ps.append(',');
                                    }
                                    ps.append(simpleType(v.getType().toString()));
                                }
                                String key = name + "#" + simple + "(" + ps + ")";
                                String s = since(docs, p);
                                MEMBER_SINCE.put(key, s);
                                if (isInterface || isVisible(m.getModifiers().getFlags())) {
                                    VISIBLE.add(key);
                                }
                                String ak = name + "#" + simple + "/" + m.getParameters().size();
                                List<String> l = BY_ARITY.get(ak);
                                if (l == null) {
                                    l = new ArrayList<String>();
                                    BY_ARITY.put(ak, l);
                                }
                                l.add(s);
                            } else if (member instanceof VariableTree) {
                                VariableTree v = (VariableTree) member;
                                String key = name + "#" + v.getName();
                                MEMBER_SINCE.put(key, since(docs, p));
                                if (isInterface || isVisible(v.getModifiers().getFlags())) {
                                    VISIBLE.add(key);
                                }
                            } else if (member instanceof ClassTree) {
                                scan(p, name);
                            }
                        }
                        return null;
                    }
                }.scan(new TreePath(unit), null);
            }
        } finally {
            z.close();
        }
    }

    static boolean isVisible(Set<Modifier> flags) {
        return flags.contains(Modifier.PUBLIC) || flags.contains(Modifier.PROTECTED);
    }

    /** "java.util.List<? extends T>" -> "List"; "byte[]" stays; "Object..." -> "Object[]". */
    static String simpleType(String t) {
        t = t.replace("...", "[]");
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (depth == 0) {
                sb.append(c);
            }
        }
        String s = sb.toString().trim();
        while (s.startsWith("@")) { // annotations on the type
            int sp = s.indexOf(' ');
            s = sp < 0 ? s : s.substring(sp + 1).trim();
        }
        String arrays = "";
        while (s.endsWith("[]")) {
            arrays += "[]";
            s = s.substring(0, s.length() - 2).trim();
        }
        return s.substring(s.lastIndexOf('.') + 1) + arrays;
    }

    static String since(DocTrees docs, TreePath p) {
        DocCommentTree d = docs.getDocCommentTree(p);
        if (d == null) {
            return "";
        }
        for (DocTree t : d.getBlockTags()) {
            if (t.getKind() == DocTree.Kind.SINCE) {
                return ((SinceTree) t).getBody().toString().trim();
            }
            if (t instanceof BlockTagTree && ((BlockTagTree) t).getTagName().equals("since")) {
                return t.toString();
            }
        }
        return "";
    }

    static byte[] readAll(InputStream in) throws IOException {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
