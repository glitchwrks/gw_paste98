package com.glitchwrks.paste98.client.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.glitchwrks.paste98.client.prefs.SettingsFile;
import com.glitchwrks.paste98.tls.TlsOptions;

public class SettingsTest {

    private static final String PIN1 = "Jisk0gqPSNvZGT2YXdsQm/slV6XN/1gv6UWXWHwXyeM=";
    private static final String PIN2 = "MnwlM5ruy9bz2zSVXbMqbxe8F0JZzLfgyIpCpzEc3VQ=";

    private File dir;

    @Before
    public void setUp() throws IOException {
        dir = File.createTempFile("settings", "");
        assertTrue(dir.delete() && dir.mkdir());
    }

    @After
    public void tearDown() {
        File[] files = dir.listFiles();
        for (int i = 0; files != null && i < files.length; i++) {
            files[i].delete();
        }
        dir.delete();
    }

    private SettingsFile tree() {
        return new SettingsFile(new File(dir, "PASTE98.PRF"), Settings.NODE);
    }

    // ---- declarations --------------------------------------------------------

    @Test
    public void declarationsAreConsistent() {
        List<String> keys = new ArrayList<String>();
        for (int i = 0; i < Settings.all().size(); i++) {
            Setting s = (Setting) Settings.all().get(i);
            assertFalse("duplicate key " + s.getKey(), keys.contains(s.getKey()));
            keys.add(s.getKey());
            assertTrue(s.getKey().length() <= 80); // java.util.prefs' limit, for exports
            boolean known = false;
            for (int j = 0; j < Settings.SECTIONS.length; j++) {
                known |= Settings.SECTIONS[j].equals(s.getSection());
            }
            assertTrue("unknown section for " + s, known);
            assertDefaultRoundTrips(s);
        }
    }

    private static void assertDefaultRoundTrips(Setting s) {
        try {
            assertEquals(s.getKey(), s.getDefault(), s.parse(s.format(s.getDefault())));
        } catch (InvalidSettingException e) {
            fail("default of " + s + " does not validate: " + e.getMessage());
        }
    }

    @Test
    public void validation() throws Exception {
        assertEquals("pastes.example.com", Settings.HOST.parse("  pastes.example.com "));
        assertEquals("10.0.0.5", Settings.HOST.parse("10.0.0.5"));
        bad(Settings.HOST, "");
        bad(Settings.HOST, "https://pastes.example.com");
        bad(Settings.HOST, "two words");

        assertEquals(Integer.valueOf(8443), Settings.PORT.parse("8443"));
        bad(Settings.PORT, "0");
        bad(Settings.PORT, "65536");
        bad(Settings.PORT, "443x");

        assertEquals(Boolean.FALSE, Settings.WARM_UP.parse("FALSE"));
        bad(Settings.WARM_UP, "yes");

        assertEquals(TlsOptions.CIPHER_CHACHA, Settings.CIPHER.parse("chacha"));
        bad(Settings.CIPHER, "rc4");

        List pins = (List) Settings.PINS.parse(PIN1 + "\r\n\r\n  sha256//" + PIN2 + "\n" + PIN1);
        assertEquals(2, pins.size()); // duplicate dropped, prefix stripped
        assertEquals(PIN2, pins.get(1));
        assertEquals(PIN1 + " " + PIN2, Settings.PINS.format(pins));
        assertTrue(((List) Settings.PINS.parse("  \n ")).isEmpty());
        bad(Settings.PINS, "REPLACE_WITH_LIVE_KEY_PIN");
        bad(Settings.PINS, PIN1 + " AAAA");

        assertEquals("", Settings.USERNAME.parse("   "));
        bad(Settings.USERNAME, "tab\there");
    }

