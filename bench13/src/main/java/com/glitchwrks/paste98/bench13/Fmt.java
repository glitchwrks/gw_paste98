/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.bench13;

/** Number and column formatting; Java 1.3 has no String.format. */
final class Fmt {

    private Fmt() {
    }

    /** Pads or truncates to exactly width characters, left-aligned. */
    static String left(String s, int width) {
        if (s.length() >= width) {
            return s.substring(0, width);
        }
        StringBuffer sb = new StringBuffer(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /** Right-aligns to at least width characters. */
    static String right(String s, int width) {
        StringBuffer sb = new StringBuffer();
        for (int i = s.length(); i < width; i++) {
            sb.append(' ');
        }
        return sb.append(s).toString();
    }

    /** A number with a fixed number of decimals (half up), e.g. dec(3.14159, 2) = "3.14". */
    static String dec(double v, int places) {
        boolean neg = v < 0;
        if (neg) {
            v = -v;
        }
        long scale = 1;
        for (int i = 0; i < places; i++) {
            scale *= 10;
        }
        long r = (long) (v * scale + 0.5);
        String whole = Long.toString(r / scale);
        if (places == 0) {
            return (neg ? "-" : "") + whole;
        }
        String frac = Long.toString(r % scale);
        while (frac.length() < places) {
            frac = "0" + frac;
        }
        return (neg ? "-" : "") + whole + "." + frac;
    }

    /** Milliseconds as "1234 ms", right-aligned to 8 columns. */
    static String ms(long millis) {
        return right(Long.toString(millis), 6) + " ms";
    }
}
