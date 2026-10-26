/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.tls;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Vector;

import org.bouncycastle.crypto.digests.SHA256Digest;

/**
 * The program's one SecureRandom, seeded quickly on every Java.
 *
 * Java 1.3's own SecureRandom seeding times threads and took 33 s on a
 * Pentium III (Java 5: 0.2 s), so instead the seed is SHA-256 of every
 * available source:
 * - sources the program adds (the client adds Windows' CryptGenRandom, from
 *   its tray DLL, where Windows has it);
 * - a seed file kept between runs (rewritten with fresh output at every start
 *   and at exit, so no two runs start from the same file);
 * - Java's own seeding on Java 5+, where it is fast;
 * - timing jitter: how many loop passes fit in each clock tick, 8 ticks when
 *   a strong source answered and 32 when none did;
 * - the time, memory figures and object identity hashes.
 * describe() reports which sources were used.
 */
public final class Randomness {

    /** A source of seed bytes. */
    public interface Source {
        String name();

        /** n bytes, or null if unavailable. */
        byte[] bytes(int n);
    }

    static final int SEED_FILE_BYTES = 64;

    private static final Vector SOURCES = new Vector();
    private static File seedFile;
    private static SecureRandom random;
    private static String description = "not seeded yet";

    private Randomness() {
    }

    /** Adds a strong seed source, tried first. Call before the first get(). */
    public static synchronized void addSource(Source s) {
        SOURCES.addElement(s);
    }

    /** Where to keep the seed file. Call before the first get(). */
    public static synchronized void setSeedFile(File f) {
        seedFile = f;
    }

    /** The shared SecureRandom, seeded on first use. */
    public static synchronized SecureRandom get() {
        if (random == null) {
            random = build();
        }
        return random;
    }

    /** Which sources seeded it, and how long it took. */
    public static synchronized String describe() {
        return description;
    }

    /** Rewrites the seed file with fresh output (call at exit). */
    public static synchronized void saveSeed() {
        if (random != null && seedFile != null) {
            writeSeedFile(random);
        }
    }

    /** Forgets sources, seed file and generator (tests only). */
    static synchronized void reset() {
        SOURCES.removeAllElements();
        seedFile = null;
        random = null;
        description = "not seeded yet";
    }

    private static SecureRandom build() {
        long start = System.currentTimeMillis();
        SHA256Digest d = new SHA256Digest();
        StringBuffer used = new StringBuffer();
        boolean strong = false;

        for (int i = 0; i < SOURCES.size(); i++) {
            Source s = (Source) SOURCES.elementAt(i);
            byte[] b = null;
            try {
                b = s.bytes(32);
            } catch (RuntimeException e) {
                b = null;
            }
            if (b != null && b.length > 0) {
                mix(d, b);
                strong = true;
                used.append(s.name()).append(", ");
            }
        }

        byte[] file = readSeedFile();
        if (file != null) {
            mix(d, file);
            used.append("seed file, ");
        }

        if (javaSeedIsFast()) {
            byte[] b = new byte[32];
            new SecureRandom().nextBytes(b); // self-seeds from the JRE's own source
            mix(d, b);
            strong = true;
            used.append("Java, ");
        }

        int ticks = strong ? 8 : 32;
        mix(d, jitter(ticks));
        used.append("timing jitter (" + ticks + " ticks), ");

        mixLong(d, System.currentTimeMillis());
        mixLong(d, Runtime.getRuntime().freeMemory());
        mixLong(d, Runtime.getRuntime().totalMemory());
        mixLong(d, System.identityHashCode(new Object()));
        mixLong(d, System.identityHashCode(Thread.currentThread()));

        byte[] seed = new byte[32];
        d.doFinal(seed, 0);
        SecureRandom r = new SecureRandom(seed); // seeded explicitly: no self-seeding
        java.util.Arrays.fill(seed, (byte) 0);

        if (seedFile != null) {
            writeSeedFile(r);
        }
        description = "seeded from " + used.toString() + "in " + (System.currentTimeMillis() - start) + " ms"
                + (strong || file != null ? "" : " (no strong source: only timing jitter)");
        return r;
    }

    /**
     * Java 5+ seeds SecureRandom from the operating system quickly; Java 1.3
     * (and possibly 1.4) times threads for tens of seconds on old hardware.
     */
    static boolean javaSeedIsFast() {
        try {
            Class.forName("java.lang.StringBuilder"); // Java 5
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Seed material from scheduling jitter: loop passes per clock tick. */
    static byte[] jitter(int ticks) {
        byte[] out = new byte[ticks * 2];
        for (int i = 0; i < ticks; i++) {
            long t = System.currentTimeMillis();
            int spins = 0;
            while (System.currentTimeMillis() == t) {
                spins++;
            }
            out[2 * i] = (byte) spins;
            out[2 * i + 1] = (byte) (spins >>> 8);
        }
        return out;
    }

    private static byte[] readSeedFile() {
        if (seedFile == null || !seedFile.isFile()) {
            return null;
        }
        InputStream in = null;
        try {
            in = new FileInputStream(seedFile);
            byte[] b = new byte[SEED_FILE_BYTES];
            int n = 0;
            int r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) {
                n += r;
            }
            return n == 0 ? null : b;
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // nothing useful to do
                }
            }
        }
    }

    private static void writeSeedFile(SecureRandom r) {
        byte[] b = new byte[SEED_FILE_BYTES];
        r.nextBytes(b);
        OutputStream out = null;
        try {
            out = new FileOutputStream(seedFile);
            out.write(b);
        } catch (IOException e) {
            // best effort: the other sources still seed the next run
        } finally {
            java.util.Arrays.fill(b, (byte) 0);
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // nothing useful to do
                }
            }
        }
    }

    private static void mix(SHA256Digest d, byte[] b) {
        d.update(b, 0, b.length);
    }

    private static void mixLong(SHA256Digest d, long v) {
        for (int i = 0; i < 8; i++) {
            d.update((byte) (v >>> (8 * i)));
        }
    }
}
