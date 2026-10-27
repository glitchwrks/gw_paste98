/*
 * Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client;

/** Application name and version. */
public final class Version {

    /** Called gw_paste98 up to 0.8.1. */
    public static final String NAME = "Paste Jar";
    public static final String VERSION = "0.9.0";
    /** NAME as an HTTP product token (no spaces). */
    public static final String TOKEN = "PasteJar";

    private Version() {
    }

    /** User-Agent header for API requests. */
    public static String userAgent() {
        return TOKEN + "/" + VERSION + " (Java " + System.getProperty("java.version") + "; "
                + System.getProperty("os.name") + " " + System.getProperty("os.version") + ")";
    }
}
