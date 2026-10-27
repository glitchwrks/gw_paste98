/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.glitchwrks.paste98.compat13.Causes;
import com.glitchwrks.paste98.compat13.Level;
import com.glitchwrks.paste98.compat13.Logger;

/**
 * Minimal log to PASTE98.LOG in the application folder (started fresh each
 * launch) and to stderr. Under javaw there is no console, so the file is the
 * only record of, for example, why the tray icon could not be shown.
 *
 * Never logs paste content, passwords or Authorization headers.
 */
public final class Log {

    private static PrintStream file;
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss.SSS");

    private Log() {
    }

    public static synchronized void open(File logFile) {
        try {
            file = new PrintStream(new FileOutputStream(logFile.getPath()) /* (File, boolean) is Java 1.4 */, true);
        } catch (IOException e) {
            System.err.println("cannot write log " + logFile + ": " + e);
        }
    }

    public static synchronized void info(String msg) {
        String line = TIME.format(new Date()) + " " + Thread.currentThread().getName() + ": " + msg;
        System.err.println(line);
        if (file != null) {
            file.println(line);
        }
    }

    /** Logs the error and its stack trace, with every cause (which Java 1.3 cannot show itself). */
    public static synchronized void error(String msg, Throwable t) {
        info(msg + ": " + Causes.describe(t));
        String traces = Causes.stackTraces(t);
        if (file != null) {
            file.print(traces);
        }
        System.err.print(traces);
    }

    /**
     * Sends Bouncy Castle's own log messages (its java.util.logging calls,
     * renamed by the build to compat13.Logger) to this log: INFO and above,
     * or everything from FINE with -Dpaste98.debug=true.
     */
    public static void routeLibraryLogging() {
        Level lowest = Boolean.getBoolean("paste98.debug") ? Level.FINE : Level.INFO;
        Logger.setSink(new Logger.Sink() {
            public void log(String logger, Level level, String message, Throwable thrown) {
                if (thrown != null) {
                    error("bc " + level + " " + logger + ": " + message, thrown);
                } else {
                    info("bc " + level + " " + logger + ": " + message);
                }
            }
        }, lowest);
    }
}
