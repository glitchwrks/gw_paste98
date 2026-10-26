/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Build-time tool (runs on the build JDK, not on Windows): makes Bouncy
 * Castle's Java 1.4 builds (bc*-jdk14) loadable on Java 1.3.
 *
 * The parts of BC this program uses need only two things 1.3 lacks:
 * - java.util.logging (bctls logs at FINE): references are renamed to the
 *   do-nothing stand-ins in com.glitchwrks.paste98.compat13.
 * - javax.security.auth.Destroyable and DestroyFailedException (core only
 *   from 1.4), implemented by BC's private key classes: renamed likewise.
 * - exception causes: x.initCause(c) becomes Causes.initCause(x, c), and
 *   JDK exception constructors taking a cause, X(Throwable) and
 *   X(String, Throwable), become X(String), keeping a message.
 * Every class file is also re-marked as version 47 (Java 1.3). The one
 * bytecode difference that matters: javac 1.4 stores an inner class's outer
 * instance and captured variables before calling the superclass constructor,
 * which Java 1.3's verifier rejects ("Expecting to find object/array on
 * stack"); later verifiers, including JDK 8's for old class files, accept it.
 * Those stores are moved to just after the superclass constructor call, the
 * order javac 1.3 used.
 *
 * Check13 then verifies the final jar against the Java 1.3 API.
 *
 * Usage: Retarget13 IN.jar OUT.jar
 */
public final class Retarget13 {

    static final String COMPAT = "com/glitchwrks/paste98/compat13/";
    static final String LOGGING = "java/util/logging/";
    /** Single classes 1.3 lacks, renamed to stand-ins of the same simple name. */
    static final String[] RENAMED = { "javax/security/auth/Destroyable", "javax/security/auth/DestroyFailedException" };

    /** JDK exception classes whose cause-taking constructors 1.3 lacks. */
    static final String[] CAUSE_CTOR_OWNERS = { "java/lang/Throwable", "java/lang/Exception",
        "java/lang/RuntimeException", "java/lang/Error", "java/lang/IllegalArgumentException",
        "java/lang/IllegalStateException", "java/lang/UnsupportedOperationException",
        "java/lang/SecurityException", "java/io/IOException" };

