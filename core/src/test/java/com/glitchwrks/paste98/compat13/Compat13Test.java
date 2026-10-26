package com.glitchwrks.paste98.compat13;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Test;

public class Compat13Test {

    @After
    public void tearDown() {
        Logger.setSink(null, Level.INFO);
    }

    @Test
    public void loggerForwardsAtOrAboveTheThreshold() {
        final List<String> got = new ArrayList<String>();
        Logger.setSink(new Logger.Sink() {
            public void log(String logger, Level level, String message, Throwable thrown) {
                got.add(logger + " " + level + " " + message + (thrown == null ? "" : " " + thrown.getMessage()));
            }
        }, Level.INFO);
        Logger log = Logger.getLogger("org.bouncycastle.test");
        log.fine("hidden");
        log.info("shown");
        log.log(Level.WARNING, "with", new IOException("boom"));
        log.log(Level.SEVERE, "param", "p");
        assertFalse(log.isLoggable(Level.FINE));
        assertTrue(log.isLoggable(Level.WARNING));
        assertEquals("[org.bouncycastle.test INFO shown, org.bouncycastle.test WARNING with boom, "
                + "org.bouncycastle.test SEVERE param [p]]", got.toString());

        Logger.setSink(null, Level.ALL);
        assertFalse(log.isLoggable(Level.SEVERE));
        log.severe("nowhere");
        assertEquals(3, got.size());
    }

    @Test
    public void causesFallBackToTheJavaChain() {
        IOException root = new IOException("root");
        RuntimeException jdk = new RuntimeException("jdk", root);
        assertSame(root, Causes.getCause(jdk));
        IOException ours = new IOException("ours");
        assertSame(ours, Causes.initCause(ours, jdk));
        assertSame(jdk, Causes.getCause(ours));
        assertEquals("java.io.IOException: ours; caused by java.lang.RuntimeException: jdk; caused by "
                + "java.io.IOException: root", Causes.describe(ours));
        assertNull(Causes.getCause(root));
        assertNull(Causes.getCause(null));
        assertSame(root, Causes.initCause(root, root)); // never its own cause
        assertNull(Causes.getCause(root));
    }

    @Test
    public void stackTracesShowEachCauseOnce() {
        IOException root = new IOException("root");
        RuntimeException jdk = new RuntimeException("jdk", root);
        IOException ours = new IOException("ours");
        Causes.initCause(ours, jdk);
        String s = Causes.stackTraces(ours);
        assertEquals(1, count(s, "Caused by: java.lang.RuntimeException: jdk"));
        assertEquals(1, count(s, "Caused by: java.io.IOException: root"));
    }

    @Test
    public void standInsBehave() throws Exception {
        try {
            throw new DestroyFailedException("x");
        } catch (DestroyFailedException e) {
            assertEquals("x", e.getMessage());
        }
        assertTrue(Level.SEVERE.intValue() > Level.WARNING.intValue());
        assertTrue(Level.FINE.intValue() < Level.INFO.intValue());
    }

    private static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) {
            n++;
        }
        return n;
    }
}
