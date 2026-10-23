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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;

import org.bouncycastle.tls.TlsClientProtocol;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

/**
 * An open, pin-verified TLS connection.
 *
 * Uses Bouncy Castle's lightweight crypto (BcTlsCrypto), so it does not depend
 * on Java 5's JCE provider or its export-strength policy files.
 */
public final class PinnedConnection {

    private static BcTlsCrypto sharedCrypto;

    private final Socket socket;
    private final TlsClientProtocol protocol;
    private final PinnedTlsClient client;

    private PinnedConnection(Socket socket, TlsClientProtocol protocol, PinnedTlsClient client) {
        this.socket = socket;
        this.protocol = protocol;
        this.client = client;
    }

    /**
     * One BcTlsCrypto (and SecureRandom) per JVM: seeding SecureRandom costs a
     * few hundred milliseconds on old hardware, so it is done once.
     */
    public static synchronized BcTlsCrypto crypto() {
        if (sharedCrypto == null) {
            SecureRandom random = new SecureRandom();
            random.nextBytes(new byte[32]); // force seeding now rather than mid-handshake
            sharedCrypto = new BcTlsCrypto(random);
        }
        return sharedCrypto;
    }

    /**
     * Connects and completes a TLS handshake that only succeeds if the server
     * key matches one of the options' pins.
     *
     * @throws PinMismatchException if the server's key is not pinned
     * @throws IOException on any other network or TLS failure
     */
    public static PinnedConnection open(TlsOptions options) throws IOException {
        if (options.getPins().isEmpty()) {
            throw new IOException("no server key pin is configured");
        }
        Socket socket = new Socket();
        PinnedTlsClient client = new PinnedTlsClient(crypto(), options, true, null);
        try {
            socket.connect(new InetSocketAddress(options.getHost(), options.getPort()), options.getTimeoutMs());
            socket.setSoTimeout(options.getTimeoutMs());
            socket.setTcpNoDelay(true);
            TlsClientProtocol protocol = new TlsClientProtocol(socket.getInputStream(), socket.getOutputStream());
            protocol.connect(client);
            return new PinnedConnection(socket, protocol, client);
        } catch (IOException e) {
            closeQuietly(socket);
            if (client.pinMismatch()) {
                throw new PinMismatchException(client.getObservedPin());
            }
            throw e;
        } catch (RuntimeException e) {
            closeQuietly(socket);
            throw e;
        }
    }

    public InputStream getInputStream() {
        return protocol.getInputStream();
    }

    public OutputStream getOutputStream() {
        return protocol.getOutputStream();
    }

    /** Negotiated protocol and cipher suite, for display. */
    public String describe() {
        return client.describeConnection();
    }

    public void close() {
        try {
            protocol.close();
        } catch (IOException ignored) {
            // the socket is closed next regardless
        }
        closeQuietly(socket);
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }
}
