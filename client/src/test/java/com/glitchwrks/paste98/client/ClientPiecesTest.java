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

    @Test
    public void programFolderUrlsDecode() throws Exception {
        assertEquals("C:/Program Files/gw_paste98/GWPASTE.JAR",
                AppDir.decodePercent("/C:/Program%20Files/gw_paste98/GWPASTE.JAR"));
        assertEquals("/home/a+b/caf\u00e9/x.jar", AppDir.decodePercent("/home/a+b/caf%C3%A9/x.jar"));
        assertEquals("/odd%zz", AppDir.decodePercent("/odd%zz"));
        // the result matches what java.net.URI (Java 1.4+) gives for the same URL
        java.net.URL url = new java.io.File("/tmp/with space/caf\u00e9.jar").toURI().toURL();
        assertEquals(new java.io.File(url.toURI()).getPath(), AppDir.decodePercent(url.getFile()));
    }
}
