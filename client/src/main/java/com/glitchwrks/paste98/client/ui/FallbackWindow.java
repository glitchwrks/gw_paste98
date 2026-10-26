/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.glitchwrks.paste98.client.Version;

/**
 * Used when the tray icon is not available: a small window with the same
 * actions as the tray menu. Minimise it to keep it in the taskbar; closing it
 * exits the program.
 */
public final class FallbackWindow {

    private final JFrame frame;
    private final JLabel status;

    /**
     * @param labels the same menu labels as the tray ("-" entries are skipped)
     * @param listener receives the index of the chosen label
     */
    public FallbackWindow(String[] labels, final ActionIndexListener listener, final Runnable onClose, Image icon,
            String whyNoTray) {
        frame = new JFrame(Version.NAME);
        if (icon != null) {
            frame.setIconImage(icon);
        }
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onClose.run();
            }
        });

        int count = 0;
        for (int i = 0; i < labels.length; i++) {
            if (!labels[i].equals("-")) {
                count++;
            }
        }
        JPanel buttons = new JPanel(new GridLayout(count, 1, 4, 4));
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals("-")) {
                continue;
            }
            final int index = i;
            JButton b = new JButton(labels[i]);
            b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    listener.action(index);
                }
            });
            buttons.add(b);
        }
        buttons.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        status = new JLabel("Starting");
        status.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
        JLabel note = new JLabel("<html><small>No tray icon: " + escape(whyNoTray) + "</small></html>");
        note.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));

        JPanel south = new JPanel(new BorderLayout());
        south.add(status, BorderLayout.NORTH);
        south.add(note, BorderLayout.SOUTH);

        frame.getContentPane().add(buttons, BorderLayout.CENTER);
        frame.getContentPane().add(south, BorderLayout.SOUTH);
        frame.pack();
        frame.setResizable(false);
        Dialogs.center(frame);
    }

    /** Receives button presses as label indexes. */
    public interface ActionIndexListener {
        void action(int index);
    }

    public void show() {
        frame.setVisible(true);
    }

    public JFrame getFrame() {
        return frame;
    }

    public void setStatus(String s) {
        status.setText(s);
    }

    private static String escape(String s) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
