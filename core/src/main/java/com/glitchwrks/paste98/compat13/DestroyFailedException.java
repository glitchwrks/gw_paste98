/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.compat13;

/** Stand-in for javax.security.auth.DestroyFailedException (Java 1.4). See Destroyable. */
public class DestroyFailedException extends Exception {

    public DestroyFailedException() {
    }

    public DestroyFailedException(String message) {
        super(message);
    }
}
