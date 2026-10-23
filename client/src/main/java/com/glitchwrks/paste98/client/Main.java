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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.SecureRandom;
import java.util.prefs.Preferences;

import javax.swing.SwingUtilities;

import com.glitchwrks.paste98.api.PasteClient;
import com.glitchwrks.paste98.api.PasteException;
import com.glitchwrks.paste98.api.PasteResult;
import com.glitchwrks.paste98.api.PinnedTransport;
import com.glitchwrks.paste98.client.prefs.FilePreferencesFactory;
import com.glitchwrks.paste98.client.settings.Settings;
import com.glitchwrks.paste98.client.settings.SettingsStore;
import com.glitchwrks.paste98.client.ui.Appearance;
import com.glitchwrks.paste98.client.settings.Values;

/**
 * Entry point.
 *
 * <pre>
 *   javaw -jar GWPASTE.JAR              tray application
 *   javaw -jar GWPASTE.JAR --no-tray    same, in a small window instead of the tray
 *   java -jar GWPASTE.JAR --send FILE   send FILE as a paste and print its address
 *                                       (password from the PASTE98_PASSWORD variable)
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // First, before anything uses java.util.prefs: store settings in a
        // file in the program folder rather than in the Windows registry.
        FilePreferencesFactory.install();

        boolean tray = true;
        String sendFile = null;
        String user = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--no-tray")) {
                tray = false;
            } else if (a.equals("--send") && i + 1 < args.length) {
                sendFile = args[++i];
            } else if (a.equals("--user") && i + 1 < args.length) {
                user = args[++i];
            } else if (a.equals("--version")) {
                System.out.println(Version.NAME + " " + Version.VERSION);
                return;
            } else {
                System.err.println("usage: java -jar GWPASTE.JAR [--no-tray]");
                System.err.println("       java -jar GWPASTE.JAR --send FILE [--user NAME]");
                System.err.println("         (password from the PASTE98_PASSWORD environment variable)");
                System.exit(1);
            }
        }

        if (sendFile != null) {
            System.exit(sendFromCommandLine(sendFile, user));
            return;
        }
        runTray(tray);
    }

    private static void runTray(boolean wantTray) {
        Log.open(AppDir.file("PASTE98.LOG"));
        Runtime.getRuntime().addShutdownHook(new Thread("shutdown-log") {
            public void run() {
                Log.info("JVM exiting");
            }
        });
        Log.info(Version.NAME + " " + Version.VERSION + " starting; Java " + System.getProperty("java.version")
                + " (" + System.getProperty("java.vm.name") + ") on " + System.getProperty("os.name") + " "
                + System.getProperty("os.version") + "; folder " + AppDir.get());

        SettingsStore settings = openSettings();
        String imported = settings.importLegacy(AppDir.get());
        Appearance.apply(settings.current().get(Settings.LOOK));

        PasteService service = new PasteService(settings);
        service.warmUp(); // starts the slow first handshake immediately, in the background

        final Controller controller = new Controller(service);
        controller.setLegacyImport(imported);
        final String whyNoTray = controller.startTray(wantTray);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                controller.startUi(whyNoTray);
            }
        });
    }

    /** Headless paste for scripts and testing. Returns the exit status. */
    static int sendFromCommandLine(String path, String userOverride) {
        SettingsStore settings = openSettings();
        settings.importLegacy(AppDir.get());
        Values v = settings.current();
        if (!v.isConfigured()) {
            System.err.println("not set up: start " + Version.NAME + " and enter the server's key pin in Settings");
            return 1;
        }
        String user = userOverride != null ? userOverride : v.rememberedUsername();
        String pw = System.getenv("PASTE98_PASSWORD");
        if (user == null || pw == null) {
            System.err.println("need a username (--user, or remembered in Settings) and the PASTE98_PASSWORD "
                    + "environment variable");
            return 1;
        }
        String content;
        try {
            content = readFile(new File(path));
        } catch (IOException e) {
            System.err.println("cannot read " + path + ": " + e.getMessage());
            return 1;
        }
        long t0 = System.currentTimeMillis();
        try {
            PasteClient client = new PasteClient(new PinnedTransport(v.tlsOptions()), new SecureRandom(),
                    Version.userAgent());
            PasteResult r = client.send(content, user, pw.toCharArray());
            System.out.println(r.getUrl());
            System.err.println("sent " + content.length() + " characters in " + (System.currentTimeMillis() - t0)
                    + " ms");
            return 0;
        } catch (PasteException e) {
            System.err.println("paste failed (" + e.getKind() + "): " + e.getMessage());
            return 2;
        }
    }

    /** The settings, from java.util.prefs (normally the PASTE98.PRF file; see FilePreferencesFactory). */
    private static SettingsStore openSettings() {
        Preferences root = Preferences.userRoot();
        Log.info("settings backend: " + root.getClass().getName()
                + (FilePreferencesFactory.isActive() ? " (" + FilePreferencesFactory.userFile() + ")" : ""));
        return new SettingsStore(root);
    }

    /** Reads a text file in the platform's default encoding (cp1252 on an English Windows 98). */
    private static String readFile(File f) throws IOException {
        Reader r = new InputStreamReader(new FileInputStream(f));
        try {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } finally {
            r.close();
        }
    }
}
