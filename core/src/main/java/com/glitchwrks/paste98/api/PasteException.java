/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.api;

/** A paste could not be created. {@link #getKind()} says why, for the UI to explain. */
public final class PasteException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Why the paste failed. */
    public static final class Kind {
        /** Empty or too long; nothing was sent. */
        public static final Kind CONTENT = new Kind("CONTENT");
        /** Username or password rejected (HTTP 401). */
        public static final Kind AUTH = new Kind("AUTH");
        /** The server refused the request as invalid (HTTP 422). */
        public static final Kind REJECTED = new Kind("REJECTED");
        /** The server's key did not match the configured pin. */
        public static final Kind PIN = new Kind("PIN");
        /** Could not connect, or the connection failed. */
        public static final Kind NETWORK = new Kind("NETWORK");
        /** Any other server response. */
        public static final Kind SERVER = new Kind("SERVER");

        private final String name;

        private Kind(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }
    }

    private final Kind kind;

    public PasteException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public PasteException(Kind kind, String message, Throwable cause) {
        super(message);
        initCause(cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
