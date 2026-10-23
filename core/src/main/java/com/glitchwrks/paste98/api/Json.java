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

/**
 * Minimal JSON string encoding. Every character outside printable ASCII is
 * written as a JSON "backslash-u" hex escape, so the request body is pure ASCII regardless of
 * the platform's default charset (cp1252 on an English Windows 98).
 */
public final class Json {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Json() {
    }

    /** Returns s as a quoted JSON string literal. */
    public static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '"':
                sb.append("\\\"");
                break;
            case '\\':
                sb.append("\\\\");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\t':
                sb.append("\\t");
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
        sb.append('"');
        return sb.toString();
    }

    /** Builds the body for POST /api/pastes. */
    public static String pasteBody(String name, String content) {
        return "{\"name\":" + quote(name) + ",\"content\":" + quote(content) + "}";
    }
}
