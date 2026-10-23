package com.glitchwrks.paste98.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClientPiecesTest {

    @Test
    public void previewShortensLongText() {
        String p = Controller.preview("one\r\ntwo\nthree\nfour\nfive\nsix\nseven\n");
        assertTrue(p, p.startsWith("  one\n  two\n"));
        assertTrue(p, p.endsWith("  ..."));
        StringBuilder longLine = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longLine.append('x');
        }
        assertTrue(Controller.preview(longLine.toString()).indexOf("...") > 0);
    }

    @Test
    public void trayLabelsAreAscii() {
        assertEquals("caf? ?", com.glitchwrks.paste98.client.tray.TrayTestAccess.ascii("caf\u00e9 \u2603", 63));
        assertEquals("abc", com.glitchwrks.paste98.client.tray.TrayTestAccess.ascii("abcdef", 3));
    }
}
