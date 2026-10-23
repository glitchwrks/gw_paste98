/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
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
            file = new PrintStream(new FileOutputStream(logFile, false), true);
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

    public static synchronized void error(String msg, Throwable t) {
        info(msg + ": " + t);
        if (file != null) {
            t.printStackTrace(file);
        }
        t.printStackTrace();
    }
}
