/*
 * Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import com.glitchwrks.paste98.client.Version;
import com.glitchwrks.paste98.tls.ServerKeyInfo;

/**
 * Shows a key fetched from a server and makes the person confirm they have
 * checked its pin against a trusted source before it can be added.
 */
final class PinVerifyDialog {

    private static final Color WARNING = new Color(0x99, 0x00, 0x00);

    private final JDialog dialog;
    private boolean add;

    private PinVerifyDialog(Dialog owner, ServerKeyInfo info, List currentPins) {
        boolean known = currentPins.contains(info.getPin());
        boolean changed = !known && !currentPins.isEmpty();

        dialog = new JDialog(owner, Version.NAME + " - check the server's key", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel body = new JPanel(new GridBagLayout());
        body.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(1, 2, 1, 6);
        int row = 0;

        JLabel heading = new JLabel(known ? "This pin is already in your list" : "Check this key pin before you use it");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() + 1f));
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        c.insets = new Insets(0, 2, 8, 6);
        body.add(heading, c);
        c.gridwidth = 1;
        c.insets = new Insets(1, 2, 1, 6);

        row = detail(body, c, row, "Server:", new JLabel(info.getHost() + ":" + info.getPort()));
        row = detail(body, c, row, "Key:", new JLabel(info.getKeyDescription()));
        JTextField pin = new JTextField(info.getPin(), 46);
        pin.setEditable(false);
        pin.setFont(new Font("Monospaced", Font.BOLD, 12));
        pin.setMinimumSize(pin.getPreferredSize());
        row = detail(body, c, row, "Pin:", pin);

        String names = join(info.getNames());
        JLabel namesLabel = new JLabel(names.length() == 0 ? "(no names)" : names);
        row = detail(body, c, row, "Certificate for:", namesLabel);
        if (!info.namesHost()) {
            row = detail(body, c, row, "", warning("The certificate does not name " + info.getHost() + "."));
        }
        row = detail(body, c, row, "Issued by:", new JLabel(shorten(info.getIssuer(), 70)));
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd");
        row = detail(body, c, row, "Valid:", new JLabel(day.format(info.getNotBefore()) + " to "
                + day.format(info.getNotAfter())));
        Date now = new Date();
        if (now.after(info.getNotAfter()) || now.before(info.getNotBefore())) {
            row = detail(body, c, row, "", warning("The certificate is not valid today (or this PC's clock is wrong). "
                    + "The pin can still be right: certificates are renewed, keys may stay the same."));
        }

        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10, 2, 2, 6);
        if (known) {
            body.add(SettingsDialog.helpText("Nothing to do: you already trust this key."), c);
        } else {
            if (changed) {
                body.add(warning("This does not match any of your current pins. If the server's key was not "
                        + "deliberately changed, do not add it: the connection may be intercepted."), c);
                c.gridy = row++;
            }
            body.add(text("This pin has NOT been verified.", "It is simply what the computer that answered at "
                    + info.getHost() + ":" + info.getPort() + " sent. If something between you and the server "
                    + "is intercepting the connection, this is the interceptor's key, and adding it would let "
                    + "them read your password and pastes."), c);
            c.gridy = row++;
            body.add(text(null, "Before adding it, compare it character by character with a pin from a source you "
                    + "trust: the server's administrator, or the openssl command in Help (The key pin) run on the "
                    + "server itself or on a trusted machine and network."), c);
        }

        final JCheckBox verified = new JCheckBox("I have checked this pin against a trusted source and it matches");
        final JButton addButton = new JButton("Add pin");
        addButton.setEnabled(false);
        verified.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                addButton.setEnabled(verified.isSelected());
            }
        });
        if (!known) {
            c.gridy = row++;
            c.insets = new Insets(8, 0, 2, 6);
            body.add(verified, c);
        }

        addButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                add = true;
                dialog.dispose();
            }
        });
        JButton cancel = new JButton(known ? "Close" : "Cancel");
        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        dialog.getRootPane().registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        JButton help = new JButton("Help");
        help.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Help.show(dialog, Help.KEY_PIN);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        if (!known) {
            buttons.add(addButton);
        }
        buttons.add(cancel);
        buttons.add(help);

        dialog.getContentPane().add(body, BorderLayout.CENTER);
        dialog.getContentPane().add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        Dialogs.centerOn(dialog, owner);
    }

    /**
     * Shows the key and returns true only if the person confirmed they checked
     * the pin and chose to add it.
     */
    static boolean confirmAdd(Dialog owner, ServerKeyInfo info, List currentPins) {
        PinVerifyDialog d = new PinVerifyDialog(owner, info, currentPins);
        d.dialog.setVisible(true); // modal
        return d.add;
    }

    private static int detail(JPanel p, GridBagConstraints c, int row, String label, JComponent value) {
        c.gridy = row;
        c.gridx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        p.add(new JLabel(label), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        p.add(value, c);
        return row + 1;
    }

    /** Wrapped text, optionally starting with a bold sentence. */
    private static JComponent text(String boldLead, String s) {
        String all = boldLead == null ? s : boldLead + " " + s;
        String wrapped = html(SettingsDialog.wrap(all, 80));
        if (boldLead != null) {
            String lead = html(boldLead);
            if (wrapped.startsWith(lead)) {
                wrapped = "<b>" + lead + "</b>" + wrapped.substring(lead.length());
            }
        }
        return new JLabel("<html>" + wrapped + "</html>");
    }

    private static JComponent warning(String s) {
        JLabel l = new JLabel("<html><b>" + html(SettingsDialog.wrap(s, 72)) + "</b></html>");
        l.setForeground(WARNING);
        return l;
    }

    private static String html(String s) {
        return SettingsDialog.replace(SettingsDialog.replace(SettingsDialog.replace(SettingsDialog.replace(s, "&",
                "&amp;"), "<", "&lt;"), ">", "&gt;"), "\n", "<br>");
    }

    private static String join(List l) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(l.get(i));
        }
        return shorten(sb.toString(), 70);
    }

    private static String shorten(String s, int max) {
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}
