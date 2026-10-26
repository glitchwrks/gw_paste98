package com.glitchwrks.paste98.client.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.Preferences;

import org.junit.After;
import org.junit.Test;

/**
 * PrefsXml must stay interchangeable with java.util.prefs exports (what 0.7
 * and earlier wrote and read). The tests run on a JDK that has
 * java.util.prefs, so they check against the real thing; surefire points its
 * backend at a scratch directory.
 */
public class PrefsXmlTest {

    private static final String NODE = "com/glitchwrks/paste98test/client";

    @After
    public void tearDown() throws Exception {
        Preferences.userRoot().node("com/glitchwrks/paste98test").removeNode();
        Preferences.userRoot().flush();
    }

    private static Map sample() {
        Map m = new TreeMap();
        m.put("server.host", "pastes.glitchwrks.com");
        m.put("pins", "abc/def= sha256//xyz=");
        m.put("odd", "<&>\"' and\ttab\nnewline");
        m.put("accents", "caf\u00e9 \u20ac \u2603");
        m.put("astral", "smile \ud83d\ude00 end");
        m.put("empty", "");
        return m;
    }

    @Test
    public void ourExportImportsIntoJavaUtilPrefs() throws Exception {
        assertTrue("tests must use a scratch prefs directory", System.getProperty("java.util.prefs.userRoot") != null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrefsXml.write(out, NODE, sample());
        byte[] xml = out.toByteArray();
        for (int i = 0; i < xml.length; i++) {
            assertTrue("output must be ASCII", xml[i] > 0);
        }

        Preferences.importPreferences(new ByteArrayInputStream(xml));
        Preferences n = Preferences.userRoot().node(NODE);
        Map expected = sample();
        // XML attribute normalisation turns tab and newline into spaces unless they are references.
        assertEquals(expected.keySet().toString(), new TreeMap(toMap(n)).keySet().toString());
        for (java.util.Iterator it = expected.keySet().iterator(); it.hasNext();) {
            String k = (String) it.next();
            assertEquals(k, expected.get(k), n.get(k, null));
        }
    }

    @Test
    public void javaUtilPrefsExportReadsBack() throws Exception {
        Preferences n = Preferences.userRoot().node(NODE);
        Map expected = sample();
        for (java.util.Iterator it = expected.keySet().iterator(); it.hasNext();) {
            String k = (String) it.next();
            n.put(k, (String) expected.get(k));
        }
        Preferences.userRoot().node("com/glitchwrks/paste98test/client/child").put("deeper", "ignored");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Preferences.userRoot().node("com/glitchwrks/paste98test").exportSubtree(out);

        Map read = PrefsXml.read(new ByteArrayInputStream(out.toByteArray()), NODE);
        assertEquals(expected, read);
        assertEquals("{deeper=ignored}", PrefsXml.read(new ByteArrayInputStream(out.toByteArray()),
                NODE + "/child").toString());
        assertNull(PrefsXml.read(new ByteArrayInputStream(out.toByteArray()), "com/glitchwrks/elsewhere"));
    }

    @Test
    public void ourOwnRoundTrip() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrefsXml.write(out, NODE, sample());
        assertEquals(sample(), PrefsXml.read(new ByteArrayInputStream(out.toByteArray()), NODE));
        out.reset();
        PrefsXml.write(out, NODE, new TreeMap());
        assertEquals(new TreeMap(), PrefsXml.read(new ByteArrayInputStream(out.toByteArray()), NODE));
    }

    @Test
    public void handWrittenVariantsParse() throws Exception {
        String xml = "\ufeff<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<!-- a comment -->\n"
                + "<!DOCTYPE preferences SYSTEM 'http://java.sun.com/dtd/preferences.dtd'>\n"
                + "<preferences EXTERNAL_XML_VERSION='1.0'><root type='user'><map/>\n"
                + "<node name='a'><map/><node name='b'><map>\n"
                + "  <entry key='hex' value='&#x41;&#66;&apos;'/>\n"
                + "  <entry value=\"swapped\" key=\"order\" />\n"
                + "</map></node></node></root></preferences>";
        Map m = PrefsXml.read(new ByteArrayInputStream(xml.getBytes("UTF-8")), "a/b");
        assertEquals("AB'", m.get("hex"));
        assertEquals("swapped", m.get("order"));
    }

    @Test
    public void nonsenseIsRejected() throws Exception {
        String[] bad = { "", "not xml", "<html><body/></html>", "<preferences><map/></preferences>",
            "<preferences><root type='user'><node name='a'><map><entry key='k' value='&bogus;'/></map></node></root></preferences>" };
        for (int i = 0; i < bad.length; i++) {
            try {
                PrefsXml.read(new ByteArrayInputStream(bad[i].getBytes("UTF-8")), "a");
                fail("accepted: " + bad[i]);
            } catch (IOException expected) {
                // good
            }
        }
    }

    private static Map toMap(Preferences n) throws Exception {
        Map m = new TreeMap();
        String[] keys = n.keys();
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], n.get(keys[i], null));
        }
        return m;
    }
}
