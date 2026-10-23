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

import java.awt.Component;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.net.URL;

import com.glitchwrks.paste98.api.ContentCheck;
import com.glitchwrks.paste98.api.PasteException;
import com.glitchwrks.paste98.api.PasteResult;
import com.glitchwrks.paste98.client.prefs.FilePreferencesFactory;
import com.glitchwrks.paste98.client.settings.Settings;
import com.glitchwrks.paste98.client.settings.SettingsStore;
import com.glitchwrks.paste98.client.settings.Values;
import com.glitchwrks.paste98.client.tray.NativeTray;
import com.glitchwrks.paste98.client.ui.Appearance;
import com.glitchwrks.paste98.client.ui.ComposeWindow;
import com.glitchwrks.paste98.client.ui.Dialogs;
import com.glitchwrks.paste98.client.ui.FallbackWindow;
import com.glitchwrks.paste98.client.ui.SettingsDialog;

/**
 * Wires the tray (or fallback window), the compose window, the dialogs and
 * the paste service together. Everything here runs on the Swing event thread
 * except {@link #startTray()}.
 */
public final class Controller implements NativeTray.Listener, ComposeWindow.Actions, PasteService.StatusListener,
        SettingsStore.Listener {

    static final String[] MENU = { "New paste...", "Paste clipboard", "-", "Status...", "Settings...", "About...", "-",
        "Exit" };
    static final int ITEM_NEW = 0;
    static final int ITEM_CLIPBOARD = 1;
    static final int ITEM_STATUS = 3;
    static final int ITEM_SETTINGS = 4;
    static final int ITEM_ABOUT = 5;
    static final int ITEM_EXIT = 7;

    private final PasteService service;
    private final SettingsStore settings;
    private String legacyImport;
    private final Image icon;
    private ComposeWindow compose;
    private FallbackWindow fallback;
    private boolean trayActive;
    private boolean sending;

    public Controller(PasteService service) {
        this.service = service;
        this.settings = service.getSettings();
        URL png = Controller.class.getResource("gwtray.png");
        this.icon = png == null ? null : Toolkit.getDefaultToolkit().createImage(png);
    }

    /**
     * Tries to show the tray icon. Call from the main thread (it waits for the
     * icon to appear). Returns null on success or the reason it failed.
     */
    public String startTray(boolean wanted) {
        if (!wanted) {
            Log.info("no tray icon: disabled with --no-tray");
            return "disabled with --no-tray";
        }
        String why = NativeTray.start(AppDir.file("GWTRAY.DLL"), AppDir.file("GWTRAY.ICO"),
                tooltip(service.getShortStatus()), MENU, ITEM_NEW, this);
        trayActive = why == null;
        Log.info(trayActive ? "tray icon shown" : "no tray icon: " + why);
        return why;
    }

    /** Records that settings were imported from the old PASTE98.CFG, to mention on start-up. */
    public void setLegacyImport(String description) {
        legacyImport = description;
    }

    /**
     * Finishes start-up on the event thread: shows the fallback window if
     * there is no tray, then opens Settings if the program is not set up yet.
     */
    public void startUi(String whyNoTray) {
        service.setStatusListener(this);
        settings.addListener(this);
        if (!trayActive) {
            showFallback(whyNoTray);
        }
        statusChanged(service.getShortStatus(), service.getDetail());
        SettingsDialog.showLoadProblems(parent(), settings.getLoadProblems());
        if (legacyImport != null) {
            Dialogs.info(parent(), "Settings", "Your settings were copied from " + SettingsStore.LEGACY_SETTINGS
                    + " into " + FilePreferencesFactory.USER_FILE + ". " + SettingsStore.LEGACY_SETTINGS
                    + " has been renamed to " + SettingsStore.LEGACY_RENAMED
                    + " and is no longer used: change settings with Settings... in the menu.");
        }
        if (!settings.current().isConfigured()) {
            SettingsDialog.show(parent(), settings, "Welcome to " + Version.NAME
                    + ". Enter the server's key pin, or get it from the server and check it, to start.",
                    Settings.SERVER);
        }
    }

    private void showFallback(String whyNoTray) {
        if (fallback == null) {
            fallback = new FallbackWindow(MENU, new FallbackWindow.ActionIndexListener() {
                public void action(int index) {
                    trayAction(index);
                }
            }, new Runnable() {
                public void run() {
                    exit();
                }
            }, icon, whyNoTray);
        }
        fallback.setStatus(service.getShortStatus());
        fallback.show();
    }

    /** The tray icon went away on its own; carry on in the fallback window. */
    public void trayLost(String why) {
        Log.info("tray lost: " + why);
        trayActive = false;
        showFallback(why);
    }

    // ---- tray / fallback actions ---------------------------------------------

    public void trayAction(int item) {
        switch (item) {
        case -1: // double-click on the icon
        case ITEM_NEW:
            compose().show();
            break;
        case ITEM_CLIPBOARD:
            pasteClipboard();
            break;
        case ITEM_STATUS:
            showStatus();
            break;
        case ITEM_SETTINGS:
            SettingsDialog.show(parent(), settings, null, null);
            break;
        case ITEM_ABOUT:
            showAbout();
            break;
        case ITEM_EXIT:
            exit();
            break;
        default:
            break;
        }
    }

    private ComposeWindow compose() {
        if (compose == null) {
            compose = new ComposeWindow(this, icon);
        }
        return compose;
    }

    private Component parent() {
        if (compose != null && compose.getFrame().isVisible()) {
            return compose.getFrame();
        }
        if (fallback != null) {
            return fallback.getFrame();
        }
        return null;
    }

    private void pasteClipboard() {
        String text = clipboardText();
        if (text == null) {
            Dialogs.info(parent(), "Paste clipboard", "The clipboard does not contain any text.");
            return;
        }
        if (settings.current().is(Settings.CONFIRM_CLIPBOARD) && !Dialogs.confirm(parent(), "Send the clipboard ("
                + text.length() + " characters) as a new paste?\n\n" + preview(text))) {
            return;
        }
        sendText(text, null);
    }

    // ---- compose window actions ----------------------------------------------

    public void send(String text) {
        sendText(text, compose);
    }

    public void pasteClipboardInto(ComposeWindow window) {
        String text = clipboardText();
        if (text == null) {
            Dialogs.info(window.getFrame(), "Paste from clipboard", "The clipboard does not contain any text.");
            return;
        }
        window.insertText(text);
    }

    // ---- sending -------------------------------------------------------------

    private void sendText(String text, final ComposeWindow window) {
        Component parent = window != null ? window.getFrame() : parent();
        if (sending) {
            Dialogs.info(parent, "Busy", "A paste is already being sent.");
            return;
        }
        if (!settings.current().isConfigured()
                && !SettingsDialog.show(parent, settings, "Enter the server's key pin before sending.", Settings.SERVER)) {
            return;
        }
        if (!settings.current().isConfigured()) {
            return;
        }
        ContentCheck check = ContentCheck.of(text);
        if (check.isEmpty()) {
            Dialogs.info(parent, "Nothing to send", "The paste is empty.");
            return;
        }
        if (check.isTooLong()) {
            Dialogs.error(parent, "Too long", "The paste is " + check.getLength() + " characters; the server stores at most "
                    + ContentCheck.MAX_CHARS + ".");
            return;
        }
        if (check.getNonLatin1Count() > 0 && !Dialogs.confirm(parent, check.getNonLatin1Count()
                + " characters are outside the Latin-1 character set and will not be stored correctly by the "
                + "server. Send anyway?")) {
            return;
        }
        if (!ensureCredentials(parent, null)) {
            return;
        }
        sending = true;
        if (window != null) {
            window.setBusy(true, "Sending...");
        }
        service.send(text, new PasteService.Callback() {
            public void succeeded(PasteResult result) {
                sending = false;
                Values v = settings.current();
                String note;
                if (v.is(Settings.COPY_URL)) {
                    note = copyToClipboard(result.getUrl()) ? "The address has been copied to the clipboard."
                            : "Could not copy the address to the clipboard.";
                } else {
                    note = "";
                }
                if (window != null) {
                    window.setText("");
                    window.setBusy(false, null);
                    window.hide();
                }
                if (v.is(Settings.SHOW_RESULT)) {
                    Dialogs.showResult(parent(), result.getUrl(), note);
                }
            }

            public void failed(PasteException e) {
                sending = false;
                if (window != null) {
                    window.setBusy(false, null);
                }
                Dialogs.showError(window != null ? window.getFrame() : parent(), e);
            }
        });
    }

    private boolean ensureCredentials(Component parent, String reason) {
        if (service.hasCredentials()) {
            return true;
        }
        Values v = settings.current();
        String remembered = v.rememberedUsername();
        String last = service.getUsername();
        Dialogs.Credentials c = Dialogs.askCredentials(parent, v.get(Settings.HOST),
                last != null ? last : remembered, remembered != null, reason);
        if (c == null) {
            return false;
        }
        service.setCredentials(c.username, c.password);
        String want = c.rememberUsername ? c.username : null;
        if (want == null ? remembered != null : !want.equals(remembered)) {
            try {
                settings.rememberUsername(want);
            } catch (java.util.prefs.BackingStoreException e) {
                Log.info("cannot save the username: " + e.getMessage());
            }
        }
        return true;
    }

    // ---- other actions -------------------------------------------------------

    private void showStatus() {
        Values v = settings.current();
        StringBuilder sb = new StringBuilder();
        sb.append(service.getDetail()).append("\n\n");
        sb.append("Server: ").append(v.get(Settings.HOST)).append(':').append(v.getInt(Settings.PORT)).append('\n');
        sb.append("Pinned keys: ").append(v.get(Settings.PINS).size()).append('\n');
        sb.append("Logged in as: ").append(service.hasCredentials() ? service.getUsername() : "(not logged in)")
                .append('\n');
        if (service.getLastUrl() != null) {
            sb.append("Last paste: ").append(service.getLastUrl()).append('\n');
        }
        sb.append("Tray icon: ").append(trayActive ? "yes" : "no (see PASTE98.LOG)").append('\n');
        sb.append("Appearance: ").append(Appearance.describe()).append('\n');
        sb.append("Settings file: ").append(FilePreferencesFactory.userFile()).append('\n');
        sb.append("Folder: ").append(AppDir.get());
        Dialogs.info(parent(), "Status", sb.toString());
    }

    private void showAbout() {
        Dialogs.info(parent(), "About", Version.NAME + " " + Version.VERSION
                + "\nA Windows 98 client for the Glitch Works pastes service."
                + "\n\nEncryption: TLS 1.2 by Bouncy Castle, server authenticated by a pinned key."
                + "\nJava " + System.getProperty("java.version") + " on " + System.getProperty("os.name") + " "
                + System.getProperty("os.version")
                + "\n\nCopyright (C) 2026 Glitch Works, LLC. Free software under the GNU GPL v3 or later; "
                + "see LICENSE.TXT. There is NO WARRANTY.");
    }

    public void exit() {
        Log.info("exiting");
        service.shutdown();
        if (trayActive) {
            NativeTray.stop();
        }
        System.exit(0);
    }

    // ---- settings ------------------------------------------------------------

    public void settingsChanged(Values before, Values after) {
        service.settingsChanged(before, after);
        if (!before.get(Settings.LOOK).equals(after.get(Settings.LOOK))) {
            Appearance.apply(after.get(Settings.LOOK));
        }
    }

    // ---- status --------------------------------------------------------------

    public void statusChanged(String shortStatus, String detail) {
        if (trayActive) {
            NativeTray.setTooltip(tooltip(shortStatus));
        }
        if (fallback != null) {
            fallback.setStatus(shortStatus);
        }
    }

    private static String tooltip(String shortStatus) {
        return Version.NAME + " - " + shortStatus;
    }

    // ---- clipboard -----------------------------------------------------------

    static String clipboardText() {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable t = cb.getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String s = (String) t.getTransferData(DataFlavor.stringFlavor);
                return s.length() == 0 ? null : s;
            }
        } catch (Exception e) {
            Log.info("cannot read the clipboard: " + e);
        }
        return null;
    }

    static boolean copyToClipboard(String s) {
        try {
            StringSelection sel = new StringSelection(s);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            return true;
        } catch (Exception e) {
            Log.info("cannot write the clipboard: " + e);
            return false;
        }
    }

    /** First few lines of text, shortened, for confirmation dialogs. */
    static String preview(String text) {
        StringBuilder sb = new StringBuilder();
        int lines = 0;
        int start = 0;
        while (start < text.length() && lines < 6) {
            int nl = text.indexOf('\n', start);
            int end = nl < 0 ? text.length() : nl;
            String line = text.substring(start, end);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.length() > 60) {
                line = line.substring(0, 60) + "...";
            }
            sb.append("  ").append(line).append('\n');
            lines++;
            start = end + 1;
        }
        if (start < text.length()) {
            sb.append("  ...");
        }
        return sb.toString();
    }
}
