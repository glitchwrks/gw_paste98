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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Exception causes on Java 1.3, which has no Throwable.initCause, getCause
 * or cause-taking constructors (all 1.4).
 *
 * The build (retro13's Retarget13) rewrites Bouncy Castle's x.initCause(c)
 * to initCause(x, c), and JDK exception constructors X(Throwable) and
 * X(String, Throwable) to X(String) bracketed by stash(c) and attach(x).
 * Causes are kept in a weak map, so an exception and its cause are freed
 * together as usual. getCause and describe work on any Java: they also ask
 * exceptions that record their own cause (Java 1.4+, and Bouncy Castle's own
 * exception classes) through a getCause() method.
 */
public final class Causes {

    private static final Map CAUSES = Collections.synchronizedMap(new WeakHashMap());
    private static final ThreadLocal STASH = new ThreadLocal();
    private static final int MAX_DEPTH = 10;

    private Causes() {
    }

    /** Replaces t.initCause(cause): records the cause and returns t, as initCause does. */
    public static Throwable initCause(Throwable t, Throwable cause) {
        if (t != null && cause != null && cause != t) {
            CAUSES.put(t, cause);
        }
        return t;
    }

    /** Before a rewritten X(String) constructor: remembers the cause it was given. */
    public static void stash(Throwable cause) {
        STASH.set(cause);
    }

    /** After a rewritten X(String) constructor: records the stashed cause for t. */
    public static void attach(Throwable t) {
        Throwable cause = (Throwable) STASH.get();
        STASH.set(null);
        initCause(t, cause);
    }

    /** Message for a replaced X(Throwable cause) constructor: what Java 1.4+ uses. */
    public static String message(Throwable cause) {
        return cause == null ? null : cause.toString();
    }

    /** The cause of t, or null: recorded here, or from t's own getCause(). */
    public static Throwable getCause(Throwable t) {
        if (t == null) {
            return null;
        }
        Throwable c = (Throwable) CAUSES.get(t);
        if (c != null) {
            return c;
        }
        try {
            Method m = t.getClass().getMethod("getCause", new Class[0]);
            Object o = m.invoke(t, new Object[0]);
            if (o instanceof Throwable && o != t) {
                return (Throwable) o;
            }
        } catch (Exception e) {
            // no getCause() (Java 1.3 exception classes): no cause
        }
        return null;
    }

    /** "x; caused by y; caused by z", for one-line messages. */
    public static String describe(Throwable t) {
        StringBuffer sb = new StringBuffer(String.valueOf(t));
        Throwable c = getCause(t);
        for (int i = 0; c != null && i < MAX_DEPTH; i++) {
            sb.append("; caused by ").append(c);
            c = getCause(c);
        }
        return sb.toString();
    }

    /**
     * Stack trace of t followed by each cause's, like Java 1.4+'s
     * printStackTrace (which on Java 1.3 shows no causes).
     */
    public static String stackTraces(Throwable t) {
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        t.printStackTrace(pw);
        boolean ownChain = hasOwnCauseChain();
        Throwable c = getCause(t);
        for (int i = 0; c != null && i < MAX_DEPTH; i++) {
            if (!ownChain || CAUSES.get(t) != null) {
                // Java 1.4+ already printed causes it knows about; add ours.
                pw.print("Caused by: ");
                c.printStackTrace(pw);
            }
            t = c;
            c = getCause(c);
        }
        pw.flush();
        return out.toString();
    }

    /** True on Java 1.4+, where printStackTrace prints causes itself. */
    private static boolean hasOwnCauseChain() {
        try {
            Throwable.class.getMethod("getCause", new Class[0]);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
