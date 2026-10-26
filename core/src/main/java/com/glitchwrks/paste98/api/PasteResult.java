/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.api;

/** A paste that was created. */
public final class PasteResult {

    private final String name;
    private final String url;
    private final int attempts;

    public PasteResult(String name, String url, int attempts) {
        this.name = name;
        this.url = url;
        this.attempts = attempts;
    }

    public String getName() {
        return name;
    }

    /** Where the paste can be read, e.g. https://pastes.glitchwrks.com/abcd1234 */
    public String getUrl() {
        return url;
    }

    /** How many names were tried (more than 1 only after a name clash). */
    public int getAttempts() {
        return attempts;
    }
}
