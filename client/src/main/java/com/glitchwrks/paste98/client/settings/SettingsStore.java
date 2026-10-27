/*
 * Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.glitchwrks.paste98.client.Log;
import com.glitchwrks.paste98.client.prefs.PrefsXml;
import com.glitchwrks.paste98.client.prefs.SettingsFile;

/**
 * Loads, validates and saves {@link Settings} in the settings file, and
 * tells listeners when they change. Thread-safe; listeners are called on the
 * thread that saved.
 */
public final class SettingsStore {

    /** Notified after settings are saved. */
    public interface Listener {
        void settingsChanged(Values before, Values after);
    }

    private final SettingsFile file;
    private final List listeners = new ArrayList();
    private Values current;
    private final List loadProblems = new ArrayList();

    /**
     * @param file the settings file (new SettingsFile(SettingsFile.defaultFile(), Settings.NODE) in the program)
     */
    public SettingsStore(SettingsFile file) {
        this.file = file;
        this.current = read();
    }

    /** The settings file, for Status. */
    public java.io.File getFile() {
        return file.getFile();
    }

    public synchronized Values current() {
        return current;
    }

    /** Stored values that were invalid and replaced by defaults, for the log and Status. */
    public synchronized List getLoadProblems() {
        return new ArrayList(loadProblems);
    }

    public synchronized void addListener(Listener l) {
        listeners.add(l);
    }

    /** True if settings were ever saved (as opposed to all defaults on a fresh install). */
    public boolean hasBeenSaved() {
        return file.get(Settings.VERSION_KEY) != null;
    }

    private Values read() {
        Map m = new HashMap();
        loadProblems.clear();
        if (!file.exists()) {
            return new Values(m); // fresh install: all defaults
        }
        List all = Settings.all();
        for (int i = 0; i < all.size(); i++) {
            Setting s = (Setting) all.get(i);
            String text = file.get(s.getKey());
            if (text == null) {
                continue;
            }
            try {
                m.put(s, s.parse(text));
            } catch (InvalidSettingException e) {
                String msg = "ignoring stored " + s.getKey() + " (" + e.getMessage() + "); using the default";
                loadProblems.add(msg);
                Log.info("settings: " + msg);
            }
        }
        return new Values(m);
    }

    /**
     * Saves all values and notifies listeners.
     *
     * @throws IOException if the settings file cannot be written
     */
    public void save(Values values) throws IOException {
        Values before;
        List ls;
        synchronized (this) {
            List all = Settings.all();
            for (int i = 0; i < all.size(); i++) {
                Setting st = (Setting) all.get(i);
                file.put(st.getKey(), st.format(values.get(st)));
            }
            file.put(Settings.VERSION_KEY, Integer.toString(Settings.CURRENT_VERSION));
            file.flush();
            before = current;
            current = values;
            loadProblems.clear();
            ls = new ArrayList(listeners);
        }
        for (int i = 0; i < ls.size(); i++) {
            ((Listener) ls.get(i)).settingsChanged(before, values);
        }
    }

    /** Remembers or forgets the username, leaving other settings as they are. */
    public void rememberUsername(String username) throws IOException {
        save(current().with(Settings.USERNAME, username == null ? "" : username));
    }

    /**
     * Writes the saved settings in Java's standard preferences XML format (as
     * java.util.prefs exportSubtree did in versions up to 0.7).
     */
    public void exportXml(OutputStream out) throws IOException {
        PrefsXml.write(out, file.getNode(), file.entries());
    }

    // ---- importing the old PASTE98.CFG / PASTE98.USR ---------------------------

    public static final String LEGACY_SETTINGS = "PASTE98.CFG";
    public static final String LEGACY_USER = "PASTE98.USR";
    public static final String LEGACY_RENAMED = "PASTE98.OLD";

    /**
     * On the first run after upgrading from version 0.1, copies the settings
     * from PASTE98.CFG and PASTE98.USR, then renames PASTE98.CFG to
     * PASTE98.OLD (so it is not mistaken for the live settings) and deletes
     * PASTE98.USR. Does nothing if settings were already saved or there is no
     * old file.
     *
     * @return a description of what was imported, or null if nothing was
     */
    public String importLegacy(File dir) {
        File cfg = new File(dir, LEGACY_SETTINGS);
        if (hasBeenSaved() || !cfg.isFile()) {
            return null;
        }
        Properties p = new Properties();
        try {
            load(p, cfg);
            File usr = new File(dir, LEGACY_USER);
            if (usr.isFile()) {
                Properties u = new Properties();
                load(u, usr);
                if (u.getProperty("username") != null) {
                    p.setProperty("username", u.getProperty("username"));
                }
            }
        } catch (IOException e) {
            Log.info("settings: cannot read " + cfg + ": " + e);
            return null;
        }

        // Old property name -> new setting.
        String[][] mapping = {
            { "host", Settings.HOST.getKey() },
            { "port", Settings.PORT.getKey() },
            { "pins", Settings.PINS.getKey() },
            { "cipher", Settings.CIPHER.getKey() },
            { "username", Settings.USERNAME.getKey() },
        };
        Values v = current();
        List imported = new ArrayList();
        List skipped = new ArrayList();
        for (int i = 0; i < mapping.length; i++) {
            String text = p.getProperty(mapping[i][0]);
            if (text == null || text.trim().length() == 0) {
                continue;
            }
            Setting s = Settings.byKey(mapping[i][1]);
            try {
                v = withParsed(v, s, text);
                imported.add(mapping[i][0]);
            } catch (InvalidSettingException e) {
                skipped.add(mapping[i][0] + " (" + e.getMessage() + ")");
            }
        }
        try {
            save(v);
        } catch (IOException e) {
            Log.info("settings: cannot save imported settings: " + e.getMessage());
            return null;
        }

        File renamed = new File(dir, LEGACY_RENAMED);
        if (renamed.exists()) {
            renamed.delete();
        }
        if (!cfg.renameTo(renamed)) {
            Log.info("settings: imported, but cannot rename " + cfg + " to " + renamed);
        }
        new File(dir, LEGACY_USER).delete();

        String msg = "imported " + imported + " from " + LEGACY_SETTINGS + " (now " + LEGACY_RENAMED + ")"
                + (skipped.isEmpty() ? "" : "; skipped invalid " + skipped);
        Log.info("settings: " + msg);
        return msg;
    }

    /** Parses text for s and returns v with that value set. */
    static Values withParsed(Values v, Setting s, String text) throws InvalidSettingException {
        return v.with(s, s.parse(text));
    }

    private static void load(Properties p, File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            p.load(in);
        } finally {
            in.close();
        }
    }
}
