/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Reads gw_paste98 settings out of a java.util.prefs XML export (as written
 * by {@link SettingsStore#exportXml}), without importing it into the live
 * settings: the values go into the Settings dialog for review, and are only
 * saved when the user presses OK.
 *
 * Preferences.importPreferences is not used because it imports every node in
 * the file straight into the live preferences tree.
 */
public final class XmlImport {

    /** Values read from a file, and anything that could not be used. */
    public static final class Result {
        public final Values values;
        public final int count;
        public final List<String> problems;

        Result(Values values, int count, List<String> problems) {
            this.values = values;
            this.count = count;
            this.problems = problems;
        }
    }

    private XmlImport() {
    }

    /**
     * @param base values for settings the file does not mention
     * @throws IOException if the file is not a preferences export containing gw_paste98 settings
     */
    public static Result read(InputStream in, Values base) throws IOException {
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setValidating(false);
            f.setNamespaceAware(false);
            f.setIgnoringComments(true);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            // The DOCTYPE names http://java.sun.com/dtd/preferences.dtd; never fetch it.
            b.setEntityResolver(new EntityResolver() {
                public InputSource resolveEntity(String publicId, String systemId) {
                    return new InputSource(new StringReader(""));
                }
            });
            // Errors become an IOException below; don't also print them to the console.
            b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            doc = b.parse(in);
        } catch (ParserConfigurationException e) {
            throw new IOException("XML parser unavailable: " + e.getMessage());
        } catch (SAXException e) {
            throw new IOException("not a valid settings file: " + e.getMessage());
        }

        Element root = doc.getDocumentElement();
        if (!"preferences".equals(root.getTagName())) {
            throw new IOException("not a Java preferences export");
        }
        Element tree = child(root, "root", null);
        if (tree == null) {
            throw new IOException("not a Java preferences export (no root element)");
        }
        Element node = tree;
        StringTokenizer path = new StringTokenizer(Settings.NODE, "/");
        while (node != null && path.hasMoreTokens()) {
            node = child(node, "node", path.nextToken());
        }
        Element map = node == null ? null : child(node, "map", null);
        if (map == null) {
            throw new IOException("the file contains no gw_paste98 settings");
        }

        Values v = base;
        int count = 0;
        List<String> problems = new ArrayList<String>();
        NodeList entries = map.getChildNodes();
        for (int i = 0; i < entries.getLength(); i++) {
            Node n = entries.item(i);
            if (!(n instanceof Element) || !"entry".equals(((Element) n).getTagName())) {
                continue;
            }
            Element e = (Element) n;
            String key = e.getAttribute("key");
            Setting<?> s = Settings.byKey(key);
            if (s == null) {
                continue; // settings.version, or from a newer version of the program
            }
            try {
                v = SettingsStore.withParsed(v, s, e.getAttribute("value"));
                count++;
            } catch (InvalidSettingException ex) {
                problems.add(ex.getMessage());
            }
        }
        return new Result(v, count, problems);
    }

    /** First child element with the tag (and, if name is not null, that name attribute). */
    private static Element child(Element parent, String tag, String name) {
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element) {
                Element e = (Element) n;
                if (tag.equals(e.getTagName()) && (name == null || name.equals(e.getAttribute("name")))) {
                    return e;
                }
            }
        }
        return null;
    }
}
