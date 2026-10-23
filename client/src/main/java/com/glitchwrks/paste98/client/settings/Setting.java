/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
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
 * stored as text in java.util.prefs, so a hand-edited or out-of-range value
 * is caught by {@link #parse(String)} and replaced by the default.
 *
 * @param <T> the Java type of the value
 */
public abstract class Setting<T> {

    private final String key;
    private final String section;
    private final String label;
    private final String help;
    private final T defaultValue;

    Setting(String key, String section, String label, String help, T defaultValue) {
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

    public T getDefault() {
        return defaultValue;
    }

    /**
     * Converts and validates text, as stored or as typed in the dialog.
     *
     * @throws InvalidSettingException with a message for the user
     */
    public abstract T parse(String text) throws InvalidSettingException;

    /** The stored text for a value; parse(format(v)) equals v. */
    public abstract String format(T value);

    public String toString() {
        return key;
    }

    InvalidSettingException invalid(String why) {
        return new InvalidSettingException(this, label + ": " + why);
    }

    // ---- types ---------------------------------------------------------------

    /** Free text, optionally required, with a length limit and no control characters. */
    public static class Text extends Setting<String> {
        private final boolean required;
        private final int maxLength;

        Text(String key, String section, String label, String help, String defaultValue, boolean required,
                int maxLength) {
            super(key, section, label, help, defaultValue);
            this.required = required;
            this.maxLength = maxLength;
        }

        public String parse(String text) throws InvalidSettingException {
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

        public String format(String value) {
            return value;
        }
    }

    /** A host name or IP address. */
    public static final class Host extends Text {
        Host(String key, String section, String label, String help, String defaultValue) {
            super(key, section, label, help, defaultValue, true, 253);
        }

        public String parse(String text) throws InvalidSettingException {
            String s = super.parse(text);
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
    public static final class Int extends Setting<Integer> {
        private final int min;
        private final int max;
        private final String unit;

        Int(String key, String section, String label, String help, int defaultValue, int min, int max, String unit) {
            super(key, section, label, help, Integer.valueOf(defaultValue));
            this.min = min;
            this.max = max;
            this.unit = unit;
        }

        public Integer parse(String text) throws InvalidSettingException {
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
            return Integer.valueOf(v);
        }

        public String format(Integer value) {
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
    public static final class Flag extends Setting<Boolean> {
        Flag(String key, String section, String label, String help, boolean defaultValue) {
            super(key, section, label, help, Boolean.valueOf(defaultValue));
        }

        public Boolean parse(String text) throws InvalidSettingException {
            String s = text == null ? "" : text.trim();
            if (s.equalsIgnoreCase("true")) {
                return Boolean.TRUE;
            }
            if (s.equalsIgnoreCase("false")) {
                return Boolean.FALSE;
            }
            throw invalid("must be true or false");
        }

        public String format(Boolean value) {
            return value.toString();
        }
    }

    /** One of a fixed set of stored values, each with a display name. */
    public static final class Choice extends Setting<String> {
        private final String[] values;
        private final String[] names;

        Choice(String key, String section, String label, String help, String defaultValue, String[] values,
                String[] names) {
            super(key, section, label, help, defaultValue);
            this.values = values;
            this.names = names;
        }

        public String parse(String text) throws InvalidSettingException {
            String s = text == null ? "" : text.trim();
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(s)) {
                    return s;
                }
            }
            throw invalid("\"" + s + "\" is not one of the choices");
        }

        public String format(String value) {
            return value;
        }

        public String[] getValues() {
            return values.clone();
        }

        public String[] getNames() {
            return names.clone();
        }
    }

    /**
     * SHA-256 SPKI pins: stored space-separated, typed one per line (spaces,
     * commas and blank lines are also accepted). Empty is allowed here; the
     * program refuses to connect until at least one pin is set.
     */
    public static final class Pins extends Setting<List<String>> {
        Pins(String key, String section, String label, String help) {
            super(key, section, label, help, Collections.<String>emptyList());
        }

        public List<String> parse(String text) throws InvalidSettingException {
            List<String> pins = new ArrayList<String>();
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

        public String format(List<String> value) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < value.size(); i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(value.get(i));
            }
            return sb.toString();
        }
    }
}
