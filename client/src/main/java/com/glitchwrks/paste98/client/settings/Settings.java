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

import com.glitchwrks.paste98.client.ui.Appearance;
import com.glitchwrks.paste98.tls.TlsOptions;

/**
 * Every configurable parameter, declared once. The Settings dialog, loading,
 * saving, validation and XML import are all driven by this list, so adding a
 * parameter means adding one declaration here (and using it).
 *
 * Stored under the java.util.prefs node {@link #NODE}.
 */
public final class Settings {

    /** java.util.prefs node holding the settings. */
    public static final String NODE = "com/glitchwrks/paste98";

    /** Key of the settings layout version, for future migrations. */
    public static final String VERSION_KEY = "settings.version";
    public static final int CURRENT_VERSION = 1;

    public static final String SERVER = "Server";
    public static final String CONNECTION = "Connection";
    public static final String PASTES = "Pastes";
    public static final String ACCOUNT = "Account";
    public static final String APPEARANCE = "Appearance";

    /** Dialog tab order. */
    public static final String[] SECTIONS = { SERVER, CONNECTION, PASTES, ACCOUNT, APPEARANCE };

    // ---- Server --------------------------------------------------------------

    public static final Setting.Host HOST = new Setting.Host("server.host", SERVER, "Host",
            "The pastes server's name, e.g. pastes.glitchwrks.com.", "pastes.glitchwrks.com");

    public static final Setting.Int PORT = new Setting.Int("server.port", SERVER, "Port",
            "HTTPS port, normally 443.", 443, 1, 65535, "");

    public static final Setting.Pins PINS = new Setting.Pins("server.pins", SERVER, "Key pins",
            "One per line: the base64 SHA-256 hash of the server's public key. The program only talks to a "
                    + "server holding one of these keys. Add a backup key's pin as a second line. \"Get pin from "
                    + "server\" reads it from the server, but you must check it against a trusted source.");

    // ---- Connection ------------------------------------------------------------

    public static final Setting.Choice CIPHER = new Setting.Choice("tls.cipher", CONNECTION, "Cipher",
            "ChaCha20 is fastest on CPUs without AES instructions. The server makes the final choice.",
            TlsOptions.CIPHER_AUTO,
            new String[] { TlsOptions.CIPHER_AUTO, TlsOptions.CIPHER_CHACHA, TlsOptions.CIPHER_AES128,
                TlsOptions.CIPHER_AES256 },
            new String[] { "Automatic (ChaCha20 preferred)", "ChaCha20-Poly1305 only", "AES-128-GCM only",
                "AES-256-GCM only" });

    public static final Setting.Choice GROUP = new Setting.Choice("tls.group", CONNECTION, "Key exchange",
            "X25519 is about three times faster than P-256 on a Pentium III.", TlsOptions.GROUP_AUTO,
            new String[] { TlsOptions.GROUP_AUTO, TlsOptions.GROUP_X25519, TlsOptions.GROUP_P256 },
            new String[] { "Automatic (X25519 preferred)", "X25519 only", "P-256 only" });

    public static final Setting.Int TIMEOUT = new Setting.Int("net.timeout", CONNECTION, "Timeout",
            "How long to wait for the server before giving up.", 30, 5, 300, "seconds");

    public static final Setting.Flag WARM_UP = new Setting.Flag("net.warmup", CONNECTION,
            "Connect at start-up to warm up the encryption code",
            "Makes the first paste take about half a second instead of two or three.", true);

    // ---- Pastes --------------------------------------------------------------

    public static final Setting.Flag CONFIRM_CLIPBOARD = new Setting.Flag("paste.confirmClipboard", PASTES,
            "Ask before sending the clipboard", "Shows the start of the text and asks first.", true);

    public static final Setting.Flag COPY_URL = new Setting.Flag("paste.copyUrl", PASTES,
            "Copy the new paste's address to the clipboard", "", true);

    public static final Setting.Flag SHOW_RESULT = new Setting.Flag("paste.showResult", PASTES,
            "Show the new paste's address in a window", "The address is also under Status... either way.", true);

    // ---- Account ---------------------------------------------------------------

    public static final Setting.Text USERNAME = new Setting.Text("account.username", ACCOUNT, "Remembered username",
            "Filled in when you log in. Leave empty to be asked each time. The password is never stored.", "",
            false, 64);

    // ---- Appearance ----------------------------------------------------------------

    public static final Setting.Choice LOOK = new Setting.Choice("ui.lookAndFeel", APPEARANCE, "Look and feel",
            "Classic Metal is the Swing look of Java 1.2 to 1.4. Windows matches the system's controls, but Java "
                    + "cannot draw Windows 98's MS Sans Serif, so text uses Microsoft Sans Serif or Tahoma if "
                    + "installed, otherwise Arial. Changes apply as soon as you press OK.",
            Appearance.METAL_STEEL,
            new String[] { Appearance.METAL_STEEL, Appearance.METAL_OCEAN, Appearance.WINDOWS },
            new String[] { "Java Metal, classic (Steel)", "Java Metal, Ocean (Java 5 default)", "Windows" });

    private static final List<Setting<?>> ALL;

    static {
        List<Setting<?>> l = new ArrayList<Setting<?>>();
        l.add(HOST);
        l.add(PORT);
        l.add(PINS);
        l.add(CIPHER);
        l.add(GROUP);
        l.add(TIMEOUT);
        l.add(WARM_UP);
        l.add(CONFIRM_CLIPBOARD);
        l.add(COPY_URL);
        l.add(SHOW_RESULT);
        l.add(USERNAME);
        l.add(LOOK);
        ALL = Collections.unmodifiableList(l);
    }

    private Settings() {
    }

    /** All settings, in dialog order. */
    public static List<Setting<?>> all() {
        return ALL;
    }

    /** Settings in one dialog tab, in order. */
    public static List<Setting<?>> inSection(String section) {
        List<Setting<?>> l = new ArrayList<Setting<?>>();
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).getSection().equals(section)) {
                l.add(ALL.get(i));
            }
        }
        return l;
    }

    /** Looks a setting up by key; null if unknown. */
    public static Setting<?> byKey(String key) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).getKey().equals(key)) {
                return ALL.get(i);
            }
        }
        return null;
    }
}
