/*
 * Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.tls;

/**
 * Receives a call at each step of a handshake. Used by the benchmark to build
 * phase timelines; the client does not need one.
 */
public interface HandshakeListener {

    /** Called on the handshaking thread with a short description of the step just reached. */
    void step(String description);
}
