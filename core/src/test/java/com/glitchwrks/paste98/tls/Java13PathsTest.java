package com.glitchwrks.paste98.tls;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;

import org.junit.After;
import org.junit.Test;

/** The code paths Java 1.3 takes: connecting on a helper thread, and seeding. */
public class Java13PathsTest {

    @After
    public void tearDown() {
        Randomness.reset();
    }

    @Test
    public void threadConnectSucceeds() throws Exception {
        ServerSocket server = new ServerSocket(0);
        try {
            Socket s = Sockets.connectOnThread("127.0.0.1", server.getLocalPort(), 5000);
            assertTrue(s.isConnected());
            Socket accepted = server.accept();
            accepted.close();
            s.close();
        } finally {
            server.close();
        }
    }

    @Test
    public void threadConnectReportsRefusal() throws Exception {
        ServerSocket server = new ServerSocket(0);
        int port = server.getLocalPort();
        server.close();
        try {
            Sockets.connectOnThread("127.0.0.1", port, 5000);
            fail("connected to a closed port");
        } catch (IOException expected) {
            // ConnectException on a real network stack
        }
    }

    @Test
    public void threadConnectGivesUpInTime() {
        long t0 = System.currentTimeMillis();
        try {
            // Not routable: either times out (InterruptedIOException) or fails at once.
            Sockets.connectOnThread("10.255.255.1", 9, 700).close();
            fail("connected to 10.255.255.1");
        } catch (IOException expected) {
            assertTrue("took " + (System.currentTimeMillis() - t0) + " ms", System.currentTimeMillis() - t0 < 3000);
        }
    }

    @Test
    public void connectUsesTheJavaTimeoutWhenThereIsOne() {
        assertTrue("the build JDK has Socket.connect(address, timeout)", Sockets.hasNativeConnectTimeout());
    }

    @Test
    public void seedingUsesSourcesAndKeepsASeedFile() throws Exception {
        File seed = File.createTempFile("paste98", ".rnd");
        assertTrue(seed.delete());
        try {
            final int[] asked = new int[1];
            Randomness.addSource(new Randomness.Source() {
                public String name() {
                    return "test source";
                }

                public byte[] bytes(int n) {
                    asked[0] = n;
                    return new byte[n];
                }
            });
            Randomness.addSource(new Randomness.Source() {
                public String name() {
                    return "broken source";
                }

                public byte[] bytes(int n) {
                    throw new IllegalStateException("broken");
                }
            });
            Randomness.addSource(new Randomness.Source() {
                public String name() {
                    return "missing source";
                }

                public byte[] bytes(int n) {
                    return null;
                }
            });
            Randomness.setSeedFile(seed);
            SecureRandom r = Randomness.get();
            assertNotNull(r);
            assertTrue(r == Randomness.get());
            assertEquals(32, asked[0]);
            String d = Randomness.describe();
            assertTrue(d, d.startsWith("seeded from test source, Java, timing jitter (8 ticks), in "));
            assertFalse(d, d.indexOf("broken") >= 0 || d.indexOf("missing") >= 0);
            assertEquals(Randomness.SEED_FILE_BYTES, seed.length());

            byte[] before = read(seed);
            Randomness.saveSeed();
            assertFalse("the seed file must change", java.util.Arrays.equals(before, read(seed)));

            // a second start reads it back
            Randomness.reset();
            Randomness.setSeedFile(seed);
            Randomness.get();
            assertTrue(Randomness.describe(), Randomness.describe().startsWith("seeded from seed file, Java, "));
        } finally {
            seed.delete();
        }
    }

    @Test
    public void twoGeneratorsDiffer() {
        byte[] a = new byte[32];
        Randomness.get().nextBytes(a);
        Randomness.reset();
        byte[] b = new byte[32];
        Randomness.get().nextBytes(b);
        assertFalse(java.util.Arrays.equals(a, b));
    }

    @Test
    public void jitterProducesTwoBytesPerTick() {
        assertEquals(6, Randomness.jitter(3).length);
    }

    private static byte[] read(File f) throws IOException {
        byte[] b = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream(f);
        try {
            int n = 0;
            while (n < b.length) {
                n += in.read(b, n, b.length - n);
            }
        } finally {
            in.close();
        }
        return b;
    }
}
