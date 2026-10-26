/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.ui;

import java.awt.Image;
import java.awt.Toolkit;
import java.io.File;
import java.net.URL;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileSystemView;

import com.glitchwrks.paste98.client.Log;

/** The program's pictures (native/make_icon.py, make_logo.py) and the Java runtime's icon. */
public final class AppIcons {

    private static Image window;
    private static boolean windowLoaded;

    private AppIcons() {
    }

    /** The 16x16 paste jar for window title bars (null if missing). */
    public static synchronized Image window() {
        if (!windowLoaded) {
            windowLoaded = true;
            URL png = AppIcons.class.getResource("/com/glitchwrks/paste98/client/gwtray16.png");
            window = png == null ? null : Toolkit.getDefaultToolkit().createImage(png);
        }
        return window;
    }

    /**
     * Puts the paste jar in the title bar of every dialog without a window of
     * ours as its owner. Java 5 dialogs show their owner's icon, and dialogs
     * with no owner belong to Swing's hidden shared frame, whose icon is Java's.
     */
    public static void install() {
        Image i = window();
        if (i != null) {
            JOptionPane.getRootFrame().setIconImage(i);
        }
    }

    /** The 64x64 paste jar for the About window (null if missing). */
    public static Icon about() {
        URL png = AppIcons.class.getResource("/com/glitchwrks/paste98/client/gwabout.png");
        return png == null ? null : new ImageIcon(png);
    }

    /**
     * The running Java's own icon, as Windows shows it for javaw.exe: 32x32
     * if available, else 16x16. Null off Windows or if Windows has none. Read
     * from the installed runtime rather than shipped with the program.
     */
    public static Icon javaRuntime() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return null;
        }
        File bin = new File(System.getProperty("java.home", ""), "bin");
        File exe = new File(bin, "javaw.exe");
        if (!exe.isFile()) {
            exe = new File(bin, "java.exe");
        }
        if (!exe.isFile()) {
            return null;
        }
        try {
            // sun.awt.shell.ShellFolder.getIcon(true) gives the 32x32 icon on Java 5;
            // reflection, as it is not a public API.
            Class<?> c = Class.forName("sun.awt.shell.ShellFolder");
            Object folder = c.getMethod("getShellFolder", new Class[] { File.class }).invoke(null,
                    new Object[] { exe });
            Object image = c.getMethod("getIcon", new Class[] { Boolean.TYPE }).invoke(folder,
                    new Object[] { Boolean.TRUE });
            if (image instanceof Image) {
                return new ImageIcon((Image) image);
            }
        } catch (Throwable t) {
            Log.info("about: no large Java icon (" + t + "); trying the small one");
        }
        try {
            return FileSystemView.getFileSystemView().getSystemIcon(exe);
        } catch (Throwable t) {
            Log.info("about: no Java icon: " + t);
            return null;
        }
    }
}
