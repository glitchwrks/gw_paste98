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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One preferences tree held in memory and saved to a single file.
 *
 * The file is in java.util.Properties syntax, one line per value, sorted:
 *
 * <pre>
 *   com/glitchwrks/paste98/client/server.host=pastes.glitchwrks.com
 * </pre>
 *
 * The part before the last '/' is the node's path without the leading '/';
 * the part after it is the key. In keys, '%' and '/' are written as %25 and
 * %2F; in node names, '%' is written as %25. A node with no keys is recorded
 * as "path/%" so that it survives a restart.
 *
 * Saving writes a temporary file and then renames it into place. Windows 98
 * cannot rename over an existing file, so the previous file is first renamed
 * to the .BAK name; if the program stops between the two renames, the next
 * load falls back to the .BAK file.
 *
 * All methods are synchronized on the store. (Until 0.7 this was the backend
 * of a java.util.prefs implementation; Java 1.3 has no java.util.prefs, so
 * SettingsFile now uses it directly. The file format is unchanged.)
 */
final class FileStore {

    private static final String EMPTY_NODE_MARKER = "%";

    private final File file;
    private final File tempFile;
    private final File backupFile;
    private final String description;

    /** Node path ("/" or "/a/b") to its key/value map. Every existing node has an entry. */
    private Map nodes = new HashMap();
    private boolean dirty;
    private long loadedModified = -1;
    private long loadedLength = -1;

    FileStore(File file, String description) {
        this.file = file;
        this.tempFile = sibling(file, "$$$");
        this.backupFile = sibling(file, "BAK");
        this.description = description;
        nodes.put("/", new HashMap());
        load();
    }

    File getFile() {
        return file;
    }

    // ---- node data -----------------------------------------------------------

    synchronized String get(String path, String key) {
        Map m = (Map) nodes.get(path);
        return m == null ? null : (String) m.get(key);
    }

    synchronized void put(String path, String key, String value) {
        Map m = ensure(path);
        if (!value.equals(m.get(key))) {
            m.put(key, value);
            dirty = true;
        }
    }

    synchronized void remove(String path, String key) {
        Map m = (Map) nodes.get(path);
        if (m != null && m.remove(key) != null) {
            dirty = true;
        }
    }

    synchronized String[] keys(String path) {
        Map m = (Map) nodes.get(path);
        if (m == null) {
            return new String[0];
        }
        List l = new ArrayList(m.keySet());
        Collections.sort(l);
        return (String[]) l.toArray(new String[l.size()]);
    }

    synchronized boolean exists(String path) {
        return nodes.containsKey(path);
    }

    /** Creates the node (and its ancestors) if missing. */
    synchronized void createNode(String path) {
        ensure(path);
    }

    /** Names of the direct children of path. */
    synchronized String[] children(String path) {
        String prefix = path.equals("/") ? "/" : path + "/";
        TreeSet names = new TreeSet();
        for (Iterator it = nodes.keySet().iterator(); it.hasNext();) {
            String p = (String) it.next();
            if (p.length() > prefix.length() && p.startsWith(prefix)) {
                String rest = p.substring(prefix.length());
                int slash = rest.indexOf('/');
                names.add(slash < 0 ? rest : rest.substring(0, slash));
            }
        }
        return (String[]) names.toArray(new String[names.size()]);
    }

    /** Removes one node. AbstractPreferences removes the children first. */
    synchronized void removeNode(String path) {
        if (!path.equals("/") && nodes.remove(path) != null) {
            dirty = true;
        }
    }

    private Map ensure(String path) {
        Map m = (Map) nodes.get(path);
        if (m == null) {
            if (!path.equals("/")) {
                ensure(parentOf(path));
            }
            m = new HashMap();
            nodes.put(path, m);
            dirty = true;
        }
        return m;
    }

    private static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    // ---- persistence -----------------------------------------------------------

    /** Writes the file if anything changed since the last load or save. */
    synchronized void flush() throws IOException {
        if (dirty) {
            save();
        }
    }

    /**
     * Writes pending changes; with none, reloads the file if something else
     * changed it since it was read.
     */
    synchronized void sync() throws IOException {
        if (dirty) {
            save();
        } else if (changedOnDisk()) {
            load();
        }
    }

    private boolean changedOnDisk() {
        File f = file.isFile() ? file : backupFile;
        return f.isFile() ? f.lastModified() != loadedModified || f.length() != loadedLength : loadedLength >= 0;
    }

