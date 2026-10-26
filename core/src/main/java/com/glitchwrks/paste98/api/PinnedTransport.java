/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
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

import com.glitchwrks.paste98.tls.PinnedConnection;
import com.glitchwrks.paste98.tls.TlsOptions;

/** {@link Transport} over a pin-verified TLS connection. */
public final class PinnedTransport implements Transport {

    private final TlsOptions options;

    public PinnedTransport(TlsOptions options) {
        this.options = options;
    }

    public Exchange open() throws IOException {
        final PinnedConnection c = PinnedConnection.open(options);
        return new Exchange() {
            public InputStream getInputStream() {
                return c.getInputStream();
            }

            public OutputStream getOutputStream() {
                return c.getOutputStream();
            }

            public void close() {
                c.close();
            }
        };
    }

    public String getHostHeader() {
        return options.getPort() == 443 ? options.getHost() : options.getHost() + ":" + options.getPort();
    }

    public String getBaseUrl() {
        return "https://" + getHostHeader() + "/";
    }
}
