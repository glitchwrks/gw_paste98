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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.glitchwrks.paste98.tls.TlsOptions;

/**
 * An immutable set of setting values. Settings with no value take their
 * default. Obtain the current values from {@link SettingsStore#current()}.
 */
public final class Values {

    private final Map<Setting<?>, Object> values;

    Values(Map<Setting<?>, Object> values) {
        this.values = new HashMap<Setting<?>, Object>(values);
    }

    /** All defaults. */
    public static Values defaults() {
        return new Values(new HashMap<Setting<?>, Object>());
    }

    public <T> T get(Setting<T> s) {
        Object v = values.get(s);
        @SuppressWarnings("unchecked")
        T t = v == null ? s.getDefault() : (T) v;
        return t;
    }

    public int getInt(Setting.Int s) {
        return get(s).intValue();
    }

    public boolean is(Setting.Flag s) {
        return get(s).booleanValue();
    }

    /** A copy with one value changed. */
    public <T> Values with(Setting<T> s, T value) {
        Map<Setting<?>, Object> m = new HashMap<Setting<?>, Object>(values);
        m.put(s, value);
        return new Values(m);
    }

    /** True once the server key is pinned: the program refuses to connect without a pin. */
    public boolean isConfigured() {
        return !get(Settings.PINS).isEmpty();
    }

    /** Connection settings for the TLS client. */
    public TlsOptions tlsOptions() {
        TlsOptions o = new TlsOptions().setHost(get(Settings.HOST)).setPort(getInt(Settings.PORT))
                .setCipher(get(Settings.CIPHER)).setGroup(get(Settings.GROUP))
                .setTimeoutMs(getInt(Settings.TIMEOUT) * 1000);
        List<String> pins = get(Settings.PINS);
        for (int i = 0; i < pins.size(); i++) {
            o.addPin(pins.get(i));
        }
        return o;
    }

    /** The remembered username, or null if none. */
    public String rememberedUsername() {
        String u = get(Settings.USERNAME);
        return u.length() == 0 ? null : u;
    }

    /** True if host or port differ: a password for one server must not be sent to another. */
    public boolean sameServerAs(Values other) {
        return get(Settings.HOST).equalsIgnoreCase(other.get(Settings.HOST))
                && getInt(Settings.PORT) == other.getInt(Settings.PORT);
    }

    /** True if anything that affects connections differs. */
    public boolean sameConnectionAs(Values other) {
        return sameServerAs(other) && get(Settings.PINS).equals(other.get(Settings.PINS))
                && get(Settings.CIPHER).equals(other.get(Settings.CIPHER))
                && get(Settings.GROUP).equals(other.get(Settings.GROUP))
                && getInt(Settings.TIMEOUT) == other.getInt(Settings.TIMEOUT);
    }
}
