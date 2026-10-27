/*
 * Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import com.glitchwrks.paste98.tls.SpkiPin;

/**
 * One configurable parameter: where it is stored, its type, default and
 * validation, and how it is described in the Settings dialog. Values are
 * stored as text in the settings file, so a hand-edited or out-of-range value
 * is caught by {@link #parse(String)} and replaced by the default.
 *
 * Values are objects of one type per kind of setting (String, Integer,
 * Boolean or List of String); Values has typed getters for each.
 */
public abstract class Setting {

    private final String key;
    private final String section;
    private final String label;
    private final String help;
    private final Object defaultValue;
    Setting(String key, String section, String label, String help, Object defaultValue) {
        this.key = key;
        this.section = section;
        this.label = label;
        this.help = help;
        this.defaultValue = defaultValue;
    }

    /** Preferences key, e.g. "server.host". */
    public String getKey() {
        return key;
    }

    /** Settings dialog tab. */
    public String getSection() {
        return section;
    }

    public String getLabel() {
        return label;
    }

    /** One or two sentences shown under the control. */
    public String getHelp() {
        return help;
    }

    public Object getDefault() {
        return defaultValue;
    }

    /**
     * Converts and validates text, as stored or as typed in the dialog.
     *
     * @throws InvalidSettingException with a message for the user
     */
    public abstract Object parse(String text) throws InvalidSettingException;

    /** The stored text for a value; parse(format(v)) equals v. */
    public abstract String format(Object value);

    public String toString() {
        return key;
    }

    InvalidSettingException invalid(String why) {
        return new InvalidSettingException(this, label + ": " + why);
    }

    // ---- types ---------------------------------------------------------------

    /** Free text, optionally required, with a length limit and no control characters. */
    public static class Text extends Setting {
        private final boolean required;
        private final int maxLength;

        Text(String key, String section, String label, String help, String defaultValue, boolean required,
                int maxLength) {
            super(key, section, label, help, defaultValue);
            this.required = required;
            this.maxLength = maxLength;
        }

        public Object parse(String text) throws InvalidSettingException {
            return parseText(text);
        }
        String parseText(String text) throws InvalidSettingException {
            String s = text == null ? "" : text.trim();
            if (required && s.length() == 0) {
                throw invalid("must not be empty");
            }
            if (s.length() > maxLength) {
                throw invalid("is longer than " + maxLength + " characters");
            }
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) < 0x20) {
                    throw invalid("contains a control character");
                }
            }
            return s;
        }

        public String format(Object value) {
            return (String) value;
        }
    }
    /** A host name or IP address. */
    public static final class Host extends Text {
        Host(String key, String section, String label, String help, String defaultValue) {
            super(key, section, label, help, defaultValue, true, 253);
        }

        public Object parse(String text) throws InvalidSettingException {
            String s = parseText(text);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                        || c == '.' || c == '-' || c == ':';
                if (!ok) {
                    throw invalid("\"" + s + "\" is not a host name (letters, digits, '.', '-' only)");
                }
            }
            return s;
        }
    }

    /** A whole number in a range. */
    public static final class Int extends Setting {
        private final int min;
        private final int max;
        private final String unit;

        Int(String key, String section, String label, String help, int defaultValue, int min, int max, String unit) {
            super(key, section, label, help, new Integer(defaultValue));
            this.min = min;
            this.max = max;
            this.unit = unit;
        }

        public Object parse(String text) throws InvalidSettingException {
            String s = text == null ? "" : text.trim();
            int v;
            try {
                v = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw invalid("\"" + s + "\" is not a whole number");
            }
            if (v < min || v > max) {
                throw invalid("must be between " + min + " and " + max);
            }
            return new Integer(v);
        }

        public String format(Object value) {
            return value.toString();
        }

        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }

        /** Unit shown after the field, e.g. "seconds"; may be empty. */
        public String getUnit() {
            return unit;
        }
    }

    /** On or off. */
    public static final class Flag extends Setting {
        Flag(String key, String section, String label, String help, boolean defaultValue) {
            super(key, section, label, help, defaultValue ? Boolean.TRUE : Boolean.FALSE);
        }

        public Object parse(String text) throws InvalidSettingException {
            String s = text == null ? "" : text.trim();
            if (s.equalsIgnoreCase("true")) {
                return Boolean.TRUE;
            }
            if (s.equalsIgnoreCase("false")) {
                return Boolean.FALSE;
            }
            throw invalid("must be true or false");
        }

        public String format(Object value) {
            return value.toString();
        }
    }

    /** One of a fixed set of stored values, each with a display name. */
    public static final class Choice extends Setting {
        private final String[] values;
        private final String[] names;

        Choice(String key, String section, String label, String help, String defaultValue, String[] values,
                String[] names) {
            super(key, section, label, help, defaultValue);
            this.values = values;
            this.names = names;
        }

        public Object parse(String text) throws InvalidSettingException {
            String s = text == null ? "" : text.trim();
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(s)) {
                    return s;
                }
            }
            throw invalid("\"" + s + "\" is not one of the choices");
        }

        public String format(Object value) {
            return (String) value;
        }
        public String[] getValues() {
            return (String[]) values.clone();
        }
        public String[] getNames() {
            return (String[]) names.clone();
        }
    }

    /**
     * SHA-256 SPKI pins: stored space-separated, typed one per line (spaces,
     * commas and blank lines are also accepted). Empty is allowed here; the
     * program refuses to connect until at least one pin is set.
     */
    public static final class Pins extends Setting {
        Pins(String key, String section, String label, String help) {
            super(key, section, label, help, Collections.EMPTY_LIST);
        }

        public Object parse(String text) throws InvalidSettingException {
            List pins = new ArrayList();
            StringTokenizer st = new StringTokenizer(text == null ? "" : text, " ,;\t\r\n");
            while (st.hasMoreTokens()) {
                String pin = st.nextToken();
                String p;
                try {
                    p = SpkiPin.normalise(pin);
                } catch (IllegalArgumentException e) {
                    throw invalid("\"" + pin + "\" is not a valid pin (a base64 SHA-256 hash, 44 characters "
                            + "ending in '=')");
                }
                if (!pins.contains(p)) {
                    pins.add(p);
                }
            }
            if (pins.size() > 8) {
                throw invalid("at most 8 pins");
            }
            return Collections.unmodifiableList(pins);
        }

        public String format(Object value) {
            List list = (List) value;
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(list.get(i));
            }
            return sb.toString();
        }
    }
}
