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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import com.glitchwrks.paste98.client.Log;
import com.glitchwrks.paste98.client.Version;
import com.glitchwrks.paste98.client.settings.InvalidSettingException;
import com.glitchwrks.paste98.client.settings.Setting;
import com.glitchwrks.paste98.client.settings.Settings;
import com.glitchwrks.paste98.client.settings.SettingsStore;
import com.glitchwrks.paste98.client.settings.Values;
import com.glitchwrks.paste98.client.settings.XmlImport;
import com.glitchwrks.paste98.tls.PinFetcher;
import com.glitchwrks.paste98.tls.ServerKeyInfo;

/**
 * The Settings dialog: one tab per {@link Settings} section, one control per
 * setting, built from the declarations so new settings appear automatically.
 * Nothing is saved until OK, and OK only succeeds when every value is valid.
 */
public final class SettingsDialog {

    /** One control and how to read and write it as text. */
    private interface Field {
        JComponent component();

        /** Component to focus when the value is invalid. */
        JComponent focusTarget();

        String text();

        void setText(String text);
    }

    private final SettingsStore store;
    private final JDialog dialog;
    private final JTabbedPane tabs = new JTabbedPane();
    /** Setting to its Field; fieldOrder keeps the settings in dialog order. */
    private final Map fields = new HashMap();
    private final List fieldOrder = new ArrayList();
    private Values result;