    static int rewrittenInitCause;
    static int rewrittenCtors;
    static int renamedLogging;
    static int renamedOther;
    static int movedFieldStores;

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: Retarget13 IN.jar OUT.jar");
            System.exit(2);
        }
        File in = new File(args[0]);
        File out = new File(args[1]);
        int classes = 0;
        ZipFile zin = new ZipFile(in);
        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(out));
        try {
            Enumeration<? extends ZipEntry> e = zin.entries();
            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF/")) {
                    continue; // directories are implied; BC's signatures would not match
                }
                byte[] data = read(zin.getInputStream(entry));
                if (name.endsWith(".class") && !name.endsWith("module-info.class")) {
                    data = retarget(data, name);
                    classes++;
                } else if (name.endsWith(".class")) {
                    continue;
                }
                zout.putNextEntry(new ZipEntry(name));
                zout.write(data);
                zout.closeEntry();
            }
        } finally {
            zout.close();
            zin.close();
        }
        System.out.println("Retarget13: " + in.getName() + " -> " + out.getName() + ": " + classes
                + " classes; " + renamedLogging + " logging and " + renamedOther + " Destroyable references, "
                + rewrittenInitCause
                + " initCause calls, " + rewrittenCtors + " cause constructors rewritten, " + movedFieldStores
                + " constructors' early field stores moved after super()");
    }

    static byte[] retarget(byte[] classFile, String name) {
        ClassReader reader = new ClassReader(classFile);
        int major = ((classFile[6] & 0xff) << 8) | (classFile[7] & 0xff);
        if (major > 48) {
            throw new IllegalStateException(name + " is class version " + major
                    + "; only Java 1.4 (48) and older can be retargeted");
        }
        ClassNode node = new ClassNode();
        Remapper remapper = new Remapper() {
            public String map(String internalName) {
                if (internalName.startsWith(LOGGING)) {
                    renamedLogging++;
                    return COMPAT + internalName.substring(LOGGING.length());
                }
                for (int i = 0; i < RENAMED.length; i++) {
                    if (RENAMED[i].equals(internalName)) {
                        renamedOther++;
                        return COMPAT + internalName.substring(internalName.lastIndexOf('/') + 1);
                    }
                }
                return internalName;
            }
        };
        ClassVisitor v = new ClassVisitor(Opcodes.ASM9, node) {
            public void visit(int version, int access, String n, String sig, String superName, String[] itfs) {
                super.visit(Opcodes.V1_3, access, n, sig, superName, itfs);
            }

            public MethodVisitor visitMethod(int access, String n, String desc, String sig, String[] exc) {
                return new CauseRewriter(super.visitMethod(access, n, desc, sig, exc));
            }
        };
        reader.accept(new ClassRemapper(v, remapper), 0);
        for (int i = 0; i < node.methods.size(); i++) {
            MethodNode m = (MethodNode) node.methods.get(i);
            if (m.name.equals("<init>")) {
                moveEarlyFieldStores(node, m, name);
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static boolean isMeta(AbstractInsnNode i) {
        return i instanceof LabelNode || i instanceof LineNumberNode || i instanceof FrameNode;
    }

    static boolean isLoad(int op) {
        return op == Opcodes.ALOAD || op == Opcodes.ILOAD || op == Opcodes.LLOAD || op == Opcodes.FLOAD
                || op == Opcodes.DLOAD;
    }

    /**
     * javac 1.4 starts a constructor with "this.f = param" stores (aload_0,
     * load, putfield) before the super(...) call. Moves them to just after it.
     */
    static void moveEarlyFieldStores(ClassNode c, MethodNode m, String where) {
        InsnList code = m.instructions;
        InsnList moved = new InsnList();
        AbstractInsnNode i = code.getFirst();
        while (true) {
            while (i != null && isMeta(i)) {
                i = i.getNext();
            }
            if (i == null || i.getOpcode() != Opcodes.ALOAD || ((VarInsnNode) i).var != 0) {
                break;
            }
            AbstractInsnNode load = next(i);
            AbstractInsnNode put = load == null ? null : next(load);
            if (load == null || !isLoad(load.getOpcode()) || ((VarInsnNode) load).var == 0 || put == null
                    || put.getOpcode() != Opcodes.PUTFIELD || !((FieldInsnNode) put).owner.equals(c.name)) {
                break;
            }
            AbstractInsnNode after = put.getNext();
            code.remove(i);
            code.remove(load);
            code.remove(put);
            moved.add(i);
            moved.add(load);
            moved.add(put);
            i = after;
        }

        // Find this constructor's own super(...) or this(...) call: the first
        // INVOKESPECIAL <init> not paired with a NEW for an argument.
        AbstractInsnNode superCall = null;
        int pending = 0;
        for (AbstractInsnNode x = code.getFirst(); x != null; x = x.getNext()) {
            if (x.getOpcode() == Opcodes.NEW) {
                pending++;
            } else if (x.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) x).name.equals("<init>")) {
                if (pending == 0) {
                    superCall = x;
                    break;
                }
                pending--;
            } else if (x.getOpcode() == Opcodes.PUTFIELD && ((FieldInsnNode) x).owner.equals(c.name)) {
                throw new IllegalStateException(where + " " + m.desc
                        + ": a field store before super() that Retarget13 cannot move");
            }
        }
        if (moved.size() == 0) {
            return;
        }
        if (superCall == null) {
            throw new IllegalStateException(where + " " + m.desc + ": no super() call found");
        }
        code.insert(superCall, moved);
        movedFieldStores++;
    }

    static AbstractInsnNode next(AbstractInsnNode i) {
        AbstractInsnNode n = i.getNext();
        while (n != null && isMeta(n)) {
            n = n.getNext();
        }
        return n;
    }

    static boolean isCauseCtorOwner(String owner) {
        for (int i = 0; i < CAUSE_CTOR_OWNERS.length; i++) {
            if (CAUSE_CTOR_OWNERS[i].equals(owner)) {
                return true;
            }
        }
        return false;
    }

    static final class CauseRewriter extends MethodVisitor {
        CauseRewriter(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            if (op == Opcodes.INVOKEVIRTUAL && name.equals("initCause")
                    && desc.equals("(Ljava/lang/Throwable;)Ljava/lang/Throwable;")) {
                rewrittenInitCause++;
                super.visitMethodInsn(Opcodes.INVOKESTATIC, COMPAT + "Causes", "initCause",
                        "(Ljava/lang/Throwable;Ljava/lang/Throwable;)Ljava/lang/Throwable;", false);
                return;
            }
            if (op == Opcodes.INVOKESPECIAL && name.equals("<init>") && isCauseCtorOwner(owner)) {
                if (desc.equals("(Ljava/lang/Throwable;)V")) {
                    // stack: ..., uninitialised, cause  ->  ..., uninitialised, cause.toString()
                    rewrittenCtors++;
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, COMPAT + "Causes", "message",
                            "(Ljava/lang/Throwable;)Ljava/lang/String;", false);
                    super.visitMethodInsn(op, owner, name, "(Ljava/lang/String;)V", false);
                    return;
                }
                if (desc.equals("(Ljava/lang/String;Ljava/lang/Throwable;)V")) {
                    // stack: ..., uninitialised, message, cause  ->  ..., uninitialised, message
                    rewrittenCtors++;
                    super.visitInsn(Opcodes.POP);
                    super.visitMethodInsn(op, owner, name, "(Ljava/lang/String;)V", false);
                    return;
                }
            }
            super.visitMethodInsn(op, owner, name, desc, itf);
        }
    }

    static byte[] read(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                buf.write(b, 0, n);
            }
            return buf.toByteArray();
        } finally {
            in.close();
        }
    }
}
