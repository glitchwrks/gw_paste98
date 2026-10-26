/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.prefs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * Java's preferences XML format (java.util.prefs exportSubtree and
 * importPreferences, preferences.dtd), written and read without an XML
 * library: Java 1.3 has neither java.util.prefs nor an XML parser. Files are
 * interchangeable with those written by versions up to 0.7, which used
 * java.util.prefs.
 *
 * Only what the format needs is parsed: elements, attributes in single or
 * double quotes, the five predefined entities and character references,
 * comments, the XML declaration and the DOCTYPE (never fetched).
 */
public final class PrefsXml {

    private PrefsXml() {
    }

    /**
     * Writes one node's entries as a user-preferences export of that node,
     * like Preferences.exportSubtree: the node's ancestors appear with empty
     * maps. Output is ASCII; other characters become character references.
     *
     * @param node the node path, e.g. "com/glitchwrks/paste98"
     */
    public static void write(OutputStream out, String node, Map entries) throws IOException {
        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        sb.append("<!DOCTYPE preferences SYSTEM \"http://java.sun.com/dtd/preferences.dtd\">\n");
        sb.append("<preferences EXTERNAL_XML_VERSION=\"1.0\">\n");
        sb.append("  <root type=\"user\">\n");
        sb.append("    <map/>\n");
        StringTokenizer t = new StringTokenizer(node, "/");
        String indent = "    ";
        int depth = 0;
        while (t.hasMoreTokens()) {
            String name = t.nextToken();
            sb.append(indent).append("<node name=\"").append(escape(name)).append("\">\n");
            indent = indent + "  ";
            depth++;
            if (t.hasMoreTokens()) {
                sb.append(indent).append("<map/>\n");
            }
        }
        if (entries.isEmpty()) {
            sb.append(indent).append("<map/>\n");
        } else {
            sb.append(indent).append("<map>\n");
            Map sorted = new TreeMap(entries);
            for (Iterator it = sorted.entrySet().iterator(); it.hasNext();) {
                Map.Entry e = (Map.Entry) it.next();
                sb.append(indent).append("  <entry key=\"").append(escape((String) e.getKey()))
                        .append("\" value=\"").append(escape((String) e.getValue())).append("\"/>\n");
            }
            sb.append(indent).append("</map>\n");
        }
        for (int i = 0; i < depth; i++) {
            indent = indent.substring(2);
            sb.append(indent).append("</node>\n");
        }
        sb.append("  </root>\n");
        sb.append("</preferences>\n");
        out.write(sb.toString().getBytes("US-ASCII"));
        out.flush();
    }

    /** Escapes for an attribute value; line breaks and non-ASCII become character references. */
    static String escape(String s) {
        StringBuffer sb = new StringBuffer(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') {
                sb.append("&amp;");
            } else if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < s.length() && s.charAt(i + 1) >= 0xdc00
                    && s.charAt(i + 1) <= 0xdfff) {
                // a surrogate pair is one character, and XML wants its code point
                int cp = 0x10000 + ((c - 0xd800) << 10) + (s.charAt(++i) - 0xdc00);
                sb.append("&#").append(cp).append(';');
            } else if (c < 0x20 || c > 0x7e) {
                sb.append("&#").append((int) c).append(';');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * The entries of one node of a preferences export.
     *
     * @param node the node path, e.g. "com/glitchwrks/paste98"
     * @return the entries (key to value), or null if the file has no such node
     * @throws IOException if the input is not a preferences export
     */
    public static Map read(InputStream in, String node) throws IOException {
        Parser p = new Parser(decode(readAll(in)));
        Element root = p.parseDocument();
        if (!"preferences".equals(root.name)) {
            throw new IOException("not a Java preferences export");
        }
        Element tree = root.child("root", null);
        if (tree == null) {
            throw new IOException("not a Java preferences export (no root element)");
        }
        Element n = tree;
        StringTokenizer path = new StringTokenizer(node, "/");
        while (n != null && path.hasMoreTokens()) {
            n = n.child("node", path.nextToken());
        }
        Element map = n == null ? null : n.child("map", null);
        if (map == null) {
            return null;
        }
        Map entries = new TreeMap();
        for (int i = 0; i < map.children.size(); i++) {
            Element e = (Element) map.children.elementAt(i);
            if ("entry".equals(e.name)) {
                String key = (String) e.attributes.get("key");
                String value = (String) e.attributes.get("value");
                if (key != null && value != null) {
                    entries.put(key, value);
                }
            }
        }
        return entries;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) {
            out.write(b, 0, n);
            if (out.size() > 1024 * 1024) {
                throw new IOException("file too large for a settings export");
            }
        }
        return out.toByteArray();
    }

    /** UTF-8 (the format's encoding; ASCII is a subset), skipping a byte order mark. */
    private static String decode(byte[] b) throws IOException {
        int start = b.length >= 3 && (b[0] & 0xff) == 0xef && (b[1] & 0xff) == 0xbb && (b[2] & 0xff) == 0xbf ? 3 : 0;
        return new String(b, start, b.length - start, "UTF-8");
    }

    /** A parsed element: name, attributes and child elements (text is ignored). */
    static final class Element {
        final String name;
        final Map attributes = new HashMap();
        final java.util.Vector children = new java.util.Vector();

        Element(String name) {
            this.name = name;
        }

        /** First child element with the name (and, if given, that name attribute). */
        Element child(String tag, String nameAttribute) {
            for (int i = 0; i < children.size(); i++) {
                Element e = (Element) children.elementAt(i);
                if (tag.equals(e.name) && (nameAttribute == null || nameAttribute.equals(e.attributes.get("name")))) {
                    return e;
                }
            }
            return null;
        }
    }

