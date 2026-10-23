/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client.settings;

/** A setting value that failed validation, with a message for the user. */
public final class InvalidSettingException extends Exception {

    private static final long serialVersionUID = 1L;

    private final transient Setting<?> setting;

    InvalidSettingException(Setting<?> setting, String message) {
        super(message);
        this.setting = setting;
    }

    public Setting<?> getSetting() {
        return setting;
    }
}
