package com.glitchwrks.paste98.client.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SettingsDialogTest {

    @Test
    public void wrapsAtSpacesWithinTheWidth() {
        String w = SettingsDialog.wrap("one two three four five six seven", 10);
        assertEquals("one two\nthree four\nfive six\nseven", w);
        String[] lines = SettingsDialog.wrap("a long-unbreakable-word stays whole", 8).split("\n");
        assertEquals("long-unbreakable-word", lines[1]);
        assertTrue(SettingsDialog.wrap("short", 64).indexOf('\n') < 0);
    }
}
