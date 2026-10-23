/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.prefs;

import java.io.IOException;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/**
 * A java.util.prefs node whose data lives in a {@link FileStore}. Each node
 * is a view onto the store by its absolute path; AbstractPreferences provides
 * the locking, caching of child nodes, events and XML import/export.
 */
final class FileNode extends AbstractPreferences {

    private final FileStore store;
    private final boolean user;

    /** Root node of a user (or, with user false, system) preferences tree. */
    FileNode(FileStore store, boolean user) {
        super(null, "");
        this.store = store;
        this.user = user;
    }

    private FileNode(FileNode parent, String name) {
        super(parent, name);
        this.store = parent.store;
        this.user = parent.user;
        newNode = !store.exists(absolutePath());
        store.createNode(absolutePath());
    }

    /**
     * AbstractPreferences decides this by comparing the root with the global
     * Preferences.userRoot(), which is wrong for a tree that is not installed
     * globally. XML export labels the tree with it, and import uses the label
     * to pick the tree to import into.
     */
    public boolean isUserNode() {
        return user;
    }

    protected String getSpi(String key) {
        return store.get(absolutePath(), key);
    }

    protected void putSpi(String key, String value) {
        store.put(absolutePath(), key, value);
    }

    protected void removeSpi(String key) {
        store.remove(absolutePath(), key);
    }

    protected String[] keysSpi() {
        return store.keys(absolutePath());
    }

    protected String[] childrenNamesSpi() {
        return store.children(absolutePath());
    }

    protected AbstractPreferences childSpi(String name) {
        return new FileNode(this, name);
    }

    protected void removeNodeSpi() throws BackingStoreException {
        store.removeNode(absolutePath());
    }

    /** Called for every node in a subtree; the store writes at most once. */
    protected void flushSpi() throws BackingStoreException {
        try {
            store.flush();
        } catch (IOException e) {
            throw backingStore("cannot save settings to " + store.getFile(), e);
        }
    }

    protected void syncSpi() throws BackingStoreException {
        try {
            store.sync();
        } catch (IOException e) {
            throw backingStore("cannot save settings to " + store.getFile(), e);
        }
    }

    private static BackingStoreException backingStore(String msg, IOException cause) {
        BackingStoreException e = new BackingStoreException(msg + ": " + cause.getMessage());
        e.initCause(cause);
        return e;
    }
}