    private void load() {
        File source = file.isFile() ? file : (backupFile.isFile() ? backupFile : null);
        Map fresh = new HashMap();
        fresh.put("/", new HashMap());
        if (source == null) {
            nodes = fresh;
            loadedModified = -1;
            loadedLength = -1;
            dirty = false;
            return;
        }
        Properties p = new Properties();
        try {
            InputStream in = new FileInputStream(source);
            try {
                p.load(in);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            // Keep what we have; a broken file must not lose settings already in memory.
            warn("cannot read " + source + ": " + e);
            return;
        }
        for (Enumeration en = p.propertyNames(); en.hasMoreElements();) {
            String line = (String) en.nextElement();
            int slash = line.lastIndexOf('/');
            if (slash < 0) {
                warn("ignoring malformed entry in " + source + ": " + line);
                continue;
            }
            String path = decodePath(line.substring(0, slash));
            String rawKey = line.substring(slash + 1);
            if (path == null) {
                warn("ignoring malformed entry in " + source + ": " + line);
                continue;
            }
            Map m = ensureIn(fresh, path);
            if (!rawKey.equals(EMPTY_NODE_MARKER)) {
                String key = unescape(rawKey);
                if (key == null) {
                    warn("ignoring malformed key in " + source + ": " + line);
                    continue;
                }
                m.put(key, p.getProperty(line));
            }
        }
        nodes = fresh;
        loadedModified = source.lastModified();
        loadedLength = source.length();
        dirty = !source.equals(file); // recovered from .BAK: write it back under the real name
    }

    private static Map ensureIn(Map map, String path) {
        Map m = (Map) map.get(path);
        if (m == null) {
            if (!path.equals("/")) {
                ensureIn(map, parentOf(path));
            }
            m = new HashMap();
            map.put(path, m);
        }
        return m;
    }

    private void save() throws IOException {
        TreeMap lines = new TreeMap();
        for (Iterator it = nodes.entrySet().iterator(); it.hasNext();) {
            Map.Entry e = (Map.Entry) it.next();
            String path = encodePath((String) e.getKey());
            Map m = (Map) e.getValue();
            if (m.isEmpty()) {
                if (!e.getKey().equals("/") && !hasChildren((String) e.getKey())) {
                    lines.put(path + "/" + EMPTY_NODE_MARKER, "");
                }
                continue;
            }
            for (Iterator kv = m.entrySet().iterator(); kv.hasNext();) {
                Map.Entry k = (Map.Entry) kv.next();
                lines.put(path + "/" + escape((String) k.getKey()), k.getValue());
            }
        }

        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create folder " + dir);
        }
        OutputStream out = new FileOutputStream(tempFile);
        try {
            Writer w = new OutputStreamWriter(out, "ISO-8859-1");
            w.write("# " + description + "\r\n");
            w.write("# Written by the program: change settings with its Settings dialog.\r\n");
            for (Iterator it = lines.entrySet().iterator(); it.hasNext();) {
                Map.Entry e = (Map.Entry) it.next();
                w.write(PropertiesSyntax.escapeKey((String) e.getKey()));
                w.write('=');
                w.write(PropertiesSyntax.escapeValue((String) e.getValue()));
                w.write("\r\n");
            }
            w.flush();
            ((FileOutputStream) out).getFD().sync();
        } finally {
            out.close();
        }

        if (file.exists()) {
            if (backupFile.exists() && !backupFile.delete()) {
                throw new IOException("cannot delete " + backupFile);
            }
            if (!file.renameTo(backupFile)) {
                throw new IOException("cannot rename " + file + " to " + backupFile);
            }
        }
        if (!tempFile.renameTo(file)) {
            throw new IOException("cannot rename " + tempFile + " to " + file);
        }
        loadedModified = file.lastModified();
        loadedLength = file.length();
        dirty = false;
    }

    private boolean hasChildren(String path) {
        return children(path).length > 0;
    }

    // ---- encoding --------------------------------------------------------------

    /** "/" becomes "", "/a/b" becomes "a/b", with '%' in names escaped. */
    static String encodePath(String path) {
        if (path.equals("/")) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        String[] parts = splitSlash(path.substring(1));
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            String part = parts[i];
            for (int j = 0; j < part.length(); j++) {
                char c = part.charAt(j);
                if (c == '%') {
                    sb.append("%25");
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /** Inverse of encodePath; null if malformed. */
    static String decodePath(String s) {
        if (s.length() == 0) {
            return "/";
        }
        StringBuffer sb = new StringBuffer();
        String[] parts = splitSlash(s);
        for (int i = 0; i < parts.length; i++) {
            String name = unescape(parts[i]);
            if (name == null || name.length() == 0) {
                return null;
            }
            sb.append('/').append(name);
        }
        return sb.toString();
    }

    /** s split at every '/', keeping empty parts (String.split is Java 1.4). */
    static String[] splitSlash(String s) {
        List parts = new ArrayList();
        int start = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == '/') {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        return (String[]) parts.toArray(new String[parts.size()]);
    }

    static String escape(String key) {
        StringBuffer sb = new StringBuffer(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '%') {
                sb.append("%25");
            } else if (c == '/') {
                sb.append("%2F");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Decodes %25 and %2F; null on any other '%' sequence. */
    static String unescape(String s) {
        StringBuffer sb = new StringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '%') {
                sb.append(c);
            } else if (s.startsWith("%25", i)) {
                sb.append('%');
                i += 2;
            } else if (s.startsWith("%2F", i) || s.startsWith("%2f", i)) {
                sb.append('/');
                i += 2;
            } else {
                return null;
            }
        }
        return sb.toString();
    }

    private static File sibling(File f, String extension) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(f.getAbsoluteFile().getParentFile(), base + "." + extension);
    }

    private static void warn(String msg) {
        System.err.println("preferences: " + msg);
    }

    /** For tests: the raw file text, or null. */
    String readFileText() throws IOException {
        if (!file.isFile()) {
            return null;
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "ISO-8859-1"));
        try {
            StringBuffer sb = new StringBuffer();
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
