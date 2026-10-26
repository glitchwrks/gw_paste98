/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client;

import java.io.File;
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

    /**
     * A file: URL's path with %XX escapes decoded as UTF-8 (java.net.URI is
     * Java 1.4; URLDecoder would also wrongly turn '+' into a space).
     */
    static String decodePercent(String path) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < path.length(); i++) {
                char c = path.charAt(i);
                if (c == '%' && i + 2 < path.length()) {
                    out.write(Integer.parseInt(path.substring(i + 1, i + 3), 16));
                    i += 2;
                } else {
                    byte[] b = String.valueOf(c).getBytes("UTF-8");
                    out.write(b, 0, b.length);
                }
            }
            String s = new String(out.toByteArray(), "UTF-8");
            // "/C:/Program Files/x" on Windows: drop the slash before the drive letter
            if (s.length() > 2 && s.charAt(0) == '/' && s.charAt(2) == ':') {
                s = s.substring(1);
            }
            return s;
        } catch (java.io.UnsupportedEncodingException e) {
            return path;
        } catch (NumberFormatException e) {
            return path;
        }
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
                File f = new File(decodePercent(url.getFile()));
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
