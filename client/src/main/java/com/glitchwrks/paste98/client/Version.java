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

/** Application name and version. */
public final class Version {

    public static final String NAME = "gw_paste98";
    public static final String VERSION = "0.6.2";

    private Version() {
    }

    /** User-Agent header for API requests. */
    public static String userAgent() {
        return NAME + "/" + VERSION + " (Java " + System.getProperty("java.version") + "; "
                + System.getProperty("os.name") + " " + System.getProperty("os.version") + ")";
    }
}
