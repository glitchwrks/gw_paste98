package com.glitchwrks.paste98.client.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.glitchwrks.paste98.client.settings.Setting;
import com.glitchwrks.paste98.client.settings.Settings;

/** Keeps client/src/help in step with the program. */
public class HelpTest {

    private static final File HELP = new File("src/help");

    @Test
    public void everySettingIsDocumentedOnItsTabsPage() throws IOException {
        for (int i = 0; i < Settings.SECTIONS.length; i++) {
            String topic = Help.settingsTopic(Settings.SECTIONS[i]);
            File page = new File(HELP, topic);
            assertTrue("no help page " + topic + " for the " + Settings.SECTIONS[i] + " tab", page.isFile());
            String html = read(page);
            List settings = Settings.inSection(Settings.SECTIONS[i]);
            for (int j = 0; j < settings.size(); j++) {
                String label = ((Setting) settings.get(j)).getLabel();
                assertTrue(topic + " does not mention the setting \"" + label + "\"", html.indexOf(label) >= 0);
            }
        }
    }

    @Test
    public void projectListsEveryTopicAndLinksResolve() throws IOException {
        String hhp = read(new File(HELP, "gwpaste.hhp"));
        String files = hhp.substring(hhp.indexOf("[FILES]"));
        File[] all = HELP.listFiles();
        for (int i = 0; i < all.length; i++) {
            String n = all[i].getName();
            if (n.endsWith(".htm") || n.endsWith(".css")) {
                assertTrue(n + " is not in [FILES] of gwpaste.hhp", files.indexOf("\n" + n) >= 0);
            }
        }
        Pattern link = Pattern.compile("(?:href|value)=\"([a-z_]+\\.(?:htm|css))\"");
        for (int i = 0; i < all.length; i++) {
            String n = all[i].getName();
            if (n.endsWith(".htm") || n.endsWith(".hhc") || n.endsWith(".hhk")) {
                Matcher m = link.matcher(read(all[i]));
                while (m.find()) {
                    assertTrue(n + " links to missing " + m.group(1), new File(HELP, m.group(1)).isFile());
                }
            }
        }
        assertTrue(new File(HELP, Help.OVERVIEW).isFile());
        assertTrue(new File(HELP, Help.KEY_PIN).isFile());
    }

    /**
     * Windows 98's HTML Help viewer (tested on a ThinkPad T22) shows an empty
     * Contents tab unless the contents file is in HTML Help Workshop's layout
     * with CRLF line endings; the index is kept the same way.
     */
    @Test
    public void sitemapsUseHtmlHelpWorkshopLayout() throws IOException {
        String[] names = { "toc.hhc", "index.hhk" };
        for (int i = 0; i < names.length; i++) {
            String raw = readRaw(new File(HELP, names[i]));
            assertTrue(names[i] + " has a line not ending in CRLF", raw.replaceAll("\r\n", "").indexOf('\n') < 0);
            assertTrue(names[i] + " lacks the Sitemap 1.0 header", raw.indexOf("<!-- Sitemap 1.0 -->") >= 0);
            assertTrue(names[i] + " puts params on the OBJECT line", raw.indexOf("\"text/sitemap\"><param") < 0);
        }
    }

    @Test
    public void hhTargetFormat() {
        File chm = new File("C:\\Program Files\\Paste Jar\\GWPASTE.CHM");
        assertEquals(chm.getPath() + "::/set_server.htm", Help.target(chm, Help.settingsTopic("Server")));
        assertEquals(chm.getPath(), Help.target(chm, null));
    }

    private static String readRaw(File f) throws IOException {
        FileReader r = new FileReader(f);
        try {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } finally {
            r.close();
        }
    }

    private static String read(File f) throws IOException {
        BufferedReader r = new BufferedReader(new FileReader(f));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            r.close();
        }
    }
}
