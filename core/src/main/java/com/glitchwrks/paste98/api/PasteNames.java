/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.api;

import java.util.Random;

/**
 * Paste names: exactly 8 characters (the server's JSON schema and database
 * column), drawn here from lowercase letters and digits. The server enforces
 * uniqueness, so a clash is answered by trying another name.
 */
public final class PasteNames {

    public static final int LENGTH = 8;

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private PasteNames() {
    }

    /** A new random name. Pass a SecureRandom so names are not guessable. */
    public static String generate(Random random) {
        char[] c = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            c[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(c);
    }

    /** True if name is 8 characters of [A-Za-z0-9]. */
    public static boolean isValid(String name) {
        if (name == null || name.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < LENGTH; i++) {
            char ch = name.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9');
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
