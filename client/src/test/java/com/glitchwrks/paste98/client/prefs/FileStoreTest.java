package com.glitchwrks.paste98.client.prefs;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FileStoreTest {

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

    private FileStore open() {
        return new FileStore(file, "test");
    }

    private static final String APP = "/com/glitchwrks/paste98/client";

    @Test
    public void valuesSurviveARestart() throws Exception {
        FileStore s = open();
        s.put(APP, "server.host", "pastes.glitchwrks.com");
        s.put(APP, "server.port", "443");
        s.put(APP, "ui.confirm", "false");
        s.put(APP, "pins", "Jisk0gqPSNvZGT2YXdsQm/slV6XN/1gv6UWXWHwXyeM= sha256//other=");
        s.flush();

        FileStore again = open();
        assertEquals("pastes.glitchwrks.com", again.get(APP, "server.host"));
        assertEquals("443", again.get(APP, "server.port"));
        assertEquals("false", again.get(APP, "ui.confirm"));
        assertEquals("Jisk0gqPSNvZGT2YXdsQm/slV6XN/1gv6UWXWHwXyeM= sha256//other=", again.get(APP, "pins"));
        assertArrayEquals(new String[] { "pins", "server.host", "server.port", "ui.confirm" }, again.keys(APP));
    }

    @Test
    public void awkwardKeysValuesAndNamesRoundTrip() throws Exception {
        FileStore s = open();
        String node = "/odd%name/with space/x=y:z#!";
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
            s.put(node, cases[i][0], cases[i][1]);
        }
        s.put("/", "rootkey", "at the root");
        s.flush();

        String text = open().readFileText();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            assertTrue("file must be ASCII: " + (int) c, c == '\n' || (c >= 0x20 && c < 0x7f));
        }

        FileStore again = open();
        for (int i = 0; i < cases.length; i++) {
            assertEquals(cases[i][0], cases[i][1], again.get(node, cases[i][0]));
        }
        assertEquals("at the root", again.get("/", "rootkey"));
        assertArrayEquals(new String[] { "odd%name" }, again.children("/"));
    }

    @Test
    public void fileIsSortedAndHasHeader() throws Exception {
        FileStore s = open();
        s.put("/app", "zeta", "1");
        s.put("/app", "alpha", "2");
        s.put("/app", "mid", "3");
        s.flush();
        String text = open().readFileText();
        assertTrue(text, text.startsWith("# test\n# Written by the program"));
        assertTrue(text, text.indexOf("app/alpha=2") < text.indexOf("app/mid=3"));
        assertTrue(text, text.indexOf("app/mid=3") < text.indexOf("app/zeta=1"));
        s.put("/app", "pin", "abc/def=");
        s.put("/app", "odd", "a=b:c#d!e");
        s.flush();
        text = open().readFileText();
        assertTrue("'=' needs no escape in values: " + text, text.indexOf("app/pin=abc/def=\n") > 0);
        assertTrue(text, text.indexOf("app/odd=a=b:c#d!e\n") > 0);
    }

    @Test
    public void emptyNodesPersistAndRemovalIsPersistent() throws Exception {
        FileStore s = open();
        s.createNode("/keep/empty");
        s.put("/gone/child/grandchild", "k", "v");
        s.put("/gone", "k2", "v2");
        s.flush();

        FileStore s2 = open();
        assertTrue(s2.exists("/keep/empty"));
        assertTrue(s2.exists("/gone/child/grandchild"));
        s2.removeNode("/gone/child/grandchild");
        s2.removeNode("/gone/child");
        s2.removeNode("/gone");
        s2.flush();

        FileStore s3 = open();
        assertFalse(s3.exists("/gone"));
        assertFalse(s3.exists("/gone/child/grandchild"));
        assertTrue(s3.exists("/keep/empty"));
        assertArrayEquals(new String[] { "keep" }, s3.children("/"));
    }

    @Test
    public void nothingIsWrittenUntilSomethingChanges() throws Exception {
        FileStore s = open();
        s.get("/", "missing");
        s.flush();
        assertFalse(file.exists());

        s.put("/a", "k", "v");
        s.flush();
        assertTrue(file.exists());
        assertTrue(file.delete());
        s.put("/a", "k", "v"); // same value: not a change
        s.flush();
        assertFalse("an unchanged store must not be rewritten", file.exists());
    }

    @Test
    public void secondSaveKeepsBackupAndRecoversFromIt() throws Exception {
        FileStore s = open();
        s.put("/a", "k", "first");
        s.flush();
        s.put("/a", "k", "second");
        s.flush();
        File bak = new File(dir, "PASTE98.BAK");
        assertTrue(bak.isFile());
        assertFalse(new File(dir, "PASTE98.$$$").exists());

        // Simulate stopping between the two renames: only the .BAK is left.
        assertTrue(file.delete());
        FileStore recovered = open();
        assertEquals("first", recovered.get("/a", "k"));
        recovered.put("/a", "k2", "x");
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
        assertArrayEquals(new String[] { "good" }, open().keys("/app"));
    }

    @Test
    public void syncPicksUpChangesMadeElsewhere() throws Exception {
        FileStore mine = open();
        mine.put("/a", "k", "old");
        mine.flush();

        FileStore other = open();
        other.put("/a", "k", "new");
        Thread.sleep(1100); // file times can be coarse
        other.flush();

        assertEquals("old", mine.get("/a", "k"));
        mine.sync();
        assertEquals("new", mine.get("/a", "k"));
    }

    @Test
    public void settingsFileReadsAndWritesOneNode() throws Exception {
        SettingsFile sf = new SettingsFile(file, "com/glitchwrks/paste98/client");
        assertFalse(sf.exists());
        sf.put("server.host", "pastes.glitchwrks.com");
        sf.put("a", "1");
        sf.flush();
        SettingsFile again = new SettingsFile(file, "com/glitchwrks/paste98/client");
        assertTrue(again.exists());
        assertEquals("pastes.glitchwrks.com", again.get("server.host"));
        assertNull(again.get("missing"));
        assertEquals("[a, server.host]", again.entries().keySet().toString());
        assertEquals("com/glitchwrks/paste98/client", again.getNode());
        // the same file as 0.7's java.util.prefs backend wrote
        assertTrue(open().readFileText().indexOf("com/glitchwrks/paste98/client/server.host=pastes.glitchwrks.com\n") > 0);
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
