/*
 * Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.tray;

import java.io.File;

import javax.swing.SwingUtilities;

import com.glitchwrks.paste98.client.Log;
import com.glitchwrks.paste98.tls.Randomness;

/**
 * Notification area icon through gwtray.dll (see native/gwtray.c). Java has
 * no tray API before Java 6. The DLL also supplies seed bytes from Windows'
 * CryptGenRandom ({@link #seedSource}).
 *
 * nativeRun() runs the Win32 message loop on a dedicated "gwtray" thread
 * until quit(). The DLL calls {@link #onEvent(int)} on that thread; events are
 * handed to the listener on the Swing event thread.
 *
 * The "gwtray" thread is deliberately not a daemon thread. In tray mode the
 * program has no windows open, so AWT shuts its event thread down; if this
 * thread were a daemon too, nothing would keep the JVM running and it would
 * exit a second after start-up, leaving a dead icon that vanishes as soon as
 * the mouse touches it.
 */
public final class NativeTray {

    /** Must match GWTRAY_VERSION in gwtray.c. */
    static final int EXPECTED_VERSION = 2;

    static final int EVENT_DEFAULT = -1;
    static final int EVENT_READY = -2;

    /** Tray events, delivered on the Swing event thread. */
    public interface Listener {
        /** A menu item was chosen (index into the labels), or -1 for a double-click on the icon. */
        void trayAction(int item);

        /** The tray message loop ended without {@link NativeTray#stop()}; the icon is gone. */
        void trayLost(String why);
    }

    private static volatile Listener listener;
    private static volatile Latch ready;
    private static Thread thread;
    private static boolean loaded;
    /** True while the icon is up (guarded by the start-up result lock). */
    private static boolean up;
    private static volatile boolean stopping;

    private NativeTray() {
    }

    static native int nativeVersion();

    static native int nativeRun(String iconPath, String tooltip, String[] items, int defaultItem);

    static native void nativeSetTooltip(String tooltip);

    static native void nativeQuit();

    static native int nativeRandom(byte[] out);

    /** One-shot latch (java.util.concurrent is Java 5). */
    static final class Latch {
        private boolean open;

        synchronized void release() {
            open = true;
            notifyAll();
        }

        /** Waits until released or the time is up; true if released. */
        synchronized boolean await(long ms) throws InterruptedException {
            long end = System.currentTimeMillis() + ms;
            while (!open) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) {
                    return false;
                }
                wait(left);
            }
            return true;
        }
    }

    /**
     * Loads the DLL once and checks its version.
     *
     * @return null if loaded, otherwise why not
     */
    private static synchronized String load(File dll) {
        if (loaded) {
            return null;
        }
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return "the tray icon needs Windows";
        }
        if (!dll.isFile()) {
            return dll.getName() + " not found in " + dll.getParent();
        }
        try {
            System.load(dll.getAbsolutePath());
        } catch (Throwable t) { // UnsatisfiedLinkError, SecurityException
            return "could not load " + dll.getName() + ": " + t;
        }
        int v;
        try {
            v = nativeVersion();
        } catch (Throwable t) {
            return dll.getName() + " is not usable: " + t;
        }
        if (v != EXPECTED_VERSION) {
            return dll.getName() + " is version " + v + "; this program needs version " + EXPECTED_VERSION;
        }
        loaded = true;
        return null;
    }

    /**
     * Seed bytes from Windows' CryptGenRandom through the DLL. The source
     * answers null when the DLL cannot be loaded or Windows has no CryptoAPI
     * (Windows 95 before OSR2 without Internet Explorer 3.02 or later).
     */
    public static Randomness.Source seedSource(final File dll) {
        return new Randomness.Source() {
            public String name() {
                return "Windows CryptGenRandom";
            }

            public byte[] bytes(int n) {
                String why = load(dll);
                if (why != null) {
                    Log.info("random: no Windows seed: " + why);
                    return null;
                }
                byte[] b = new byte[n];
                int rc;
                try {
                    rc = nativeRandom(b);
                } catch (Throwable t) {
                    Log.info("random: no Windows seed: " + t);
                    return null;
                }
                if (rc != n) {
                    Log.info("random: no Windows seed: CryptGenRandom unavailable (code " + rc + ")");
                    return null;
                }
                return b;
            }
        };
    }

    /** Called by gwtray.dll on the tray thread. Must return quickly. */
    static void onEvent(final int code) {
        if (code == EVENT_READY) {
            Latch r = ready;
            if (r != null) {
                r.release();
            }
            return;
        }
        final Listener l = listener;
        if (l != null) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    l.trayAction(code);
                }
            });
        }
    }

    /**
     * Loads gwtray.dll and shows the icon.
     *
     * @return null on success, otherwise why the tray is unavailable (the
     *         caller then falls back to a normal window)
     */
    public static synchronized String start(File dll, File icon, String tooltip, String[] items, int defaultItem,
            Listener l) {
        String why = load(dll);
        if (why != null) {
            return why;
        }
        if (thread != null) {
            return null; // already running
        }

        listener = l;
        ready = new Latch();
        final String iconPath = icon.getAbsolutePath();
        final String tip = ascii(tooltip, 63);
        final String[] labels = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            labels[i] = ascii(items[i], 63);
        }
        final int[] result = new int[] { Integer.MIN_VALUE };
        final int def = defaultItem;
        Thread t = new Thread("gwtray") {
            public void run() {
                int rc;
                try {
                    rc = nativeRun(iconPath, tip, labels, def);
                } catch (Throwable e) {
                    Log.error("tray thread failed", e);
                    rc = -100;
                }
                final boolean lost;
                synchronized (result) {
                    result[0] = rc;
                    lost = up && !stopping;
                    up = false;
                }
                Log.info("tray message loop ended: " + rc + (lost ? " (unexpectedly)" : ""));
                ready.release(); // releases start() if setup failed
                final Listener l = listener;
                if (lost && l != null) {
                    final int code = rc;
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            l.trayLost("the tray icon stopped unexpectedly (code " + code + ")");
                        }
                    });
                }
            }
        };
        t.setDaemon(false); // keeps the JVM alive while the icon is up; see the class comment
        stopping = false;
        t.start();
        try {
            if (!ready.await(10000)) {
                return "the tray icon did not appear within 10 seconds";
            }
        } catch (InterruptedException e) {
            return "interrupted";
        }
        synchronized (result) {
            if (result[0] != Integer.MIN_VALUE) {
                return "the tray icon could not be created (code " + result[0] + ")";
            }
            up = true;
        }
        thread = t;
        return null;
    }

    public static void setTooltip(String tooltip) {
        if (thread != null) {
            nativeSetTooltip(ascii(tooltip, 63));
        }
    }

    /** Removes the icon and ends the tray thread. */
    public static synchronized void stop() {
        if (thread == null) {
            return;
        }
        stopping = true;
        nativeQuit();
        try {
            thread.join(3000);
        } catch (InterruptedException e) {
            // give up waiting; the thread ends on its own
        }
        thread = null;
        listener = null;
    }

    /** The DLL works in ANSI; keep labels to printable ASCII. */
    static String ascii(String s, int max) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length() && sb.length() < max; i++) {
            char c = s.charAt(i);
            sb.append(c >= 0x20 && c < 0x7f ? c : '?');
        }
        return sb.toString();
    }
}
