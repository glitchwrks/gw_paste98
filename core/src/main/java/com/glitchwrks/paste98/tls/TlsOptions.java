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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Connection settings for {@link PinnedTlsClient}: where to connect, which
 * key pins to trust, and the protocol choices to offer.
 */
public final class TlsOptions {

    /** Cipher suite preference choices. */
    public static final String CIPHER_AUTO = "auto";
    public static final String CIPHER_CHACHA = "chacha";
    public static final String CIPHER_AES128 = "aes128";
    public static final String CIPHER_AES256 = "aes256";

    /** Key exchange group choices. */
    public static final String GROUP_AUTO = "auto";
    public static final String GROUP_X25519 = "x25519";
    public static final String GROUP_P256 = "p256";

    private String host = "pastes.glitchwrks.com";
    private int port = 443;
    private final List pins = new ArrayList();
    private String cipher = CIPHER_AUTO;
    private String group = GROUP_AUTO;
    private boolean offerTls13 = false;
    private boolean preValidatePinnedKey = true;
    private int timeoutMs = 30000;

    public String getHost() {
        return host;
    }

    public TlsOptions setHost(String host) {
        this.host = host;
        return this;
    }

    public int getPort() {
        return port;
    }

    public TlsOptions setPort(int port) {
        this.port = port;
        return this;
    }

    /** Pins in normalised form (bare base64). */
    public List getPins() {
        return Collections.unmodifiableList(pins);
    }

    /**
     * Adds a SHA-256 SPKI pin, bare base64 or with a "sha256//" prefix.
     *
     * @throws IllegalArgumentException if the pin is malformed
     */
    public TlsOptions addPin(String pin) {
        pins.add(SpkiPin.normalise(pin));
        return this;
    }

    public String getCipher() {
        return cipher;
    }

    public TlsOptions setCipher(String cipher) {
        this.cipher = cipher;
        return this;
    }

    public String getGroup() {
        return group;
    }

    public TlsOptions setGroup(String group) {
        this.group = group;
        return this;
    }

    public boolean isOfferTls13() {
        return offerTls13;
    }

    /** Off by default: Bouncy Castle's TLS 1.3 client does not resume sessions. */
    public TlsOptions setOfferTls13(boolean offerTls13) {
        this.offerTls13 = offerTls13;
        return this;
    }

    public boolean isPreValidatePinnedKey() {
        return preValidatePinnedKey;
    }

    /**
     * On by default. See {@link PinnedTlsClient} for why this is safe and why
     * it saves several seconds on a Pentium-class CPU.
     */
    public TlsOptions setPreValidatePinnedKey(boolean preValidatePinnedKey) {
        this.preValidatePinnedKey = preValidatePinnedKey;
        return this;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public TlsOptions setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }
}
