/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.prefs;

import java.io.File;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

import com.glitchwrks.paste98.client.AppDir;

/**
 * A java.util.prefs backend that stores settings in files in the program
 * folder instead of the Windows registry (Java's default backend on Windows
 * writes to HKEY_CURRENT_USER\Software\JavaSoft\Prefs).
 *
 * <pre>
 *   user preferences     PASTE98.PRF
 *   system preferences   PASTE98S.PRF   (unused by gw_paste98; created only if written)
 * </pre>
 *
 * The system properties paste98.prefs.user and paste98.prefs.system override
 * the file locations (used by tests).
 *
 * Install with {@link #install()} as the very first thing the program does:
 * java.util.prefs.Preferences reads the java.util.prefs.PreferencesFactory
 * property once, when the Preferences class is first used.
 */
public final class FilePreferencesFactory implements PreferencesFactory {

    public static final String PROPERTY = "java.util.prefs.PreferencesFactory";
    public static final String USER_FILE = "PASTE98.PRF";
    public static final String SYSTEM_FILE = "PASTE98S.PRF";

    private static FileNode userRoot;
    private static FileNode systemRoot;
    private static boolean hookInstalled;

    /** Public no-argument constructor: java.util.prefs creates the factory by reflection. */
    public FilePreferencesFactory() {
    }

    /**
     * Makes this the backend for Preferences.userRoot() and systemRoot(),
     * unless something already chose one on the command line.
     */
    public static void install() {
        if (System.getProperty(PROPERTY) == null) {
            System.setProperty(PROPERTY, FilePreferencesFactory.class.getName());
        }
    }

    /** True if Preferences.userRoot() is stored by this class rather than, say, the registry. */
    public static boolean isActive() {
        return Preferences.userRoot() instanceof FileNode;
    }

    public Preferences userRoot() {
        return user();
    }

    public Preferences systemRoot() {
        return system();
    }

    /** The user root, whether or not this factory was installed globally. */
    public static synchronized Preferences user() {
        if (userRoot == null) {
            userRoot = new FileNode(new FileStore(location("paste98.prefs.user", USER_FILE),
                    "gw_paste98 settings (java.util.prefs user preferences)"), true);
            installShutdownHook();
        }
        return userRoot;
    }

    static synchronized Preferences system() {
        if (systemRoot == null) {
            systemRoot = new FileNode(new FileStore(location("paste98.prefs.system", SYSTEM_FILE),
                    "gw_paste98 system preferences (java.util.prefs)"), false);
            installShutdownHook();
        }
        return systemRoot;
    }

    /**
     * A separate user preferences tree stored in the given file, independent
     * of the program's own. For tests and tools.
     */
    public static Preferences openUserTree(File file) {
        return new FileNode(new FileStore(file.getAbsoluteFile(), "gw_paste98 settings (java.util.prefs user preferences)"),
                true);
    }

    /** The file the user preferences are stored in. */
    public static File userFile() {
        return location("paste98.prefs.user", USER_FILE);
    }

    private static File location(String property, String defaultName) {
        String override = System.getProperty(property);
        return override != null ? new File(override).getAbsoluteFile() : AppDir.file(defaultName);
    }

    /** Saves anything unsaved when the program exits, as the JDK's own backends do. */
    private static void installShutdownHook() {
        if (hookInstalled) {
            return;
        }
        hookInstalled = true;
        Runtime.getRuntime().addShutdownHook(new Thread("prefs-flush") {
            public void run() {
                flushQuietly(userRoot);
                flushQuietly(systemRoot);
            }
        });
    }

    private static void flushQuietly(Preferences p) {
        if (p == null) {
            return;
        }
        try {
            p.flush();
        } catch (BackingStoreException e) {
            System.err.println("preferences: " + e.getMessage());
        } catch (IllegalStateException e) {
            // node removed; nothing to save
        }
    }
}
