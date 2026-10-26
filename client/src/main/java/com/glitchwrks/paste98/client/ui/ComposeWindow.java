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

import java.awt.Frame;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.glitchwrks.paste98.api.ContentCheck;
import com.glitchwrks.paste98.client.Version;

/**
 * The "New paste" window: a plain-text editor with Send. Hidden rather than
 * disposed when closed, so the text survives until it is sent or cleared.
 */
public final class ComposeWindow {

    /** Actions the window asks the controller to perform. */
    public interface Actions {
        void send(String text);

        void pasteClipboardInto(ComposeWindow window);
    }

    private final JFrame frame;
    private final JTextArea text;
    private final JLabel status;
    private final JButton send;
    private final JButton fromClipboard;
    private final JButton clear;

    public ComposeWindow(final Actions actions, Image icon) {
        frame = new JFrame("New paste - " + Version.NAME);
        if (icon != null) {
            frame.setIconImage(icon);
        }
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        text = new JTextArea(20, 80);
        text.setFont(new Font("Monospaced", Font.PLAIN, 12));
        text.setTabSize(8);
        JScrollPane scroll = new JScrollPane(text);

        status = new JLabel(" ");
        status.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));

        fromClipboard = new JButton("Paste from clipboard");
        fromClipboard.setMnemonic(KeyEvent.VK_P);
        fromClipboard.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                actions.pasteClipboardInto(ComposeWindow.this);
            }
        });

        clear = new JButton("Clear");
        clear.setMnemonic(KeyEvent.VK_C);
        clear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                text.setText("");
                text.requestFocus();
            }
        });

        send = new JButton("Send");
        send.setMnemonic(KeyEvent.VK_S);
        send.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                actions.send(text.getText());
            }
        });

        // Ctrl+Enter sends.
        text.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_MASK), "gw-send");
        text.getActionMap().put("gw-send", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                if (send.isEnabled()) {
                    actions.send(text.getText());
                }
            }
        });

        text.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateCount();
            }

            public void removeUpdate(DocumentEvent e) {
                updateCount();
            }

            public void changedUpdate(DocumentEvent e) {
                updateCount();
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.add(fromClipboard);
        buttons.add(clear);
        buttons.add(send);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);

        frame.getContentPane().add(scroll, BorderLayout.CENTER);
        frame.getContentPane().add(bottom, BorderLayout.SOUTH);
        frame.getRootPane().setDefaultButton(null); // Enter types a newline
        frame.addWindowListener(new WindowAdapter() {
            public void windowActivated(WindowEvent e) {
                text.requestFocus();
            }
        });
        frame.pack();
        Dialogs.center(frame);
        updateCount();
    }

    public void show() {
        if (frame.getState() == Frame.ICONIFIED) {
            frame.setState(Frame.NORMAL);
        }
        frame.setVisible(true);
        frame.toFront();
        text.requestFocus();
    }

    public void hide() {
        frame.setVisible(false);
    }

    public JFrame getFrame() {
        return frame;
    }

    public void setText(String s) {
        text.setText(s);
        text.setCaretPosition(0);
    }

    /** Inserts at the caret, replacing any selection. */
    public void insertText(String s) {
        text.replaceSelection(s);
    }

    /** Disables editing while a paste is being sent. */
    public void setBusy(boolean busy, String message) {
        text.setEditable(!busy);
        send.setEnabled(!busy);
        fromClipboard.setEnabled(!busy);
        clear.setEnabled(!busy);
        if (message != null) {
            status.setText(message);
        } else {
            updateCount();
        }
    }

    private void updateCount() {
        ContentCheck c = ContentCheck.of(text.getText());
        String s = c.getLength() + " characters";
        if (c.isTooLong()) {
            s += " - too long (limit " + ContentCheck.MAX_CHARS + ")";
        } else if (c.getNonLatin1Count() > 0) {
            s += " - " + c.getNonLatin1Count() + " will not be stored correctly";
        }
        status.setText(s);
    }
}
