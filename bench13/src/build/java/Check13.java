/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import java.util.Map;

import org.codehaus.mojo.animal_sniffer.Clazz;
import org.codehaus.mojo.animal_sniffer.SignatureChecker;
import org.codehaus.mojo.animal_sniffer.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Build-time check of the final jar: fails unless every class file is Java 1.3
 * (version 47 or lower) and nothing refers to a JDK class, method or field
 * that Java 1.3 lacks (checked against animal-sniffer's java13 signature),
 * and no constructor stores to its own fields before calling super(), which
 * Java 1.3's verifier rejects but later ones (including JDK 8's) accept.
 *
 * animal-sniffer checks method and field references but not superclasses,
 * implemented interfaces or types that appear only in descriptors, so every
 * class name each class file mentions is also checked: it must be in the jar
 * or in Java 1.3 (javax.security.auth.Destroyable got through otherwise).
 * References to classes inside the jar itself are fine.
 *
 * Usage: Check13 JAR java13.signature UNPACKED_DIR
 */
public final class Check13 {

    // "Undefined reference: [type ]Owner.member(...)" or "[type ]Owner.field" or "Owner"
    private static final Pattern MEMBER = Pattern.compile("^(?:\\S+ )?([\\w.$]+)\\.(?:<init>|[\\w$]+)\\(.*");
    private static final Pattern FIELD = Pattern.compile("^\\S+ ([\\w.$]+)\\.[\\w$]+$");

    public static void main(String[] args) throws Exception {
        File jar = new File(args[0]);
        File signature = new File(args[1]);
        File unpacked = new File(args[2]);

        InputStream sigIn = new FileInputStream(signature);
        final Map<String, Clazz> java13 = SignatureChecker.loadClasses(sigIn);
        sigIn.close();
        Set<String> own = new HashSet<String>();
        Set<String> ownInternal = new HashSet<String>();
        final Map<String, Set<String>> mentions = new java.util.TreeMap<String, Set<String>>();
        int tooNew = 0;
        int earlyStores = 0;
        int classCount = 0;
        ZipFile z = new ZipFile(jar);
        try {
            Enumeration<? extends ZipEntry> e = z.entries();
            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }
                classCount++;
                String cls = name.substring(0, name.length() - 6).replace('/', '.');
                own.add(cls);
                own.add(cls.replace('$', '.')); // the checker writes nested classes as Outer.Inner
                ClassNode cn = new ClassNode();
                InputStream cin = z.getInputStream(entry);
                ClassReader cr = new ClassReader(cin);
                cin.close();
                cr.accept(cn, 0);
                ownInternal.add(cn.name);
                final String from = cn.name;
                cr.accept(new ClassRemapper(new ClassNode(), new Remapper() {
                    public String map(String internalName) {
                        if (!internalName.startsWith("[")) {
                            Set<String> users = mentions.get(internalName);
                            if (users == null) {
                                users = new TreeSet<String>();
                                mentions.put(internalName, users);
                            }
                            users.add(from);
                        }
                        return internalName;
                    }
                }), 0);
                for (int mi = 0; mi < cn.methods.size(); mi++) {
                    MethodNode m = (MethodNode) cn.methods.get(mi);
                    if (m.name.equals("<init>") && storesBeforeSuper(cn, m)) {
                        System.err.println("Check13: " + cn.name + " " + m.desc
                                + " stores a field before super() (Java 1.3 rejects this)");
                        earlyStores++;
                    }
                }
                InputStream in = z.getInputStream(entry);
                byte[] head = new byte[8];
                int got = 0;
                while (got < 8) {
                    int n = in.read(head, got, 8 - got);
                    if (n < 0) {
                        break;
                    }
                    got += n;
                }
                in.close();
                int major = ((head[6] & 0xff) << 8) | (head[7] & 0xff);
                if (major > 47) {
                    System.err.println("Check13: " + name + " is class version " + major + " (Java 1.3 is 47)");
                    tooNew++;
                }
            }
        } finally {
            z.close();
        }

        final Set<String> missing = new TreeSet<String>();
        final Set<String> ownClasses = own;
        Logger collector = new Logger() {
            public void debug(String m) { }
            public void debug(String m, Throwable t) { }
            public void info(String m) { }
            public void info(String m, Throwable t) { }
            public void warn(String m) { }
            public void warn(String m, Throwable t) { }
            public void error(String m, Throwable t) { error(m); }
            public void error(String m) {
                int i = m.indexOf("Undefined reference: ");
                if (i < 0) {
                    return;
                }
                String ref = m.substring(i + "Undefined reference: ".length()).trim();
                String owner = ref;
                Matcher mm = MEMBER.matcher(ref);
                Matcher fm = FIELD.matcher(ref);
                if (mm.matches()) {
                    owner = mm.group(1);
                } else if (fm.matches()) {
                    owner = fm.group(1);
                }
                if (!ownClasses.contains(owner)) {
                    missing.add(ref);
                }
            }
        };
        InputStream sig = new FileInputStream(signature);
        SignatureChecker checker = new SignatureChecker(sig, new HashSet<String>(), collector);
        sig.close();
        checker.setSourcePath(new ArrayList<File>());
        checker.process(unpacked);

        int missingClasses = 0;
        for (Map.Entry<String, Set<String>> e2 : mentions.entrySet()) {
            String cls = e2.getKey();
            if (!ownInternal.contains(cls) && !java13.containsKey(cls)) {
                System.err.println("Check13: class not in Java 1.3: " + cls + " (used by " + e2.getValue().size()
                        + " classes, e.g. " + e2.getValue().iterator().next() + ")");
                missingClasses++;
            }
        }
        for (String ref : missing) {
            System.err.println("Check13: not in Java 1.3: " + ref);
        }
        if (tooNew > 0 || !missing.isEmpty() || earlyStores > 0 || missingClasses > 0) {
            System.err.println("Check13: FAILED: " + tooNew + " class files too new, " + missingClasses
                    + " classes Java 1.3 lacks, " + missing.size()
                    + " references to APIs Java 1.3 lacks, " + earlyStores + " constructors storing fields before super()");
            System.exit(1);
        }
        System.out.println("Check13: " + jar.getName() + ": " + classCount
                + " classes, all Java 1.3 class files using only the Java 1.3 API, no field stores before super()");
    }

    /** True if the constructor writes one of its own class's fields before its super()/this() call. */
    static boolean storesBeforeSuper(ClassNode c, MethodNode m) {
        int pending = 0;
        for (AbstractInsnNode x = m.instructions.getFirst(); x != null; x = x.getNext()) {
            int op = x.getOpcode();
            if (op == Opcodes.NEW) {
                pending++;
            } else if (op == Opcodes.INVOKESPECIAL && ((MethodInsnNode) x).name.equals("<init>")) {
                if (pending == 0) {
                    return false;
                }
                pending--;
            } else if (op == Opcodes.PUTFIELD && ((FieldInsnNode) x).owner.equals(c.name)) {
                return true;
            }
        }
        return false;
    }
}
