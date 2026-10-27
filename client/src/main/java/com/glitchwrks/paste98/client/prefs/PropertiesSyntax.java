/*
 * Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.prefs;

/**
 * Escaping for writing java.util.Properties files by hand, so the lines can
 * be sorted (Properties.store writes them in hash order). The output is read
 * back with Properties.load, and is pure ASCII.
 */
final class PropertiesSyntax {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private PropertiesSyntax() {
    }

    static String escapeKey(String s) {
        return escape(s, true);
    }

    static String escapeValue(String s) {
        return escape(s, false);
    }

    private static String escape(String s, boolean key) {
        StringBuffer sb = new StringBuffer(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '\\':
                sb.append("\\\\");
                break;
            case '\t':
                sb.append("\\t");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\f':
                sb.append("\\f");
                break;
            case ' ':
                // Spaces are significant in keys anywhere, in values only at the start.
                sb.append(key || i == 0 ? "\\ " : " ");
                break;
            case '=':
            case ':':
            case '#':
            case '!':
                // Only special in keys: a value never starts a line, so a
                // comment character or separator in it is read literally.
                if (key) {
                    sb.append('\\');
                }
                sb.append(c);
                break;
            default:
                if (c < 0x20 || c > 0x7e) {
                    sb.append("\\u").append(HEX[(c >> 12) & 0xf]).append(HEX[(c >> 8) & 0xf])
                            .append(HEX[(c >> 4) & 0xf]).append(HEX[c & 0xf]);
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