    /** Minimal recursive-descent XML parser for the preferences format. */
    static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        Element parseDocument() throws IOException {
            skipMisc();
            if (!s.startsWith("<", i)) {
                throw error("no root element");
            }
            Element root = parseElement();
            skipMisc();
            if (i < s.length()) {
                throw error("content after the root element");
            }
            return root;
        }

        /** Skips whitespace, comments, processing instructions and a DOCTYPE. */
        private void skipMisc() throws IOException {
            while (true) {
                skipSpace();
                if (s.startsWith("<?", i)) {
                    skipPast("?>");
                } else if (s.startsWith("<!--", i)) {
                    skipPast("-->");
                } else if (s.startsWith("<!DOCTYPE", i)) {
                    skipDoctype();
                } else {
                    return;
                }
            }
        }

        private void skipDoctype() throws IOException {
            int depth = 0;
            for (; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                } else if (c == '>' && depth == 0) {
                    i++;
                    return;
                } else if (c == '"' || c == '\'') {
                    int end = s.indexOf(c, i + 1);
                    if (end < 0) {
                        break;
                    }
                    i = end;
                }
            }
            throw error("unterminated DOCTYPE");
        }

        private Element parseElement() throws IOException {
            expect('<');
            Element e = new Element(parseName());
            while (true) {
                skipSpace();
                if (s.startsWith("/>", i)) {
                    i += 2;
                    return e;
                }
                if (s.startsWith(">", i)) {
                    i++;
                    break;
                }
                String attr = parseName();
                skipSpace();
                expect('=');
                skipSpace();
                e.attributes.put(attr, parseAttributeValue());
            }
            // content: child elements, text (ignored), comments
            while (true) {
                int lt = s.indexOf('<', i);
                if (lt < 0) {
                    throw error("unterminated element <" + e.name + ">");
                }
                i = lt;
                if (s.startsWith("</", i)) {
                    i += 2;
                    String end = parseName();
                    if (!end.equals(e.name)) {
                        throw error("</" + end + "> does not close <" + e.name + ">");
                    }
                    skipSpace();
                    expect('>');
                    return e;
                } else if (s.startsWith("<!--", i)) {
                    skipPast("-->");
                } else if (s.startsWith("<?", i)) {
                    skipPast("?>");
                } else if (s.startsWith("<![CDATA[", i)) {
                    skipPast("]]>");
                } else {
                    e.children.addElement(parseElement());
                }
            }
        }

        private String parseAttributeValue() throws IOException {
            if (i >= s.length()) {
                throw error("missing attribute value");
            }
            char q = s.charAt(i);
            if (q != '"' && q != '\'') {
                throw error("attribute value must be quoted");
            }
            int end = s.indexOf(q, i + 1);
            if (end < 0) {
                throw error("unterminated attribute value");
            }
            String raw = s.substring(i + 1, end);
            i = end + 1;
            return decodeAttribute(raw);
        }

        /** Entities and character references; literal tabs and line breaks become spaces (XML rule). */
        private String decodeAttribute(String raw) throws IOException {
            StringBuffer sb = new StringBuffer(raw.length());
            for (int k = 0; k < raw.length(); k++) {
                char c = raw.charAt(k);
                if (c == '&') {
                    int semi = raw.indexOf(';', k);
                    if (semi < 0) {
                        throw error("bad entity in attribute");
                    }
                    String ent = raw.substring(k + 1, semi);
                    sb.append(entity(ent));
                    k = semi;
                } else if (c == '\t' || c == '\n' || c == '\r') {
                    sb.append(' ');
                } else if (c == '<') {
                    throw error("'<' in attribute value");
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private String entity(String ent) throws IOException {
            if (ent.equals("lt")) {
                return "<";
            }
            if (ent.equals("gt")) {
                return ">";
            }
            if (ent.equals("amp")) {
                return "&";
            }
            if (ent.equals("quot")) {
                return "\"";
            }
            if (ent.equals("apos")) {
                return "'";
            }
            try {
                int cp = -1;
                if (ent.startsWith("#x") || ent.startsWith("#X")) {
                    cp = Integer.parseInt(ent.substring(2), 16);
                } else if (ent.startsWith("#")) {
                    cp = Integer.parseInt(ent.substring(1));
                }
                if (cp >= 0 && cp < 0x10000) {
                    return String.valueOf((char) cp);
                }
                if (cp >= 0x10000 && cp <= 0x10ffff) {
                    cp -= 0x10000;
                    return new String(new char[] { (char) (0xd800 + (cp >> 10)), (char) (0xdc00 + (cp & 0x3ff)) });
                }
            } catch (NumberFormatException e) {
                // fall through
            }
            throw error("unknown entity &" + ent + ";");
        }

        private String parseName() throws IOException {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':') {
                    i++;
                } else {
                    break;
                }
            }
            if (i == start) {
                throw error("expected a name");
            }
            return s.substring(start, i);
        }

        private void skipSpace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private void skipPast(String end) throws IOException {
            int e = s.indexOf(end, i);
            if (e < 0) {
                throw error("missing " + end);
            }
            i = e + end.length();
        }

        private void expect(char c) throws IOException {
            if (i >= s.length() || s.charAt(i) != c) {
                throw error("expected '" + c + "'");
            }
            i++;
        }

        private IOException error(String what) {
            int line = 1;
            for (int k = 0; k < i && k < s.length(); k++) {
                if (s.charAt(k) == '\n') {
                    line++;
                }
            }
            return new IOException("not a valid settings file: " + what + " (line " + line + ")");
        }
    }
}
