import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.BeforeClass;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.glitchwrks.paste98.compat13.Causes;

/**
 * Compiles a sample at Java 1.4 (class version 48, like Bouncy Castle's jdk14
 * jars), retargets it, and runs the result against core's compat13 classes.
 */
public class Retarget13Test {

    private static final String SAMPLE = ""
            + "public class Sample {\n"
            + "    public static Object[] run() {\n"
            + "        Exception root = new java.io.IOException(\"root\");\n"
            + "        RuntimeException a = new RuntimeException(\"wrapped\", root);\n"
            + "        RuntimeException b = new RuntimeException(root);\n"
            + "        IllegalStateException c = new IllegalStateException(\"c\");\n"
            + "        c.initCause(root);\n"
            + "        Mine d = new Mine(\"mine\", a);\n"
            + "        Exception e = new Exception(\"outer\", new java.io.IOException(\"inner\", root));\n"
            + "        Object outer = new Sample().new Inner().outer();\n"
            + "        return new Object[] { root, a, b, c, d, e, outer };\n"
            + "    }\n"
            + "    static class Mine extends java.io.IOException {\n"
            + "        Mine(String m, Throwable cause) { super(m); initCause(cause); }\n"
            + "    }\n"
            + "    class Inner {\n"
            + "        Object outer() { return Sample.this; }\n"
            + "    }\n"
            + "}\n";

    private static Map<String, byte[]> classes = new HashMap<String, byte[]>();

    @BeforeClass
    public static void compileAndRetarget() throws Exception {
        File dir = Files.createTempDirectory("retarget13").toFile();
        File src = new File(dir, "Sample.java");
        FileWriter w = new FileWriter(src);
        w.write(SAMPLE);
        w.close();
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull("tests need a JDK", javac);
        // java.io.IOException(String, Throwable) is Java 6, so compile against the build JDK's library.
        int rc = javac.run(null, null, null, "-nowarn", "-source", "1.4", "-target", "1.4", "-d",
                dir.getPath(), src.getPath());
        assertEquals("javac failed", 0, rc);
        File[] files = dir.listFiles();
        for (int i = 0; i < files.length; i++) {
            if (files[i].getName().endsWith(".class")) {
                byte[] in = Files.readAllBytes(files[i].toPath());
                assertEquals(48, in[7]);
                String name = files[i].getName().replace(".class", "");
                classes.put(name, Retarget13.retarget(in, files[i].getName()));
            }
            files[i].delete();
        }
        dir.delete();
    }

    private static Object[] runSample() throws Exception {
        ClassLoader loader = new ClassLoader(Retarget13Test.class.getClassLoader()) {
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] b = classes.get(name);
                if (b == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, b, 0, b.length);
            }
        };
        return (Object[]) loader.loadClass("Sample").getMethod("run").invoke(null);
    }

    @Test
    public void classesAreJava13() {
        for (byte[] b : classes.values()) {
            assertEquals(47, ((b[6] & 0xff) << 8) | (b[7] & 0xff));
        }
    }

    @Test
    public void causesAreKept() throws Exception {
        Object[] r = runSample();
        Throwable root = (Throwable) r[0];
        Throwable a = (Throwable) r[1];
        Throwable b = (Throwable) r[2];
        Throwable c = (Throwable) r[3];
        Throwable d = (Throwable) r[4];
        Throwable e = (Throwable) r[5];

        // The JDK's own cause is never set: the calls became X(String) and Causes.initCause.
        assertNull(a.getCause());
        assertNull(c.getCause());

        assertEquals("wrapped", a.getMessage());
        assertSame(root, Causes.getCause(a));
        assertEquals("java.io.IOException: root", b.getMessage()); // as Java 1.4+'s X(Throwable)
        assertSame(root, Causes.getCause(b));
        assertSame(root, Causes.getCause(c));
        assertSame(a, Causes.getCause(d));
        Throwable inner = Causes.getCause(e);
        assertEquals("inner", inner.getMessage());
        assertSame(root, Causes.getCause(inner));
        assertEquals("java.lang.Exception: outer; caused by java.io.IOException: inner; caused by "
                + "java.io.IOException: root", Causes.describe(e));
        String traces = Causes.stackTraces(d);
        assertTrue(traces, traces.indexOf("Caused by: java.lang.RuntimeException: wrapped") > 0);
        assertTrue(traces, traces.indexOf("Caused by: java.io.IOException: root") > 0);
    }

    @Test
    public void innerClassStillWorks() throws Exception {
        assertNotNull(runSample()[6]);
    }

    /** Java 1.3's verifier rejects a field store before super(); javac 1.4+ emits one for this$0. */
    @Test
    public void noFieldStoreBeforeSuper() {
        byte[] b = classes.get("Sample$Inner");
        assertNotNull(b);
        ClassNode c = new ClassNode();
        new ClassReader(b).accept(c, 0);
        for (MethodNode m : c.methods) {
            if (!m.name.equals("<init>")) {
                continue;
            }
            boolean sawSuper = false;
            boolean sawStore = false;
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) i).name.equals("<init>")) {
                    sawSuper = true;
                }
                if (i.getOpcode() == Opcodes.PUTFIELD && ((FieldInsnNode) i).name.equals("this$0")) {
                    assertTrue("this$0 stored before super()", sawSuper);
                    sawStore = true;
                }
            }
            assertTrue(sawStore);
        }
    }

    @Test
    public void newerClassesAreRefused() {
        byte[] b = Arrays.copyOf(classes.get("Sample"), classes.get("Sample").length);
        b[7] = 49;
        try {
            Retarget13.retarget(b, "Sample.class");
            assertFalse("accepted a Java 5 class", true);
        } catch (IllegalStateException expected) {
            // good
        }
    }
}
