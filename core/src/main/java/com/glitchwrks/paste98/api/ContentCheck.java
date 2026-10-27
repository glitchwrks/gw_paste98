/*
 * Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.api;

/**
 * Checks paste content against what the server can store. The server keeps
 * content in a MariaDB TEXT column with the latin1 character set: at most
 * 65,535 bytes, one byte per character, and characters outside Latin-1 are
 * not stored faithfully.
 */
public final class ContentCheck {

    /** Largest content the TEXT column holds, in latin1 bytes (= characters). */
    public static final int MAX_CHARS = 65535;

    private final int length;
    private final int nonLatin1;

    private ContentCheck(int length, int nonLatin1) {
        this.length = length;
        this.nonLatin1 = nonLatin1;
    }

    public static ContentCheck of(String content) {
        int bad = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) > 0xff) {
                bad++;
            }
        }
        return new ContentCheck(content.length(), bad);
    }

    public boolean isEmpty() {
        return length == 0;
    }

    public boolean isTooLong() {
        return length > MAX_CHARS;
    }

    public int getLength() {
        return length;
    }

    /** Characters the server will not store faithfully (outside Latin-1). */
    public int getNonLatin1Count() {
        return nonLatin1;
    }

    /** True if the paste can be sent as is. Non-Latin-1 characters are a warning, not an error. */
    public boolean isSendable() {
        return !isEmpty() && !isTooLong();
    }
}
