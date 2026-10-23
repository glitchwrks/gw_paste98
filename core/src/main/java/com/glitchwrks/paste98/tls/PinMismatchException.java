/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.tls;

import java.io.IOException;

/** The server presented a key that matches none of the configured pins. */
public final class PinMismatchException extends IOException {

    private static final long serialVersionUID = 1L;

    private final String presentedPin;

    public PinMismatchException(String presentedPin) {
        super("server key does not match any configured pin; server presented " + presentedPin);
        this.presentedPin = presentedPin;
    }

    public String getPresentedPin() {
        return presentedPin;
    }
}
