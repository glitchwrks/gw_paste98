/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.bench;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import com.glitchwrks.paste98.tls.HandshakeListener;

/**
 * Phase timeline of one handshake: timestamps and class-load counts at each
 * TLS client callback, plus how long the thread spent blocked reading from
 * the server.
 */
final class Timeline implements HandshakeListener {

    private static final ClassLoadingMXBean CLASSES = ManagementFactory.getClassLoadingMXBean();

    private final long start = System.nanoTime();
    private final long startClasses = CLASSES.getTotalLoadedClassCount();
    private final List<String> labels = new ArrayList<String>();
    private final List<long[]> marks = new ArrayList<long[]>(); // {nanos, classes, blockedNanosSoFar}

    /** Wraps the socket stream; updated by every read. */
    private long blockedNanos;
    private long firstByteAt = -1;

    public void step(String label) {
        labels.add(label);
        marks.add(new long[] { System.nanoTime(), CLASSES.getTotalLoadedClassCount(), blockedNanos });
    }

    InputStream wrap(final InputStream in) {
        return new InputStream() {
            public int read() throws IOException {
                long t = System.nanoTime();
                int r = in.read();
                account(t);
                return r;
            }

            public int read(byte[] b, int off, int len) throws IOException {
                long t = System.nanoTime();
                int r = in.read(b, off, len);
                account(t);
                return r;
            }

            public int available() throws IOException {
                return in.available();
            }

            public void close() throws IOException {
                in.close();
            }
        };
    }

    private synchronized void account(long t0) {
        long now = System.nanoTime();
        blockedNanos += now - t0;
        if (firstByteAt < 0) {
            firstByteAt = now;
        }
    }

    void print(PrintStream out, String title) {
        out.println("     " + title + " timeline (ms since handshake start; 'net wait' = blocked reading from server):");
        out.println("       at ms    step ms   net wait ms   classes   step");
        long prevT = start;
        long prevC = startClasses;
        long prevB = 0;
        for (int i = 0; i < marks.size(); i++) {
            long[] m = marks.get(i);
            out.println(String.format("       %7.0f   %7.0f   %11.0f   %7d   %s", Double.valueOf((m[0] - start) / 1e6),
                    Double.valueOf((m[0] - prevT) / 1e6), Double.valueOf((m[2] - prevB) / 1e6),
                    Long.valueOf(m[1] - prevC), labels.get(i)));
            prevT = m[0];
            prevC = m[1];
            prevB = m[2];
        }
        if (!marks.isEmpty()) {
            long[] last = marks.get(marks.size() - 1);
            double total = (last[0] - start) / 1e6;
            double net = last[2] / 1e6;
            out.println(String.format("       total %.0f ms: %.0f ms blocked on the network, %.0f ms local work",
                    Double.valueOf(total), Double.valueOf(net), Double.valueOf(total - net)));
        }
    }
}
