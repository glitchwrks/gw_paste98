/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Opens a fresh connection per request. In the client this is a pinned TLS
 * connection; tests substitute an in-memory one.
 */
public interface Transport {

    /** One open connection. */
    interface Exchange {
        InputStream getInputStream();

        OutputStream getOutputStream();

        void close();
    }

    /** Opens a connection; may throw {@link com.glitchwrks.paste98.tls.PinMismatchException}. */
    Exchange open() throws IOException;

    /** Host header value, e.g. "pastes.glitchwrks.com". */
    String getHostHeader();

    /** Base URL pastes are shown at, ending in '/'. */
    String getBaseUrl();
}
