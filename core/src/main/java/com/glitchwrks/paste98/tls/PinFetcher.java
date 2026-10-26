/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.tls;

import java.io.IOException;
import java.net.Socket;

import org.bouncycastle.tls.TlsClientProtocol;

/**
 * Asks a server for its certificate and reports the key's pin, WITHOUT
 * trusting it. This is trust on first use: the result is whatever the server
 * that answered sent, so an attacker between the client and the server would
 * show their own key. A person must compare the pin with one obtained some
 * other way before relying on it.
 *
 * The handshake is abandoned as soon as the certificate arrives, so no key
 * exchange happens and nothing can be sent. Stopping there also skips Bouncy
 * Castle's validation of an RSA key (several seconds on a Pentium III), which
 * would prove nothing here anyway.
 */
public final class PinFetcher {

    private PinFetcher() {
    }

    /**
     * Connects to the options' host and port and returns what the server presented.
     * The options' pins are ignored.
     *
     * @throws IOException if the server cannot be reached or did not send a certificate
     */
    public static ServerKeyInfo fetch(TlsOptions options) throws IOException {
        PinnedTlsClient client = new PinnedTlsClient(PinnedConnection.crypto(), options, false, null);
        client.setCaptureOnly(true);
        Socket socket = Sockets.connect(options.getHost(), options.getPort(), options.getTimeoutMs());
        try {
            socket.setSoTimeout(options.getTimeoutMs());
            TlsClientProtocol protocol = new TlsClientProtocol(socket.getInputStream(), socket.getOutputStream());
            try {
                protocol.connect(client);
                // Not reached: capture-only mode always ends the handshake at the certificate.
                protocol.close();
            } catch (IOException expected) {
                if (client.getCapturedLeaf() == null) {
                    throw expected;
                }
            }
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
        byte[] leaf = client.getCapturedLeaf();
        if (leaf == null) {
            throw new IOException("the server did not send a certificate");
        }
        return ServerKeyInfo.fromCertificate(leaf, options.getHost(), options.getPort(),
                client.getCapturedChainLength());
    }
}
