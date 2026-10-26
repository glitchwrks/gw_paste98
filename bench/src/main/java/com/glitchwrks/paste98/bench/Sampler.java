/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.bench;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Poor man's sampling profiler (Java 5 has Thread.getStackTrace but no JFR).
 * Periodically records where the target thread is and summarises it.
 * Win9x timers tick every ~55 ms, so expect samples at about that spacing.
 */
final class Sampler extends Thread {

    private final Thread target;
    private final int intervalMs;
    private volatile boolean running = true;

    private int samples;
    private final Map<String, int[]> topFrames = new HashMap<String, int[]>();
    private final Map<String, int[]> categories = new HashMap<String, int[]>();
    private final Map<String, int[]> bcFrames = new HashMap<String, int[]>();
    private int inClassLoading;
    private int inStaticInit;

    Sampler(Thread target, int intervalMs) {
        super("pastebench-sampler");
        this.target = target;
        this.intervalMs = intervalMs;
        setDaemon(true);
        setPriority(Thread.MAX_PRIORITY);
    }

    public void run() {
        while (running) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                return;
            }
            if (!running) {
                return;
            }
            record(target.getStackTrace());
        }
    }

    void finish() {
        running = false;
        interrupt();
        try {
            join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void record(StackTraceElement[] st) {
        if (st == null || st.length == 0) {
            return;
        }
        samples++;
        StackTraceElement top = st[0];
        count(topFrames, frame(top));
        count(categories, categorise(st));

        boolean loading = false;
        boolean clinit = false;
        String bc = null;
        for (int i = 0; i < st.length; i++) {
            String cls = st[i].getClassName();
            String m = st[i].getMethodName();
            if (m.equals("loadClass") || m.equals("defineClass") || m.equals("defineClass1") || m.equals("findClass")) {
                loading = true;
            }
            if (m.equals("<clinit>")) {
                clinit = true;
            }
            if (bc == null && (cls.startsWith("org.bouncycastle.") || cls.startsWith("com.glitchwrks."))) {
                bc = frame(st[i]);
            }
        }
        if (loading) {
            inClassLoading++;
        }
        if (clinit) {
            inStaticInit++;
        }
        count(bcFrames, bc == null ? "(no app frame)" : bc);
    }

    private static String categorise(StackTraceElement[] st) {
        String cls = st[0].getClassName();
        String m = st[0].getMethodName();
        if (cls.startsWith("java.net.Socket") || (cls.startsWith("java.net.") && m.startsWith("socketRead"))) {
            return "waiting on network";
        }
        if (cls.startsWith("java.util.zip.") || cls.startsWith("java.util.jar.")) {
            return "reading/inflating jar";
        }
        for (int i = 0; i < st.length; i++) {
            String mi = st[i].getMethodName();
            if (mi.equals("loadClass") || mi.startsWith("defineClass") || mi.equals("findClass")) {
                return "class loading (other)";
            }
        }
        if (m.equals("<clinit>")) {
            return "static initializer";
        }
        if (cls.startsWith("org.bouncycastle.math.") || cls.startsWith("java.math.")) {
            return "big-number / EC math";
        }
        if (cls.startsWith("org.bouncycastle.crypto.")) {
            return "BC crypto primitives";
        }
        if (cls.startsWith("org.bouncycastle.")) {
            return "BC other: " + packageOf(cls);
        }
        return "JDK/other: " + packageOf(cls);
    }

    private static String packageOf(String cls) {
        int dot = cls.lastIndexOf('.');
        return dot > 0 ? cls.substring(0, dot) : cls;
    }

    private static String frame(StackTraceElement e) {
        return e.getClassName() + "." + e.getMethodName();
    }

    private static void count(Map<String, int[]> map, String key) {
        int[] c = map.get(key);
        if (c == null) {
            map.put(key, new int[] { 1 });
        } else {
            c[0]++;
        }
    }

    synchronized void print(PrintStream out) {
        out.println("     profile of the first handshake: " + samples + " samples");
        if (samples == 0) {
            return;
        }
        out.println(String.format("       class loading on stack in %d%% of samples, a static initializer in %d%%",
                Integer.valueOf(inClassLoading * 100 / samples), Integer.valueOf(inStaticInit * 100 / samples)));
        printTop(out, "by category", categories, 10);
        printTop(out, "by top frame", topFrames, 12);
        printTop(out, "by innermost app/BC frame", bcFrames, 12);
    }

    private void printTop(PrintStream out, String title, Map<String, int[]> map, int n) {
        List<Map.Entry<String, int[]>> list = new ArrayList<Map.Entry<String, int[]>>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, int[]>>() {
            public int compare(Map.Entry<String, int[]> a, Map.Entry<String, int[]> b) {
                return b.getValue()[0] - a.getValue()[0];
            }
        });
        out.println("       " + title + ":");
        for (int i = 0; i < list.size() && i < n; i++) {
            Map.Entry<String, int[]> e = list.get(i);
            out.println(String.format("         %3d%%  %s", Integer.valueOf(e.getValue()[0] * 100 / samples),
                    e.getKey()));
        }
    }
}
