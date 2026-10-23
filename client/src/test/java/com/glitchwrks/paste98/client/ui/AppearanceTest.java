package com.glitchwrks.paste98.client.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Font;

import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;

import org.junit.Test;

public class AppearanceTest {

    @Test
    public void choosesTheFirstInstalledPreference() {
        String[] installed = { "Arial", "tahoma", "Courier New" };
        assertEquals("tahoma", Appearance.chooseFamily(installed, Appearance.WINDOWS_FONTS));
        assertEquals("Microsoft Sans Serif",
                Appearance.chooseFamily(new String[] { "Tahoma", "Microsoft Sans Serif" }, Appearance.WINDOWS_FONTS));
        assertNull(Appearance.chooseFamily(new String[] { "Arial" }, Appearance.WINDOWS_FONTS));
    }

    @Test
    public void metalThemesApplyAndWindowsFallsBackOffWindows() throws Exception {
        assertTrue(Appearance.apply(Appearance.METAL_OCEAN).indexOf("Ocean") >= 0);
        assertTrue(UIManager.getLookAndFeel() instanceof MetalLookAndFeel);
        assertEquals("Ocean", MetalLookAndFeel.getCurrentTheme().getName());

        assertTrue(Appearance.apply(Appearance.METAL_STEEL).indexOf("Steel") >= 0);
        assertEquals("Steel", MetalLookAndFeel.getCurrentTheme().getName());

        assertTrue(Appearance.apply("something-unknown").indexOf("Steel") >= 0);

        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            String d = Appearance.apply(Appearance.WINDOWS);
            assertTrue(d, d.indexOf("Steel") >= 0 && d.indexOf("needs Windows") >= 0);
        }
    }

    @Test
    public void fontOverridesKeepSizeAndStyleAndAreRemovedOnSwitch() throws Exception {
        Appearance.apply(Appearance.METAL_STEEL);
        Font before = UIManager.getFont("Button.font"); // bold in Steel
        Appearance.overrideFonts("Monospaced");
        Font after = UIManager.getFont("Button.font");
        assertEquals("Monospaced", after.getName());
        assertEquals(before.getSize(), after.getSize());
        assertEquals(before.getStyle(), after.getStyle());

        Appearance.apply(Appearance.METAL_OCEAN); // clears the overrides
        assertTrue(!"Monospaced".equals(UIManager.getFont("Button.font").getName()));
        Appearance.apply(Appearance.METAL_STEEL);
    }
}
