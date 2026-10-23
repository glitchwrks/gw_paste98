/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.tray;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import com.glitchwrks.paste98.client.Log;

/**
 * Notification area icon through gwtray.dll (see native/gwtray.c). Java 5 has
 * no tray API of its own.
 *
 * nativeRun() runs the Win32 message loop on a dedicated "gwtray" thread
 * until quit(). The DLL calls {@link #onEvent(int)} on that thread; events are
 * handed to the listener on the Swing event thread.
 */
public final class NativeTray {

    /** Must match GWTRAY_VERSION in gwtray.c. */
    static final int EXPECTED_VERSION = 1;

    static final int EVENT_DEFAULT = -1;
    static final int EVENT_READY = -2;

    /** Tray events, delivered on the Swing event thread. */
    public interface Listener {
        /** A menu item was chosen (index into the labels), or -1 for a double-click on the icon. */
        void trayAction(int item);
    }

    private static volatile Listener listener;
    private static volatile CountDownLatch ready;
    private static Thread thread;
    private static boolean loaded;

    private NativeTray() {
    }

    static native int nativeVersion();

    static native int nativeRun(String iconPath, String tooltip, String[] items, int defaultItem);

    static native void nativeSetTooltip(String tooltip);

    static native void nativeQuit();

    /** Called by gwtray.dll on the tray thread. Must return quickly. */
    static void onEvent(final int code) {
        if (code == EVENT_READY) {
            CountDownLatch r = ready;
            if (r != null) {
                r.countDown();
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
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return "the tray icon needs Windows";
        }
        if (!loaded) {
            if (!dll.isFile()) {
                return dll.getName() + " not found in " + dll.getParent();
            }
            try {
                System.load(dll.getAbsolutePath());
            } catch (Throwable t) { // UnsatisfiedLinkError, SecurityException
                return "could not load " + dll.getName() + ": " + t;
            }
            loaded = true;
            int v;
            try {
                v = nativeVersion();
            } catch (Throwable t) {
                return dll.getName() + " is not usable: " + t;
            }
            if (v != EXPECTED_VERSION) {
                return dll.getName() + " is version " + v + "; this program needs version " + EXPECTED_VERSION;
            }
        }
        if (thread != null) {
            return null; // already running
        }

        listener = l;
        ready = new CountDownLatch(1);
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
                synchronized (result) {
                    result[0] = rc;
                }
                Log.info("tray message loop ended: " + rc);
                ready.countDown(); // releases start() if setup failed
            }
        };
        t.setDaemon(true);
        t.start();
        try {
            if (!ready.await(10, TimeUnit.SECONDS)) {
                return "the tray icon did not appear within 10 seconds";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
        synchronized (result) {
            if (result[0] != Integer.MIN_VALUE) {
                return "the tray icon could not be created (code " + result[0] + ")";
            }
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
        nativeQuit();
        try {
            thread.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        thread = null;
        listener = null;
    }

    /** The DLL works in ANSI; keep labels to printable ASCII. */
    static String ascii(String s, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < max; i++) {
            char c = s.charAt(i);
            sb.append(c >= 0x20 && c < 0x7f ? c : '?');
        }
        return sb.toString();
    }
}
