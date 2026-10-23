/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.StringTokenizer;

import com.glitchwrks.paste98.tls.TlsOptions;

/**
 * Settings from PASTE98.CFG (edited by hand; never written by the program),
 * plus the remembered username in PASTE98.USR (written by the program).
 * Passwords are never stored.
 */
public final class Config {

    public static final String SETTINGS_FILE = "PASTE98.CFG";
    public static final String USER_FILE = "PASTE98.USR";

    private final TlsOptions tls;
    private final File userFile;
    private String username;

    private Config(TlsOptions tls, File userFile, String username) {
        this.tls = tls;
        this.userFile = userFile;
        this.username = username;
    }

    /**
     * Loads settings from the given folder.
     *
     * @throws ConfigException with a message suitable for showing to the user
     */
    public static Config load(File dir) throws ConfigException {
        File settings = new File(dir, SETTINGS_FILE);
        Properties p = new Properties();
        try {
            InputStream in = new FileInputStream(settings);
            try {
                p.load(in);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new ConfigException("Cannot read " + settings + ": " + e.getMessage());
        }
        return fromProperties(p, new File(dir, USER_FILE));
    }

    static Config fromProperties(Properties p, File userFile) throws ConfigException {
        TlsOptions tls = new TlsOptions();
        tls.setHost(required(p, "host"));
        String port = p.getProperty("port", "443").trim();
        try {
            tls.setPort(Integer.parseInt(port));
        } catch (NumberFormatException e) {
            throw new ConfigException("port in " + SETTINGS_FILE + " is not a number: " + port);
        }
        if (tls.getPort() < 1 || tls.getPort() > 65535) {
            throw new ConfigException("port in " + SETTINGS_FILE + " is out of range: " + port);
        }

        StringTokenizer st = new StringTokenizer(required(p, "pins"), " ,\t");
        while (st.hasMoreTokens()) {
            String pin = st.nextToken();
            try {
                tls.addPin(pin);
            } catch (IllegalArgumentException e) {
                throw new ConfigException("A pin in " + SETTINGS_FILE + " is not valid: " + pin
                        + "\n\nEach pin is the base64 SHA-256 hash of the server's public key. See README.TXT.");
            }
        }
        if (tls.getPins().isEmpty()) {
            throw new ConfigException("No server key pin is set in " + SETTINGS_FILE + ". See README.TXT.");
        }

        String cipher = p.getProperty("cipher", TlsOptions.CIPHER_AUTO).trim();
        if (!(cipher.equals(TlsOptions.CIPHER_AUTO) || cipher.equals(TlsOptions.CIPHER_CHACHA)
                || cipher.equals(TlsOptions.CIPHER_AES128) || cipher.equals(TlsOptions.CIPHER_AES256))) {
            throw new ConfigException("cipher in " + SETTINGS_FILE + " must be auto, chacha, aes128 or aes256");
        }
        tls.setCipher(cipher);

        String username = p.getProperty("username");
        Properties user = new Properties();
        if (userFile != null && userFile.isFile()) {
            try {
                InputStream in = new FileInputStream(userFile);
                try {
                    user.load(in);
                } finally {
                    in.close();
                }
            } catch (IOException e) {
                Log.info("cannot read " + userFile + ": " + e);
            }
        }
        if (user.getProperty("username") != null) {
            username = user.getProperty("username");
        }
        return new Config(tls, userFile, username == null ? null : username.trim());
    }

    private static String required(Properties p, String key) throws ConfigException {
        String v = p.getProperty(key);
        if (v == null || v.trim().length() == 0) {
            throw new ConfigException(key + " is not set in " + SETTINGS_FILE + ".");
        }
        return v.trim();
    }

    public TlsOptions getTlsOptions() {
        return tls;
    }

    /** Remembered username, or null. */
    public synchronized String getUsername() {
        return username;
    }

    /**
     * Sets the username for this session, and either remembers it in
     * PASTE98.USR or removes any remembered username.
     */
    public synchronized void setUsername(String name, boolean remember) {
        username = name;
        if (userFile == null) {
            return;
        }
        if (!remember) {
            if (userFile.exists() && !userFile.delete()) {
                Log.info("cannot delete " + userFile);
            }
            return;
        }
        Properties p = new Properties();
        p.setProperty("username", name);
        try {
            OutputStream out = new FileOutputStream(userFile);
            try {
                p.store(out, "gw_paste98 remembered username (the password is never stored)");
            } finally {
                out.close();
            }
        } catch (IOException e) {
            Log.info("cannot write " + userFile + ": " + e);
        }
    }

    /** True if a username is remembered on disk. */
    public boolean isUsernameRemembered() {
        return userFile != null && userFile.isFile();
    }

    /** A settings problem, with a message for the user. */
    public static final class ConfigException extends Exception {
        private static final long serialVersionUID = 1L;

        public ConfigException(String message) {
            super(message);
        }
    }
}
