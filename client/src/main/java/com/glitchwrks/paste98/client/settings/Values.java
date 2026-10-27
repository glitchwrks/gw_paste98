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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.glitchwrks.paste98.tls.TlsOptions;

/**
 * An immutable set of setting values. Settings with no value take their
 * default. Obtain the current values from {@link SettingsStore#current()}.
 * Typed getters: getText for Text, Host and Choice settings, getInt, is
 * (Flag) and getList (Pins).
 */
public final class Values {

    private final Map values;

    Values(Map values) {
        this.values = new HashMap(values);
    }

    /** All defaults. */
    public static Values defaults() {
        return new Values(new HashMap());
    }

    /** The value (String, Integer, Boolean or List), or the setting's default. */
    public Object get(Setting s) {
        Object v = values.get(s);
        return v == null ? s.getDefault() : v;
    }

    public String getText(Setting s) {
        return (String) get(s);
    }

    public int getInt(Setting.Int s) {
        return ((Integer) get(s)).intValue();
    }

    public boolean is(Setting.Flag s) {
        return ((Boolean) get(s)).booleanValue();
    }

    /** A Pins setting's value: an unmodifiable List of String. */
    public List getList(Setting.Pins s) {
        return (List) get(s);
    }

    /** A copy with one value changed. */
    public Values with(Setting s, Object value) {
        Map m = new HashMap(values);
        m.put(s, value);
        return new Values(m);
    }

    /** True once the server key is pinned: the program refuses to connect without a pin. */
    public boolean isConfigured() {
        return !getList(Settings.PINS).isEmpty();
    }

    /** Connection settings for the TLS client. */
    public TlsOptions tlsOptions() {
        TlsOptions o = new TlsOptions().setHost(getText(Settings.HOST)).setPort(getInt(Settings.PORT))
                .setCipher(getText(Settings.CIPHER)).setGroup(getText(Settings.GROUP))
                .setTimeoutMs(getInt(Settings.TIMEOUT) * 1000);
        List pins = getList(Settings.PINS);
        for (int i = 0; i < pins.size(); i++) {
            o.addPin((String) pins.get(i));
        }
        return o;
    }

    /** The remembered username, or null if none. */
    public String rememberedUsername() {
        String u = getText(Settings.USERNAME);
        return u.length() == 0 ? null : u;
    }

    /** True if host or port differ: a password for one server must not be sent to another. */
    public boolean sameServerAs(Values other) {
        return getText(Settings.HOST).equalsIgnoreCase(other.getText(Settings.HOST))
                && getInt(Settings.PORT) == other.getInt(Settings.PORT);
    }

    /** True if anything that affects connections differs. */
    public boolean sameConnectionAs(Values other) {
        return sameServerAs(other) && getList(Settings.PINS).equals(other.getList(Settings.PINS))
                && getText(Settings.CIPHER).equals(other.getText(Settings.CIPHER))
                && getText(Settings.GROUP).equals(other.getText(Settings.GROUP))
                && getInt(Settings.TIMEOUT) == other.getInt(Settings.TIMEOUT);
    }
}
