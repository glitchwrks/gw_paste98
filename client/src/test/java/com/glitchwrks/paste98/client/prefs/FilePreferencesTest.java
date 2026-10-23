package com.glitchwrks.paste98.client.prefs;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.prefs.Preferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FilePreferencesTest {

    private File dir;
    private File file;

    @Before
    public void setUp() throws IOException {
        dir = File.createTempFile("prefs", "");
        assertTrue(dir.delete() && dir.mkdir());
        file = new File(dir, "PASTE98.PRF");
    }

    @After
    public void tearDown() {
        File[] files = dir.listFiles();
        for (int i = 0; files != null && i < files.length; i++) {
            files[i].delete();
        }
        dir.delete();
    }

    private Preferences open() {
        return new FileNode(new FileStore(file, "test"), true);
    }

    @Test
    public void valuesSurviveARestart() throws Exception {
        Preferences root = open();
        Preferences n = root.node("com/glitchwrks/paste98/client");
        n.put("server.host", "pastes.glitchwrks.com");
        n.putInt("server.port", 443);
        n.putBoolean("ui.confirm", false);
        n.put("pins", "Jisk0gqPSNvZGT2YXdsQm/slV6XN/1gv6UWXWHwXyeM= sha256//other=");
        root.flush();

        Preferences again = open().node("com/glitchwrks/paste98/client");
        assertEquals("pastes.glitchwrks.com", again.get("server.host", null));
        assertEquals(443, again.getInt("server.port", 0));
        assertFalse(again.getBoolean("ui.confirm", true));
        assertEquals("Jisk0gqPSNvZGT2YXdsQm/slV6XN/1gv6UWXWHwXyeM= sha256//other=", again.get("pins", null));
        assertArrayEquals(new String[] { "pins", "server.host", "server.port", "ui.confirm" }, again.keys());
    }

    @Test
    public void awkwardKeysValuesAndNamesRoundTrip() throws Exception {
        Preferences root = open();
        Preferences n = root.node("odd%name/with space/x=y:z#!");
        String[][] cases = {
            { "a/b", "slash in key" },
            { "100%", "percent" },
            { "%2F", "looks escaped" },
            { "eq=col:hash#bang!", "  leading spaces, = : # ! \\ and \t tab" },
            { "new\nline", "line one\r\nline two" },
            { " lead", "caf\u00e9 \u20ac \u2603" },
            { "", "empty key" },
        };
        for (int i = 0; i < cases.length; i++) {
            n.put(cases[i][0], cases[i][1]);
        }
        root.put("rootkey", "at the root");
        root.flush();

        String text = new FileStore(file, "test").readFileText();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            assertTrue("file must be ASCII: " + (int) c, c == '\n' || (c >= 0x20 && c < 0x7f));
        }

        Preferences root2 = open();
        Preferences again = root2.node("odd%name/with space/x=y:z#!");
        for (int i = 0; i < cases.length; i++) {
            assertEquals(cases[i][0], cases[i][1], again.get(cases[i][0], null));
        }
        assertEquals("at the root", root2.get("rootkey", null));
        assertArrayEquals(new String[] { "odd%name" }, root2.childrenNames());
    }

    @Test
    public void fileIsSortedAndHasHeader() throws Exception {
        Preferences n = open().node("app");
        n.put("zeta", "1");
        n.put("alpha", "2");
        n.put("mid", "3");
        n.flush();
        String text = new FileStore(file, "test").readFileText();
        assertTrue(text, text.startsWith("# test\n# Written by the program"));
        assertTrue(text, text.indexOf("app/alpha=2") < text.indexOf("app/mid=3"));
        assertTrue(text, text.indexOf("app/mid=3") < text.indexOf("app/zeta=1"));
        n.put("pin", "abc/def=");
        n.put("odd", "a=b:c#d!e");
        n.flush();
        text = new FileStore(file, "test").readFileText();
        assertTrue("'=' needs no escape in values: " + text, text.indexOf("app/pin=abc/def=\n") > 0);
        assertTrue(text, text.indexOf("app/odd=a=b:c#d!e\n") > 0);
    }

    @Test
    public void emptyNodesPersistAndRemovalIsPersistent() throws Exception {
        Preferences root = open();
        root.node("keep/empty");
        Preferences gone = root.node("gone/child/grandchild");
        gone.put("k", "v");
        root.node("gone").put("k2", "v2");
        root.flush();

        Preferences r2 = open();
        assertTrue(r2.nodeExists("keep/empty"));
        assertTrue(r2.nodeExists("gone/child/grandchild"));
        r2.node("gone").removeNode();
        r2.flush();

        Preferences r3 = open();
        assertFalse(r3.nodeExists("gone"));
        assertFalse(r3.nodeExists("gone/child/grandchild"));
        assertTrue(r3.nodeExists("keep/empty"));
        assertArrayEquals(new String[] { "keep" }, r3.childrenNames());
    }

    @Test
    public void nothingIsWrittenUntilSomethingChanges() throws Exception {
        Preferences root = open();
        root.get("missing", "default");
        root.flush();
        assertFalse(file.exists());

        root.node("a"); // creating a node is a change under the Preferences contract
        root.flush();
        assertTrue(file.exists());

        root.node("a").put("k", "v");
        root.flush();
        assertTrue(file.delete());
        root.node("a").put("k", "v"); // same value: not a change
        root.flush();
        assertFalse("an unchanged tree must not be rewritten", file.exists());
    }

    @Test
    public void secondSaveKeepsBackupAndRecoversFromIt() throws Exception {
        Preferences root = open();
        root.node("a").put("k", "first");
        root.flush();
        root.node("a").put("k", "second");
        root.flush();
        File bak = new File(dir, "PASTE98.BAK");
        assertTrue(bak.isFile());
        assertFalse(new File(dir, "PASTE98.$$$").exists());

        // Simulate stopping between the two renames: only the .BAK is left.
        assertTrue(file.delete());
        Preferences recovered = open();
        assertEquals("first", recovered.node("a").get("k", null));
        recovered.flush(); // writes it back under the real name
        assertTrue(file.isFile());
    }

    @Test
    public void malformedLinesAreSkipped() throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        out.write(("# hand edited\r\n"
                + "app/good=yes\r\n"
                + "noslash=ignored\r\n"
                + "app/bad%zz=ignored\r\n"
                + "app//double=ignored\r\n").getBytes("ISO-8859-1"));
        out.close();
        Preferences app = open().node("app");
        assertArrayEquals(new String[] { "good" }, app.keys());
    }

    @Test
    public void syncPicksUpChangesMadeElsewhere() throws Exception {
        Preferences mine = open();
        mine.node("a").put("k", "old");
        mine.flush();

        Preferences other = open();
        other.node("a").put("k", "new");
        Thread.sleep(1100); // file times can be coarse
        other.flush();

        assertEquals("old", mine.node("a").get("k", null));
        mine.sync();
        assertEquals("new", mine.node("a").get("k", null));
    }

    @Test
    public void exportAndImportUseTheStandardXmlFormat() throws Exception {
        Preferences n = open().node("com/glitchwrks/paste98/client");
        n.put("server.host", "pastes.glitchwrks.com");
        n.put("pins", "abc/def=");
        ByteArrayOutputStream xml = new ByteArrayOutputStream();
        n.exportSubtree(xml);
        String s = xml.toString("UTF-8");
        assertTrue(s, s.indexOf("<!DOCTYPE preferences") >= 0);
        assertTrue(s, s.indexOf("<root type=\"user\">") > 0);
        assertTrue(s, s.indexOf("key=\"server.host\" value=\"pastes.glitchwrks.com\"") > 0);

        // importPreferences always targets Preferences.userRoot(); the test
        // JVM is configured (surefire) to use this backend in a scratch file.
        assertTrue("tests must run with the file backend", FilePreferencesFactory.isActive());
        Preferences.importPreferences(new ByteArrayInputStream(xml.toByteArray()));
        Preferences imported = Preferences.userRoot().node("com/glitchwrks/paste98/client");
        assertEquals("pastes.glitchwrks.com", imported.get("server.host", null));
        assertEquals("abc/def=", imported.get("pins", null));
        imported.removeNode();
        Preferences.userRoot().flush();
    }

    @Test
    public void pathEncoding() {
        assertEquals("", FileStore.encodePath("/"));
        assertEquals("a/b%25c", FileStore.encodePath("/a/b%c"));
        assertEquals("/a/b%c", FileStore.decodePath("a/b%25c"));
        assertEquals("/", FileStore.decodePath(""));
        assertNull(FileStore.decodePath("a//b"));
        assertNull(FileStore.unescape("%41"));
        assertEquals("a/b%", FileStore.unescape(FileStore.escape("a/b%")));
    }
}