    private SettingsDialog(Component parent, SettingsStore store, String message) {
        this.store = store;
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (parent instanceof Window) {
            owner = (Window) parent;
        }
        String title = Version.NAME + " - Settings";
        if (owner instanceof Dialog) {
            dialog = new JDialog((Dialog) owner, title, true);
        } else {
            dialog = new JDialog((Frame) (owner instanceof Frame ? owner : null), title, true);
        }
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        for (int i = 0; i < Settings.SECTIONS.length; i++) {
            tabs.addTab(Settings.SECTIONS[i], buildSection(Settings.inSection(Settings.SECTIONS[i])));
        }
        load(store.current());

        JPanel top = new JPanel(new BorderLayout());
        if (message != null) {
            JLabel m = new JLabel(message);
            m.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
            top.add(m, BorderLayout.NORTH);
        }
        tabs.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));
        top.add(tabs, BorderLayout.CENTER);

        JButton importButton = new JButton("Import...");
        importButton.setMnemonic(KeyEvent.VK_I);
        importButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                importFile();
            }
        });
        JButton exportButton = new JButton("Export...");
        exportButton.setMnemonic(KeyEvent.VK_E);
        exportButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exportFile();
            }
        });
        JButton help = new JButton("Help");
        help.setMnemonic(KeyEvent.VK_H);
        help.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Help.show(dialog, Help.settingsTopic(Settings.SECTIONS[tabs.getSelectedIndex()]));
            }
        });
        JButton ok = new JButton("OK");
        ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ok();
            }
        });
        JButton cancel = new JButton("Cancel");
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

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        left.add(importButton);
        left.add(exportButton);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        right.add(ok);
        right.add(cancel);
        right.add(help);
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);

        dialog.getContentPane().add(top, BorderLayout.CENTER);
        dialog.getContentPane().add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
    }

    /**
     * Shows the dialog and saves the settings if the user presses OK.
     *
     * @param message optional line shown above the tabs (e.g. on first run)
     * @param section tab to open on, or null for the first
     * @return true if settings were saved
     */
    public static boolean show(Component parent, SettingsStore store, String message, String section) {
        SettingsDialog d = new SettingsDialog(parent, store, message);
        if (section != null) {
            for (int i = 0; i < Settings.SECTIONS.length; i++) {
                if (Settings.SECTIONS[i].equals(section)) {
                    d.tabs.setSelectedIndex(i);
                }
            }
        }
        d.dialog.setVisible(true); // modal
        return d.result != null;
    }

    // ---- building ----------------------------------------------------------------

    private JPanel buildSection(List settings) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 0, 4);
        int row = 0;
        for (int i = 0; i < settings.size(); i++) {
            Setting s = (Setting) settings.get(i);
            Field f = fieldFor(s);
            fields.put(s, f);
            fieldOrder.add(s);

            c.gridy = row;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            if (s instanceof Setting.Flag) {
                c.gridx = 0;
                c.gridwidth = 2;
                c.anchor = GridBagConstraints.WEST;
                p.add(f.component(), c);
            } else {
                c.gridx = 0;
                c.gridwidth = 1;
                c.anchor = s instanceof Setting.Pins ? GridBagConstraints.NORTHEAST : GridBagConstraints.EAST;
                p.add(new JLabel(s.getLabel() + ":"), c);
                c.gridx = 1;
                c.anchor = GridBagConstraints.WEST;
                c.weightx = 1;
                c.fill = s instanceof Setting.Pins ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
                p.add(f.component(), c);
            }
            row++;

            if (s.getHelp().length() > 0) {
                c.gridy = row;
                c.gridx = 1;
                c.gridwidth = 1;
                c.weightx = 1;
                c.fill = GridBagConstraints.HORIZONTAL;
                c.anchor = GridBagConstraints.WEST;
                c.insets = new Insets(0, s instanceof Setting.Flag ? 24 : 4, 8, 4);
                if (s instanceof Setting.Flag) {
                    c.gridx = 0;
                    c.gridwidth = 2;
                }
                p.add(helpText(s.getHelp()), c);
                c.insets = new Insets(2, 4, 0, 4);
                row++;
            } else {
                c.gridy = row++;
                p.add(javax.swing.Box.createVerticalStrut(6), c);
            }
        }
        // Push everything to the top.
        c.gridy = row;
        c.gridx = 0;
        c.gridwidth = 2;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        p.add(new JPanel(), c);
        return p;
    }

    /**
     * Help text as a label, wrapped by hand. A line-wrapping JTextArea would
     * wrap itself, but its preferred height depends on its width, which makes
     * GridBagLayout fall back to minimum sizes and collapse the input fields.
     */
    static JComponent helpText(String text) {
        JLabel l = new JLabel("<html>" + replace(escapeHtml(wrap(text, 64)), "\n", "<br>") + "</html>");
        Font f = l.getFont();
        l.setFont(f.deriveFont(Font.PLAIN, Math.max(9f, f.getSize2D() - 1f)));
        l.setForeground(Color.darkGray);
        return l;
    }

    /** Breaks text into lines of at most width characters, at spaces. */
    static String wrap(String text, int width) {
        StringBuffer out = new StringBuffer();
        int lineLength = 0;
        java.util.StringTokenizer words = new java.util.StringTokenizer(text, " ");
        while (words.hasMoreTokens()) {
            String w = words.nextToken();
            if (lineLength > 0 && lineLength + 1 + w.length() > width) {
                out.append('\n');
                lineLength = 0;
            } else if (lineLength > 0) {
                out.append(' ');
                lineLength++;
            }
            out.append(w);
            lineLength += w.length();
        }
        return out.toString();
    }

    private static String escapeHtml(String s) {
        return replace(replace(replace(s, "&", "&amp;"), "<", "&lt;"), ">", "&gt;");
    }

    /** Replaces every occurrence of from (String.replace(CharSequence...) is Java 5). */
    static String replace(String s, String from, String to) {
        StringBuffer sb = new StringBuffer();
        int start = 0;
        int i;
        while ((i = s.indexOf(from, start)) >= 0) {
            sb.append(s.substring(start, i)).append(to);
            start = i + from.length();
        }
        return sb.append(s.substring(start)).toString();
    }

    private Field field(Setting s) {
        return (Field) fields.get(s);
    }

    /** Never let GridBagLayout shrink an input below its natural size. */
    private static void fixSize(JComponent c) {
        c.setMinimumSize(c.getPreferredSize());
    }

    private Field fieldFor(Setting s) {
        if (s instanceof Setting.Flag) {
            final JCheckBox box = new JCheckBox(s.getLabel());
            return new Field() {
                public JComponent component() {
                    return box;
                }

                public JComponent focusTarget() {
                    return box;
                }

                public String text() {
                    return box.isSelected() ? "true" : "false";
                }

                public void setText(String text) {
                    box.setSelected("true".equals(text));
                }
            };
        }
        if (s instanceof Setting.Choice) {
            final String[] values = ((Setting.Choice) s).getValues();
            final JComboBox combo = new JComboBox(((Setting.Choice) s).getNames());
            fixSize(combo);
            return new Field() {
                public JComponent component() {
                    return combo;
                }

                public JComponent focusTarget() {
                    return combo;
                }

                public String text() {
                    int i = combo.getSelectedIndex();
                    return i < 0 ? "" : values[i];
                }

                public void setText(String text) {
                    for (int i = 0; i < values.length; i++) {
                        if (values[i].equals(text)) {
                            combo.setSelectedIndex(i);
                        }
                    }
                }
            };
        }
        if (s instanceof Setting.Pins) {
            final JTextArea area = new JTextArea(4, 46);
            area.setFont(new Font("Monospaced", Font.PLAIN, 12));
            final JScrollPane scroll = new JScrollPane(area);
            fixSize(scroll);
            final JButton fetch = new JButton("Get pin from server...");
            fetch.setMnemonic(KeyEvent.VK_G);
            fetch.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    fetchPin(fetch, area);
                }
            });
            final JPanel panel = new JPanel(new BorderLayout(0, 4));
            panel.add(scroll, BorderLayout.CENTER);
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            row.add(fetch);
            panel.add(row, BorderLayout.SOUTH);
            return new Field() {
                public JComponent component() {
                    return panel;
                }

                public JComponent focusTarget() {
                    return area;
                }

                public String text() {
                    return area.getText();
                }

                public void setText(String text) {
                    // Stored space-separated; shown one per line.
                    area.setText(text.trim().replace(' ', '\n'));
                    area.setCaretPosition(0);
                }
            };
        }
        int columns = s instanceof Setting.Int ? 6 : 24;
        final JTextField field = new JTextField(columns);
        fixSize(field);
        final JComponent component;
        if (s instanceof Setting.Int && ((Setting.Int) s).getUnit().length() > 0) {
            JPanel withUnit = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            withUnit.add(field);
            withUnit.add(new JLabel(" " + ((Setting.Int) s).getUnit()));
            component = withUnit;
        } else {
            component = field;
        }
        return new Field() {
            public JComponent component() {
                return component;
            }

            public JComponent focusTarget() {
                return field;
            }

            public String text() {
                return field.getText();
            }

            public void setText(String text) {
                field.setText(text);
            }
        };
    }

    // ---- values ----------------------------------------------------------------

    private void load(Values v) {
        for (int i = 0; i < fieldOrder.size(); i++) {
            Setting s = (Setting) fieldOrder.get(i);
            field(s).setText(formatted(s, v));
        }
    }

    private static String formatted(Setting s, Values v) {
        return s.format(v.get(s));
    }

    /** Parses every field; returns the values, or null after reporting problems. */
    private Values collect() {
        Values v = store.current();
        List problems = new ArrayList();
        Setting first = null;
        for (int i = 0; i < fieldOrder.size(); i++) {
            Setting s = (Setting) fieldOrder.get(i);
            try {
                v = withText(v, s, field(s).text());
            } catch (InvalidSettingException ex) {
                problems.add(ex.getMessage());
                if (first == null) {
                    first = s;
                }
            }
        }
        if (problems.isEmpty()) {
            return v;
        }
        selectSetting(first);
        StringBuffer sb = new StringBuffer("Please correct:\n");
        for (int i = 0; i < problems.size(); i++) {
            sb.append("\n- ").append(problems.get(i));
        }
        Dialogs.error(dialog, "Settings", sb.toString());
        return null;
    }

    private static Values withText(Values v, Setting s, String text) throws InvalidSettingException {
        return v.with(s, s.parse(text));
    }

    private void selectSetting(Setting s) {
        for (int i = 0; i < Settings.SECTIONS.length; i++) {
            if (Settings.SECTIONS[i].equals(s.getSection())) {
                tabs.setSelectedIndex(i);
            }
        }
        field(s).focusTarget().requestFocus();
    }

    private void ok() {
        Values v = collect();
        if (v == null) {
            return;
        }
        if (!v.isConfigured() && !Dialogs.confirm(dialog, "No key pin is set, so the program will not connect "
                + "to the server until one is. Save anyway?")) {
            selectSetting(Settings.PINS);
            return;
        }
        try {
            store.save(v);
        } catch (IOException e) {
            Log.info("cannot save settings: " + e.getMessage());
            Dialogs.error(dialog, "Settings", "The settings could not be saved: " + e.getMessage());
            return;
        }
        Log.info("settings saved");
        result = v;
        dialog.dispose();
    }

    private boolean hasUnsavedChanges() {
        Values saved = store.current();
        for (int i = 0; i < fieldOrder.size(); i++) {
            Setting s = (Setting) fieldOrder.get(i);
            if (!formatted(s, saved).equals(normalised(s, field(s).text()))) {
                return true;
            }
        }
        return false;
    }

    /** Field text in stored form, or the raw text if it does not parse. */
    private static String normalised(Setting s, String text) {
        try {
            return s.format(s.parse(text));
        } catch (InvalidSettingException e) {
            return text;
        }
    }

    // ---- fetching a pin from the server -----------------------------------------------

    /**
     * Connects to the host and port currently in the dialog (not yet saved),
     * reads the server's certificate in the background, then asks the person
     * to check the pin before adding it to the pins field.
     */
    private void fetchPin(final JButton button, final JTextArea pinsArea) {
        final Values target;
        try {
            target = withText(withText(store.current(), Settings.HOST, field(Settings.HOST).text()),
                    Settings.PORT, field(Settings.PORT).text());
        } catch (InvalidSettingException e) {
            selectSetting(e.getSetting());
            Dialogs.error(dialog, "Get pin", e.getMessage());
            return;
        }
        final String label = button.getText();
        button.setEnabled(false);
        button.setText("Connecting to " + target.get(Settings.HOST) + "...");
        dialog.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        Thread t = new Thread("pin-fetch") {
            public void run() {
                ServerKeyInfo info = null;
                String error = null;
                try {
                    info = PinFetcher.fetch(target.tlsOptions());
                    Log.info("fetched key from " + info.getHost() + ":" + info.getPort() + ": " + info.getKeyDescription()
                            + ", pin " + info.getPin() + " (not verified)");
                } catch (IOException e) {
                    error = e.getMessage() == null ? e.toString() : e.getMessage();
                    Log.info("pin fetch from " + target.get(Settings.HOST) + " failed: " + e);
                } catch (RuntimeException e) {
                    error = e.toString();
                    Log.error("pin fetch failed", e);
                }
                final ServerKeyInfo result = info;
                final String why = error;
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        button.setText(label);
                        button.setEnabled(true);
                        dialog.setCursor(null);
                        if (result == null) {
                            Dialogs.error(dialog, "Get pin", "Could not get the key from " + target.get(Settings.HOST)
                                    + ":" + target.getInt(Settings.PORT) + ": " + why);
                            return;
                        }
                        offerPin(result, pinsArea);
                    }
                });
            }
        };
        t.setDaemon(true);
        t.start();
    }

    private void offerPin(ServerKeyInfo info, JTextArea pinsArea) {
        List current;
        try {
            current = (List) Settings.PINS.parse(pinsArea.getText());
        } catch (InvalidSettingException e) {
            current = new ArrayList(); // the field has a typo; compare with nothing
        }
        if (!PinVerifyDialog.confirmAdd(dialog, info, current)) {
            return;
        }
        String text = pinsArea.getText();
        if (text.length() > 0 && !text.endsWith("\n")) {
            text += "\n";
        }
        pinsArea.setText(text + info.getPin());
        Log.info("pin " + info.getPin() + " added after the user confirmed checking it (not yet saved)");
        selectSetting(Settings.PINS);
    }

    // ---- import / export --------------------------------------------------------

    private void importFile() {
        JFileChooser fc = chooser();
        if (fc.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        XmlImport.Result r;
        try {
            InputStream in = new FileInputStream(f);
            try {
                r = XmlImport.read(in, store.current());
            } finally {
                in.close();
            }
        } catch (IOException e) {
            Dialogs.error(dialog, "Import", "Cannot import " + f.getName() + ": " + e.getMessage());
            return;
        }
        load(r.values);
        StringBuffer sb = new StringBuffer();
        sb.append("Read ").append(r.count).append(" settings from ").append(f.getName())
                .append(". Review them, then press OK to save or Cancel to discard.");
        if (!r.problems.isEmpty()) {
            sb.append("\n\nThese were not valid and were left unchanged:");
            for (int i = 0; i < r.problems.size(); i++) {
                sb.append("\n- ").append(r.problems.get(i));
            }
        }
        Dialogs.info(dialog, "Import", sb.toString());
    }

    private void exportFile() {
        if (hasUnsavedChanges()) {
            Dialogs.info(dialog, "Export", "Export writes the saved settings. Press OK to save your changes "
                    + "first, then open Settings again to export.");
            return;
        }
        JFileChooser fc = chooser();
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), "PASTE98.XML"));
        if (fc.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (f.getName().indexOf('.') < 0) {
            f = new File(f.getParentFile(), f.getName() + ".XML");
        }
        if (f.exists() && !Dialogs.confirm(dialog, f.getName() + " already exists. Replace it?")) {
            return;
        }
        try {
            OutputStream out = new FileOutputStream(f);
            try {
                store.exportXml(out);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            Dialogs.error(dialog, "Export", "Cannot write " + f.getName() + ": " + e.getMessage());
            return;
        }
        Log.info("settings exported to " + f);
        Dialogs.info(dialog, "Export", "Settings saved to " + f.getPath() + ".\n\nThe file contains the "
                + "server, key pins and remembered username, but never the password.");
    }

    private static JFileChooser chooser() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".xml");
            }

            public String getDescription() {
                return "Settings files (*.xml)";
            }
        });
        return fc;
    }

    /** Shows a problem with settings that could not be loaded. */
    public static void showLoadProblems(Component parent, List problems) {
        if (problems.isEmpty()) {
            return;
        }
        StringBuffer sb = new StringBuffer("Some saved settings were not valid and the defaults are being used:\n");
        for (int i = 0; i < problems.size(); i++) {
            sb.append("\n- ").append(problems.get(i));
        }
        JOptionPane.showMessageDialog(parent, sb.toString(), Version.NAME + " - Settings",
                JOptionPane.WARNING_MESSAGE);
    }
}
