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
 * Java 1.3 exceptions cannot carry a cause (Throwable.initCause and the
 * Throwable-taking constructors arrived in 1.4). The build rewrites those
 * calls in Bouncy Castle to these methods (see src/build/java/Retarget13.java):
 * the cause is dropped, but its description is kept in the message where the
 * original call allowed one.
 */
public final class Causes {

    private Causes() {
    }

    /** Replaces t.initCause(cause): returns t unchanged, as initCause does. */
    public static Throwable initCause(Throwable t, Throwable cause) {
        return t;
    }

    /** Message for a replaced X(Throwable cause) constructor: what 1.4 would use. */
    public static String message(Throwable cause) {
        return cause == null ? null : cause.toString();
    }
}