    private static void bad(Setting s, String text) {
        try {
            s.parse(text);
            fail(s + " accepted \"" + text + "\"");
        } catch (InvalidSettingException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith(s.getLabel() + ": "));
            assertEquals(s, e.getSetting());
        }
    }

    // ---- store -----------------------------------------------------------------

    @Test
    public void freshInstallIsAllDefaultsAndNotConfigured() {
        SettingsStore store = new SettingsStore(tree());
        Values v = store.current();
        assertFalse(store.hasBeenSaved());
        assertFalse(v.isConfigured());
        assertEquals("pastes.glitchwrks.com", v.get(Settings.HOST));
        assertEquals(443, v.getInt(Settings.PORT));
        assertNull(v.rememberedUsername());
        assertTrue(v.is(Settings.WARM_UP));
        assertFalse("reading must not create the settings file", new File(dir, "PASTE98.PRF").exists());
    }

    @Test
    public void saveLoadAndNotify() throws Exception {
        SettingsStore store = new SettingsStore(tree());
        final List<String> events = new ArrayList<String>();
        store.addListener(new SettingsStore.Listener() {
            public void settingsChanged(Values before, Values after) {
                events.add(before.get(Settings.HOST) + "->" + after.get(Settings.HOST));
            }
        });
        Values v = store.current().with(Settings.HOST, "pastes.example.com").with(Settings.PORT, Integer.valueOf(8443))
                .with(Settings.PINS, Settings.PINS.parse(PIN1 + " " + PIN2)).with(Settings.WARM_UP, Boolean.FALSE)
                .with(Settings.USERNAME, "glitch");
        store.save(v);
        assertEquals("[pastes.glitchwrks.com->pastes.example.com]", events.toString());

        SettingsStore again = new SettingsStore(tree());
        Values r = again.current();
        assertTrue(again.hasBeenSaved());
        assertEquals("pastes.example.com", r.get(Settings.HOST));
        assertEquals(8443, r.getInt(Settings.PORT));
        assertEquals(2, r.getList(Settings.PINS).size());
        assertFalse(r.is(Settings.WARM_UP));
        assertEquals("glitch", r.rememberedUsername());

        TlsOptions o = r.tlsOptions();
        assertEquals("pastes.example.com", o.getHost());
        assertEquals(8443, o.getPort());
        assertEquals(2, o.getPins().size());
        assertEquals(30000, o.getTimeoutMs());

        again.rememberUsername(null);
        assertNull(new SettingsStore(tree()).current().rememberedUsername());
    }

    @Test
    public void invalidStoredValuesFallBackToDefaults() throws Exception {
        SettingsFile node = tree();
        node.put(Settings.PORT.getKey(), "99999");
        node.put(Settings.PINS.getKey(), "not-a-pin");
        node.put(Settings.HOST.getKey(), "pastes.example.com");
        node.flush();
        SettingsStore store = new SettingsStore(tree());
        assertEquals(443, store.current().getInt(Settings.PORT));
        assertTrue(store.current().getList(Settings.PINS).isEmpty());
        assertEquals("pastes.example.com", store.current().get(Settings.HOST));
        assertEquals(2, store.getLoadProblems().size());
    }

    @Test
    public void serverComparisons() throws Exception {
        Values a = Values.defaults().with(Settings.PINS, Settings.PINS.parse(PIN1));
        assertTrue(a.sameServerAs(a.with(Settings.HOST, "PASTES.GLITCHWRKS.COM")));
        assertFalse(a.sameServerAs(a.with(Settings.PORT, Integer.valueOf(8443))));
        assertFalse(a.sameServerAs(a.with(Settings.HOST, "evil.example")));
        assertTrue(a.sameConnectionAs(a.with(Settings.SHOW_RESULT, Boolean.FALSE)));
        assertFalse(a.sameConnectionAs(a.with(Settings.PINS, Settings.PINS.parse(PIN2))));
        assertFalse(a.sameConnectionAs(a.with(Settings.CIPHER, TlsOptions.CIPHER_AES128)));
    }

    // ---- importing the old files ----------------------------------------------------

    @Test
    public void importsOldSettingsOnce() throws Exception {
        write("PASTE98.CFG", "# old settings\nhost=pastes.example.com\nport=8443\npins=" + PIN1 + " sha256//" + PIN2
                + "\n#cipher=auto\n");
        write("PASTE98.USR", "username=glitch\n");
        SettingsStore store = new SettingsStore(tree());
        String msg = store.importLegacy(dir);
        assertTrue(msg, msg.indexOf("[host, port, pins, username]") > 0);

        Values v = new SettingsStore(tree()).current();
        assertEquals("pastes.example.com", v.get(Settings.HOST));
        assertEquals(8443, v.getInt(Settings.PORT));
        assertEquals(2, v.getList(Settings.PINS).size());
        assertEquals("glitch", v.rememberedUsername());
        assertFalse(new File(dir, "PASTE98.CFG").exists());
        assertTrue(new File(dir, "PASTE98.OLD").isFile());
        assertFalse(new File(dir, "PASTE98.USR").exists());

        // A PASTE98.CFG appearing later (e.g. unzipping the old version again) is ignored.
        write("PASTE98.CFG", "host=other.example\n");
        assertNull(new SettingsStore(tree()).importLegacy(dir));
        assertEquals("pastes.example.com", new SettingsStore(tree()).current().get(Settings.HOST));
    }

    @Test
    public void importSkipsTheUneditedTemplatePin() throws Exception {
        write("PASTE98.CFG", "host=pastes.glitchwrks.com\nport=443\npins=REPLACE_WITH_LIVE_KEY_PIN\n");
        SettingsStore store = new SettingsStore(tree());
        String msg = store.importLegacy(dir);
        assertTrue(msg, msg.indexOf("skipped invalid [pins") > 0);
        assertFalse(store.current().isConfigured());
    }

    @Test
    public void nothingToImportOnAFreshInstall() {
        assertNull(new SettingsStore(tree()).importLegacy(dir));
        assertFalse(new File(dir, "PASTE98.PRF").exists());
    }

    // ---- XML export and import ------------------------------------------------------------

    @Test
    public void xmlRoundTripGoesThroughTheDialogNotTheLiveSettings() throws Exception {
        SettingsStore store = new SettingsStore(tree());
        store.save(store.current().with(Settings.HOST, "pastes.example.com")
                .with(Settings.PINS, Settings.PINS.parse(PIN1)).with(Settings.COPY_URL, Boolean.FALSE));
        ByteArrayOutputStream xml = new ByteArrayOutputStream();
        store.exportXml(xml);
        String text = xml.toString("UTF-8");
        assertTrue(text, text.indexOf("<root type=\"user\">") > 0);
        assertTrue(text, text.indexOf("key=\"server.host\" value=\"pastes.example.com\"") > 0);

        XmlImport.Result r = XmlImport.read(new ByteArrayInputStream(xml.toByteArray()), Values.defaults());
        assertEquals(Settings.all().size(), r.count);
        assertTrue(r.problems.isEmpty());
        assertEquals("pastes.example.com", r.values.get(Settings.HOST));
        assertEquals(PIN1, r.values.getList(Settings.PINS).get(0));
        assertFalse(r.values.is(Settings.COPY_URL));
    }

    @Test
    public void xmlImportReportsBadValuesAndRejectsOtherFiles() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE preferences SYSTEM \"http://java.sun.com/dtd/preferences.dtd\">\n"
                + "<preferences EXTERNAL_XML_VERSION=\"1.0\"><root type=\"user\"><map/>"
                + "<node name=\"com\"><map/><node name=\"glitchwrks\"><map/><node name=\"paste98\"><map>"
                + "<entry key=\"server.port\" value=\"70000\"/>"
                + "<entry key=\"server.host\" value=\"pastes.example.com\"/>"
                + "<entry key=\"some.future.setting\" value=\"x\"/>"
                + "</map></node></node></node></root></preferences>";
        XmlImport.Result r = XmlImport.read(new ByteArrayInputStream(xml.getBytes("UTF-8")), Values.defaults());
        assertEquals(1, r.count);
        assertEquals(1, r.problems.size());
        assertEquals(443, r.values.getInt(Settings.PORT));
        assertEquals("pastes.example.com", r.values.get(Settings.HOST));

        String other = "<?xml version=\"1.0\"?><preferences><root type=\"user\"><map/>"
                + "<node name=\"org\"><map><entry key=\"a\" value=\"b\"/></map></node></root></preferences>";
        expectIo(other);
        expectIo("<html><body>not settings</body></html>");
        expectIo("this is not XML");
    }

    private static void expectIo(String text) throws Exception {
        try {
            XmlImport.read(new ByteArrayInputStream(text.getBytes("UTF-8")), Values.defaults());
            fail("accepted: " + text);
        } catch (IOException expected) {
            // ok
        }
    }

    private void write(String name, String text) throws IOException {
        FileOutputStream out = new FileOutputStream(new File(dir, name));
        out.write(text.getBytes("ISO-8859-1"));
        out.close();
    }
}
