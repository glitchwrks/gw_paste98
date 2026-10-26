/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.compat13;

/**
 * Stand-in for java.util.logging.Logger, which Java 1.3 lacks: every logger
 * is disabled. Bouncy Castle's TLS code only logs at FINE and checks
 * isLoggable first, so this costs nothing. See Level.
 */
public class Logger {

    private static final Logger INSTANCE = new Logger();

    protected Logger() {
    }

    public static Logger getLogger(String name) {
        return INSTANCE;
    }

    public boolean isLoggable(Level level) {
        return false;
    }

    public void log(Level level, String msg) {
    }

    public void log(Level level, String msg, Throwable thrown) {
    }

    public void log(Level level, String msg, Object param) {
    }

    public void severe(String msg) {
    }

    public void warning(String msg) {
    }

    public void info(String msg) {
    }

    public void config(String msg) {
    }

    public void fine(String msg) {
    }

    public void finer(String msg) {
    }

    public void finest(String msg) {
    }
}
