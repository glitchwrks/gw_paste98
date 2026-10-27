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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.glitchwrks.paste98.client.prefs.PrefsXml;

/**
 * Reads Paste Jar (formerly gw_paste98) settings out of a Java preferences XML export (as written
 * by {@link SettingsStore#exportXml}, or by versions up to 0.7 through
 * java.util.prefs), without importing it into the live settings: the values
 * go into the Settings dialog for review, and are only saved when the user
 * presses OK.
 */
public final class XmlImport {

    /** Values read from a file, and anything that could not be used. */
    public static final class Result {
        public final Values values;
        public final int count;
        /** Problem descriptions (String). */
        public final List problems;

        Result(Values values, int count, List problems) {
            this.values = values;
            this.count = count;
            this.problems = problems;
        }
    }

    private XmlImport() {
    }

    /**
     * @param base values for settings the file does not mention
     * @throws IOException if the file is not a preferences export containing Paste Jar settings
     */
    public static Result read(InputStream in, Values base) throws IOException {
        Map entries = PrefsXml.read(in, Settings.NODE);
        if (entries == null) {
            throw new IOException("the file contains no Paste Jar settings");
        }
        Values v = base;
        int count = 0;
        List problems = new ArrayList();
        for (Iterator it = entries.entrySet().iterator(); it.hasNext();) {
            Map.Entry e = (Map.Entry) it.next();
            Setting s = Settings.byKey((String) e.getKey());
            if (s == null) {
                continue; // settings.version, or from a newer version of the program
            }
            try {
                v = SettingsStore.withParsed(v, s, (String) e.getValue());
                count++;
            } catch (InvalidSettingException ex) {
                problems.add(ex.getMessage());
            }
        }
        return new Result(v, count, problems);
    }
}
