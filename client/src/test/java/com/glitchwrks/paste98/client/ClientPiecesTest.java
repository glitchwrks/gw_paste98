package com.glitchwrks.paste98.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Properties;

import org.junit.Test;

import com.glitchwrks.paste98.tls.TlsOptions;

public class ClientPiecesTest {

    private static final String PIN1 = "Jisk0gqPSNvZGT2YXdsQm/slV6XN/1gv6UWXWHwXyeM=";
    private static final String PIN2 = "MnwlM5ruy9bz2zSVXbMqbxe8F0JZzLfgyIpCpzEc3VQ=";

    private static Properties props(String host, String port, String pins) {
        Properties p = new Properties();
        if (host != null) {
            p.setProperty("host", host);
        }
        if (port != null) {
            p.setProperty("port", port);
        }
        if (pins != null) {
            p.setProperty("pins", pins);
        }
        return p;
    }

    @Test
    public void parsesSettingsWithTwoPins() throws Exception {
        Config c = Config.fromProperties(props("pastes.example", "8443", PIN1 + " sha256//" + PIN2), null);
        TlsOptions o = c.getTlsOptions();
        assertEquals("pastes.example", o.getHost());
        assertEquals(8443, o.getPort());
        assertEquals(2, o.getPins().size());
        assertEquals(PIN2, o.getPins().get(1));
        assertNull(c.getUsername());
    }

    @Test
    public void rejectsTemplatePlaceholderAndMissingValues() {
        expectProblem(props("h", "443", "REPLACE_WITH_LIVE_KEY_PIN"));
        expectProblem(props("h", "443", null));
        expectProblem(props(null, "443", PIN1));
        expectProblem(props("h", "port", PIN1));
        expectProblem(props("h", "70000", PIN1));
        Properties p = props("h", "443", PIN1);
        p.setProperty("cipher", "rc4");
        expectProblem(p);
    }

    @Test
    public void remembersAndForgetsUsername() throws Exception {
        File dir = File.createTempFile("paste98", "");
        assertTrue(dir.delete() && dir.mkdir());
        File userFile = new File(dir, Config.USER_FILE);
        try {
            Config c = Config.fromProperties(props("h", "443", PIN1), userFile);
            c.setUsername("glitch", true);
            assertTrue(userFile.isFile());
            assertEquals("glitch", Config.fromProperties(props("h", "443", PIN1), userFile).getUsername());

            c.setUsername("glitch", false);
            assertTrue(!userFile.exists());
            assertEquals("glitch", c.getUsername()); // still used for this session
            assertNull(Config.fromProperties(props("h", "443", PIN1), userFile).getUsername());
        } finally {
            userFile.delete();
            dir.delete();
        }
    }

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

    private static void expectProblem(Properties p) {
        try {
            Config.fromProperties(p, null);
            fail("expected a settings problem for " + p);
        } catch (Config.ConfigException expected) {
            // ok
        }
    }
}
