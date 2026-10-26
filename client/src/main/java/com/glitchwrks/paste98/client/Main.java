/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
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

import javax.swing.SwingUtilities;

import com.glitchwrks.paste98.api.PasteClient;
import com.glitchwrks.paste98.api.PasteException;
import com.glitchwrks.paste98.api.PasteResult;
import com.glitchwrks.paste98.api.PinnedTransport;
import com.glitchwrks.paste98.tls.Randomness;
import com.glitchwrks.paste98.client.prefs.SettingsFile;
import com.glitchwrks.paste98.client.tray.NativeTray;
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
                System.err.println("         (password from the PASTE98_PASSWORD environment variable, or on Java 1.3");
                System.err.println("          the paste98.password system property, which SENDFILE.BAT sets from it)");
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
        Log.routeLibraryLogging();
        setUpRandomness();

        SettingsStore settings = openSettings();
        String imported = settings.importLegacy(AppDir.get());
        Appearance.apply(settings.current().getText(Settings.LOOK));

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
        setUpRandomness();
        String pw = password();
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
            PasteClient client = new PasteClient(new PinnedTransport(v.tlsOptions()), Randomness.get(),
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

    /** The settings, from PASTE98.PRF in the program folder. */
    private static SettingsStore openSettings() {
        SettingsFile file = new SettingsFile(SettingsFile.defaultFile(), Settings.NODE);
        Log.info("settings file: " + file.getFile());
        return new SettingsStore(file);
    }

    /**
     * Seed sources for the program's SecureRandom: Windows' CryptGenRandom
     * through the tray DLL where available, the seed file PASTE98.RND, and
     * the rest (see Randomness). Java 1.3's own seeding takes 30 s and more.
     */
    static void setUpRandomness() {
        Randomness.setSeedFile(AppDir.file("PASTE98.RND"));
        Randomness.addSource(NativeTray.seedSource(AppDir.file("GWTRAY.DLL")));
        Runtime.getRuntime().addShutdownHook(new Thread("seed-save") {
            public void run() {
                Randomness.saveSeed();
            }
        });
    }

    /**
     * The password for --send: PASTE98_PASSWORD from the environment on Java 5+;
     * Java 1.3 cannot read the environment (System.getenv throws), so
     * SENDFILE.BAT passes it as the paste98.password system property.
     */
    static String password() {
        String p = System.getProperty("paste98.password");
        if (p != null && p.length() > 0) {
            return p;
        }
        try {
            return System.getenv("PASTE98_PASSWORD");
        } catch (Throwable t) { // java.lang.Error on Java 1.3 and 1.4
            return null;
        }
    }

    /** Reads a text file in the platform's default encoding (cp1252 on an English Windows 98). */
    private static String readFile(File f) throws IOException {
        Reader r = new InputStreamReader(new FileInputStream(f));
        try {
            StringBuffer sb = new StringBuffer();
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
