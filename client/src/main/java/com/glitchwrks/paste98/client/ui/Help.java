/*
 * Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.ui;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import com.glitchwrks.paste98.client.AppDir;
import com.glitchwrks.paste98.client.Log;

/**
 * Opens GWPASTE.CHM (built from client/src/help) with Windows' HTML Help
 * viewer, hh.exe, which comes with Windows 98 and Internet Explorer 4+.
 */
public final class Help {

    public static final String FILE = "GWPASTE.CHM";

    public static final String OVERVIEW = "overview.htm";
    public static final String KEY_PIN = "keypin.htm";

    private Help() {
    }

    /** The help page for a Settings tab, e.g. "Server" -> "set_server.htm". */
    public static String settingsTopic(String section) {
        return "set_" + section.toLowerCase(Locale.ENGLISH) + ".htm";
    }

    /** hh.exe's argument for a topic: "C:\...\GWPASTE.CHM::/topic.htm". */
    static String target(File chm, String topic) {
        return topic == null ? chm.getPath() : chm.getPath() + "::/" + topic;
    }

    /** Opens the help at a topic (null for the first page). Errors are shown, not thrown. */
    public static void show(Component parent, String topic) {
        File chm = AppDir.file(FILE);
        if (!chm.isFile()) {
            Dialogs.error(parent, "Help", "The help file is missing:\n" + chm.getPath()
                    + "\n\nReinstalling the program puts it back.");
            return;
        }
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            Dialogs.info(parent, "Help", "The help needs Windows' HTML Help viewer. It is in:\n" + chm.getPath());
            return;
        }
        String target = target(chm, topic);
        try {
            Process p = Runtime.getRuntime().exec(new String[] { "hh.exe", target });
            closeQuietly(p);
            Log.info("help: opened " + target);
        } catch (IOException e) {
            Log.info("help: cannot start hh.exe: " + e.getMessage());
            Dialogs.error(parent, "Help", "Cannot start the HTML Help viewer (hh.exe): " + e.getMessage()
                    + "\n\nIt comes with Windows 98, and with Internet Explorer 4 or later on Windows 95. The help file is:\n"
                    + chm.getPath());
        }
    }

    private static void closeQuietly(Process p) {
        try {
            p.getOutputStream().close();
            p.getInputStream().close();
            p.getErrorStream().close();
        } catch (IOException e) {
            // nothing useful to do
        }
    }
}
