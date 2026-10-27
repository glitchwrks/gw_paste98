/*
 * Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.prefs;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import com.glitchwrks.paste98.client.AppDir;

/**
 * The program's settings: one node (key/value map) of the preferences tree
 * stored in PASTE98.PRF in the program folder, not in the Windows registry.
 *
 * Versions up to 0.7 reached this file through java.util.prefs with a custom
 * backend; Java 1.3 has no java.util.prefs, so the program now uses the store
 * directly. The file format, and so existing settings files, are unchanged.
 *
 * The system property paste98.prefs.user overrides the file (used by tests).
 */
public final class SettingsFile {

    public static final String USER_FILE = "PASTE98.PRF";
    public static final String PROPERTY = "paste98.prefs.user";

    private final FileStore store;
    private final String path;

    /**
     * @param file the settings file
     * @param node the node path, e.g. "com/glitchwrks/paste98"
     */
    public SettingsFile(File file, String node) {
        this.store = new FileStore(file.getAbsoluteFile(), "Paste Jar settings");
        this.path = "/" + node;
    }

    /** The program's settings file: PASTE98.PRF in the program folder, unless overridden. */
    public static File defaultFile() {
        String override = System.getProperty(PROPERTY);
        return override != null ? new File(override).getAbsoluteFile() : AppDir.file(USER_FILE);
    }

    public File getFile() {
        return store.getFile();
    }

    /** True if the node has ever been written (a fresh install has no settings file). */
    public boolean exists() {
        return store.exists(path);
    }

    /** The stored value, or null. */
    public String get(String key) {
        return store.get(path, key);
    }

    /** Sets a value in memory; flush() writes the file. */
    public void put(String key, String value) {
        store.put(path, key, value);
    }

    /** Writes the file if anything changed. */
    public void flush() throws IOException {
        store.flush();
    }

    /** All keys and values of the node, sorted by key. */
    public Map entries() {
        Map m = new TreeMap();
        String[] keys = store.keys(path);
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], store.get(path, keys[i]));
        }
        return m;
    }

    /** The node path without the leading '/', e.g. "com/glitchwrks/paste98". */
    public String getNode() {
        return path.substring(1);
    }
}
