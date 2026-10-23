/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;

/**
 * The folder the application was installed to (where GWPASTE.JAR is), which
 * holds the DLL, icon, settings and log. Falls back to the current directory.
 */
public final class AppDir {

    private static File dir;

    private AppDir() {
    }

    public static synchronized File get() {
        if (dir == null) {
            dir = find();
        }
        return dir;
    }

    public static File file(String name) {
        return new File(get(), name);
    }

    private static File find() {
        String override = System.getProperty("paste98.dir");
        if (override != null) {
            return new File(override).getAbsoluteFile();
        }
        try {
            CodeSource cs = AppDir.class.getProtectionDomain().getCodeSource();
            if (cs != null) {
                URL url = cs.getLocation();
                File f = new File(new URI(url.toString()));
                File parent = f.isDirectory() ? f : f.getParentFile();
                if (parent != null) {
                    return parent.getAbsoluteFile();
                }
            }
        } catch (Exception e) {
            // fall through to the working directory
        }
        return new File(".").getAbsoluteFile();
    }
}
