/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.glitchwrks.paste98.api.PasteException;
import com.glitchwrks.paste98.client.Version;

/** The small dialogs. All methods must be called on the Swing event thread. */
public final class Dialogs {

    private static final String TITLE = Version.NAME;

    private Dialogs() {
    }

    /** Username and password as entered. */
    public static final class Credentials {
        public final String username;
        public final char[] password;
        public final boolean rememberUsername;

        Credentials(String username, char[] password, boolean rememberUsername) {
            this.username = username;
            this.password = password;
            this.rememberUsername = rememberUsername;
        }
    }

    /** Asks for the API username and password; null if cancelled. */
    public static Credentials askCredentials(Component parent, String host, String defaultUser, String reason) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        p.add(new JLabel(reason != null ? reason : "Log in to " + host + " to send pastes."), c);

        c.gridwidth = 1;
        c.gridy = 1;
        p.add(new JLabel("Username:"), c);
        final JTextField user = new JTextField(defaultUser == null ? "" : defaultUser, 18);
        c.gridx = 1;
        p.add(user, c);

        c.gridx = 0;
        c.gridy = 2;
        p.add(new JLabel("Password:"), c);
        final JPasswordField pass = new JPasswordField(18);
        c.gridx = 1;
        p.add(pass, c);

        c.gridx = 1;
        c.gridy = 3;
        JCheckBox remember = new JCheckBox("Remember username", defaultUser != null);
        p.add(remember, c);

        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        p.add(new JLabel("The password is kept in memory only until you exit."), c);

        // Focus the empty field once the dialog is showing.
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (user.getText().length() > 0) {
                    pass.requestFocusInWindow();
                } else {
                    user.requestFocusInWindow();
                }
            }
        });

        while (true) {
            int r = JOptionPane.showConfirmDialog(parent, p, TITLE + " - log in", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) {
                clear(pass);
                return null;
            }
            String u = user.getText().trim();
            char[] pw = pass.getPassword();
            if (u.length() > 0 && pw.length > 0 && u.indexOf(':') < 0) {
                clear(pass);
                return new Credentials(u, pw, remember.isSelected());
            }
            java.util.Arrays.fill(pw, '\0');
            JOptionPane.showMessageDialog(parent, u.indexOf(':') >= 0 ? "The username cannot contain ':'."
                    : "Enter both a username and a password.", TITLE, JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void clear(JPasswordField f) {
        f.setText("");
    }

    /** Shows the new paste's URL, selectable, and says it is on the clipboard. */
    public static void showResult(Component parent, String url, boolean copied) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JLabel("Paste created:"), BorderLayout.NORTH);
        JTextField f = new JTextField(url, 36);
        f.setEditable(false);
        f.selectAll();
        p.add(f, BorderLayout.CENTER);
        p.add(new JLabel(copied ? "The address has been copied to the clipboard."
                : "Could not copy the address to the clipboard."), BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(parent, p, TITLE, JOptionPane.INFORMATION_MESSAGE);
    }

    /** Explains a failed paste. */
    public static void showError(Component parent, PasteException e) {
        String title;
        if (e.getKind() == PasteException.Kind.PIN) {
            title = "Server key mismatch";
        } else if (e.getKind() == PasteException.Kind.AUTH) {
            title = "Login failed";
        } else if (e.getKind() == PasteException.Kind.NETWORK) {
            title = "Connection failed";
        } else {
            title = "Paste not created";
        }
        String extra = "";
        if (e.getKind() == PasteException.Kind.PIN) {
            extra = "\n\nThis could mean the server's key changed, or that something is intercepting the "
                    + "connection. If the server was rebuilt with a new key, update the pins in PASTE98.CFG.";
        } else if (e.getKind() == PasteException.Kind.AUTH) {
            extra = "\n\nYou will be asked to log in again.";
        }
        message(parent, title, e.getMessage() + extra, JOptionPane.ERROR_MESSAGE);
    }

    public static boolean confirm(Component parent, String text) {
        return JOptionPane.showConfirmDialog(parent, wrap(text), TITLE, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    public static void info(Component parent, String title, String text) {
        message(parent, title, text, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void error(Component parent, String title, String text) {
        message(parent, title, text, JOptionPane.ERROR_MESSAGE);
    }

    private static void message(Component parent, String title, String text, int type) {
        JOptionPane.showMessageDialog(parent, wrap(text), TITLE + " - " + title, type);
    }

    /** Long messages as a wrapped, read-only text area rather than one wide line. */
    private static Object wrap(String text) {
        if (text.length() < 70 && text.indexOf('\n') < 0) {
            return text;
        }
        JTextArea a = new JTextArea(text, 0, 44);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(false);
        a.setOpaque(false);
        a.setFont(new JLabel().getFont());
        a.setSize(a.getPreferredSize());
        if (a.getLineCount() > 14) {
            JScrollPane sp = new JScrollPane(a);
            sp.setPreferredSize(new java.awt.Dimension(420, 260));
            return sp;
        }
        return a;
    }
}
