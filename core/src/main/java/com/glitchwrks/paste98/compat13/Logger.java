/*
 * Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.compat13;

/**
 * Stand-in for java.util.logging.Logger, which Java 1.3 lacks. The build
 * renames Bouncy Castle's references to java.util.logging to these classes
 * (retro13's Retarget13). Messages at or above the threshold go to the sink
 * the program installs (the client sends them to PASTE98.LOG); with no sink,
 * nothing is logged. Bouncy Castle checks isLoggable first, so a disabled
 * logger costs nothing.
 */
public class Logger {

    /** Where log messages go. */
    public interface Sink {
        void log(String logger, Level level, String message, Throwable thrown);
    }

    private static volatile Sink sink;
    private static volatile int threshold = Level.INFO.intValue();

    private final String name;

    protected Logger(String name) {
        this.name = name;
    }

    /** Installs the sink and the lowest level to pass to it (null sink: log nothing). */
    public static void setSink(Sink s, Level lowest) {
        threshold = lowest.intValue();
        sink = s;
    }

    public static Logger getLogger(String name) {
        return new Logger(name);
    }

    public String getName() {
        return name;
    }

    public boolean isLoggable(Level level) {
        return sink != null && level.intValue() >= threshold && level.intValue() != Level.OFF.intValue();
    }

    public void log(Level level, String msg) {
        log(level, msg, (Throwable) null);
    }

    public void log(Level level, String msg, Throwable thrown) {
        Sink s = sink;
        if (s != null && isLoggable(level)) {
            s.log(name, level, msg, thrown);
        }
    }

    public void log(Level level, String msg, Object param) {
        log(level, msg + " [" + param + "]", (Throwable) null);
    }

    public void severe(String msg) {
        log(Level.SEVERE, msg);
    }

    public void warning(String msg) {
        log(Level.WARNING, msg);
    }

    public void info(String msg) {
        log(Level.INFO, msg);
    }

    public void config(String msg) {
        log(Level.CONFIG, msg);
    }

    public void fine(String msg) {
        log(Level.FINE, msg);
    }

    public void finer(String msg) {
        log(Level.FINER, msg);
    }

    public void finest(String msg) {
        log(Level.FINEST, msg);
    }
}
