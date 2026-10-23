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

import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.util.encoders.Base64;

/**
 * SHA-256 pins over a certificate's SubjectPublicKeyInfo (the same format as
 * HPKP and curl's --pinnedpubkey "sha256//..." option).
 *
 * The pin covers only the public key, so it survives certificate renewal as
 * long as the server keeps the same key pair.
 */
public final class SpkiPin {

    private static final String PREFIX = "sha256//";

    private SpkiPin() {
    }

    /** Computes the base64 SHA-256 SPKI pin of a DER-encoded X.509 certificate. */
    public static String fromCertificate(byte[] certDer) throws IOException {
        Certificate cert = Certificate.getInstance(certDer);
        byte[] spki = cert.getSubjectPublicKeyInfo().getEncoded("DER");
        SHA256Digest d = new SHA256Digest();
        d.update(spki, 0, spki.length);
        byte[] out = new byte[d.getDigestSize()];
        d.doFinal(out, 0);
        return new String(Base64.encode(out), "US-ASCII");
    }

    /**
     * Normalises a user-supplied pin: strips an optional "sha256//" prefix and
     * checks that it decodes to exactly 32 bytes.
     *
     * @throws IllegalArgumentException if the pin is malformed
     */
    public static String normalise(String pin) {
        String p = pin.trim();
        if (p.startsWith(PREFIX)) {
            p = p.substring(PREFIX.length());
        }
        byte[] raw;
        try {
            raw = Base64.decode(p);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("pin is not valid base64: " + pin);
        }
        if (raw.length != 32) {
            throw new IllegalArgumentException("pin must be a base64 SHA-256 hash (32 bytes), got "
                    + raw.length + " bytes: " + pin);
        }
        return p;
    }

    /** Short human-readable description of a certificate: subject and expiry. */
    public static String describe(byte[] certDer) {
        try {
            Certificate cert = Certificate.getInstance(certDer);
            return "subject=" + cert.getSubject() + ", notAfter=" + cert.getEndDate().getTime();
        } catch (RuntimeException e) {
            return "(could not parse certificate: " + e + ")";
        }
    }
}
