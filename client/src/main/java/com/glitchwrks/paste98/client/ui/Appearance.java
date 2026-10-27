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

import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.MetalTheme;

import com.glitchwrks.paste98.client.Log;

/**
 * Applies the Appearance setting: Java's Metal look and feel with the classic
 * Steel theme (Java 1.2 to 1.4) or the Ocean theme (Java 5's default), or the
 * Windows look and feel.
 *
 * Windows 98 draws its dialogs in MS Sans Serif, a bitmap font. Java can only
 * draw outline (TrueType or Type 1) fonts, so the Windows look and feel falls
 * back to Arial. When Microsoft Sans Serif (an outline version of MS Sans
 * Serif) or, failing that, Tahoma is installed, this class substitutes it for
 * every font the look and feel uses, keeping each one's size and style.
 */
public final class Appearance {

    public static final String METAL_STEEL = "metal-steel";
    public static final String METAL_OCEAN = "metal-ocean";
    public static final String WINDOWS = "windows";

    /** Preferred substitutes for MS Sans Serif, best first. */
    static final String[] WINDOWS_FONTS = { "Microsoft Sans Serif", "Tahoma" };

    private static final String WINDOWS_LAF = "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";

    /** UIManager keys this class overrode, so switching away can remove them. */
    private static final List overridden = new ArrayList();

    private static String description = "not set";

    private Appearance() {
    }

    /**
     * Sets the look and feel and refreshes any open windows. Call on the
     * Swing event thread once windows exist; before that, any thread.
     *
     * @return a description for the log and Status, e.g. "Windows, text in Tahoma"
     */
    public static synchronized String apply(String choice) {
        long t0 = System.currentTimeMillis();
        clearFontOverrides();
        String d;
        try {
            if (WINDOWS.equals(choice)) {
                d = applyWindows();
            } else if (METAL_OCEAN.equals(choice)) {
                MetalTheme ocean = ocean();
                if (ocean == null) {
                    d = applySteel() + "; Ocean needs Java 5";
                } else {
                    MetalLookAndFeel.setCurrentTheme(ocean);
                    UIManager.setLookAndFeel(new MetalLookAndFeel());
                    d = "Java Metal (Ocean)";
                }
            } else {
                d = applySteel();
            }
        } catch (Exception e) {
            Log.info("cannot set the look and feel: " + e);
            d = "Java default (" + e.getMessage() + ")";
        }
        refreshOpenWindows();
        description = d;
        Log.info("appearance: " + d + " (" + (System.currentTimeMillis() - t0) + " ms)");
        return d;
    }

    /** What {@link #apply} last set, for the Status window. */
    public static synchronized String describe() {
        return description;
    }

    /** Java 5's Ocean theme, or null on older Javas. Loaded by name: the program runs on Java 1.3. */
    static MetalTheme ocean() {
        try {
            return (MetalTheme) Class.forName("javax.swing.plaf.metal.OceanTheme").newInstance();
        } catch (Exception e) {
            return null;
        } catch (LinkageError e) {
            return null;
        }
    }

    private static String applySteel() throws Exception {
        MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
        UIManager.setLookAndFeel(new MetalLookAndFeel());
        return "Java Metal (classic Steel)";
    }

    private static String applyWindows() throws Exception {
        LookAndFeel laf;
        try {
            laf = (LookAndFeel) Class.forName(WINDOWS_LAF).newInstance();
        } catch (Exception e) {
            return applySteel() + "; the Windows look is not available here";
        }
        if (!laf.isSupportedLookAndFeel()) {
            return applySteel() + "; the Windows look needs Windows";
        }
        UIManager.setLookAndFeel(laf);
        String family = chooseFamily(availableFamilies(), WINDOWS_FONTS);
        if (family == null) {
            return "Windows, text in " + currentFamily()
                    + " (install Microsoft Sans Serif or Tahoma for a closer match)";
        }
        overrideFonts(family);
        return "Windows, text in " + family;
    }

    /** The first of the wanted families that is installed, or null. */
    static String chooseFamily(String[] available, String[] wanted) {
        for (int i = 0; i < wanted.length; i++) {
            for (int j = 0; j < available.length; j++) {
                if (available[j].equalsIgnoreCase(wanted[i])) {
                    return available[j];
                }
            }
        }
        return null;
    }

    private static String[] availableFamilies() {
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        } catch (RuntimeException e) {
            Log.info("cannot list fonts: " + e);
            return new String[0];
        }
    }

    private static String currentFamily() {
        Font f = UIManager.getFont("Label.font");
        return f == null ? "the default font" : f.getFamily();
    }

    /**
     * Replaces every font in the current look and feel with the same size and
     * style in family. Values are resolved through UIManager.getFont because
     * the Windows look and feel stores fonts as lazily-read desktop
     * properties, not Font objects.
     */
    static void overrideFonts(String family) {
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        List keys = new ArrayList();
        for (Enumeration e = defaults.keys(); e.hasMoreElements();) {
            keys.add(e.nextElement());
        }
        for (int i = 0; i < keys.size(); i++) {
            Object key = keys.get(i);
            if (!(key instanceof String) || !(((String) key).endsWith(".font") || ((String) key).endsWith("Font"))) {
                continue;
            }
            Font f = UIManager.getFont(key);
            if (f == null) {
                continue;
            }
            UIManager.put(key, new FontUIResource(family, f.getStyle(), f.getSize()));
            overridden.add(key);
        }
    }

    /** Removes font overrides so the next look and feel uses its own fonts. */
    static void clearFontOverrides() {
        for (int i = 0; i < overridden.size(); i++) {
            UIManager.put(overridden.get(i), null);
        }
        overridden.clear();
    }

    /**
     * Re-styles every open window. Frame.getFrames() includes the hidden
     * owner of ownerless dialogs, so owned windows are reached through it
     * (Window.getWindows() is Java 6).
     */
    private static void refreshOpenWindows() {
        Frame[] frames = Frame.getFrames();
        for (int i = 0; i < frames.length; i++) {
            refresh(frames[i]);
        }
    }

    private static void refresh(Window w) {
        SwingUtilities.updateComponentTreeUI(w);
        w.validate();
        Window[] owned = w.getOwnedWindows();
        for (int i = 0; i < owned.length; i++) {
            refresh(owned[i]);
        }
    }
}
