/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.bench13;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.NameType;
import org.bouncycastle.tls.NamedGroup;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SecurityParameters;
import org.bouncycastle.tls.ServerName;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.TlsSession;
import org.bouncycastle.tls.TlsUtils;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.util.Strings;
import org.bouncycastle.util.encoders.Base64;

/**
 * Java 1.3 port of the client's PinnedTlsClient (core module): TLS 1.2 with
 * forward-secret AEAD suites, the server authenticated only by a SHA-256 pin
 * of its public key. Written without Java 5 syntax or 1.4+ APIs.
 */
final class PinnedClient13 extends DefaultTlsClient {

    static final String CIPHER_AUTO = "auto";
    static final String CIPHER_CHACHA = "chacha";
    static final String CIPHER_AES128 = "aes128";
    static final String CIPHER_AES256 = "aes256";
    static final String GROUP_AUTO = "auto";
    static final String GROUP_X25519 = "x25519";
    static final String GROUP_P256 = "p256";

    /** Strong references to moduli marked validated (BC's cache is weak). */
    private static final Vector PRE_VALIDATED = new Vector();

    /** Receives handshake steps, for the timeline. */
    interface Steps {
        void step(String what);
    }

    private final String host;
    private final Vector pins;
    private final String cipher;
    private final String group;
    private final boolean enforcePins;
    private final boolean preValidate;
    private final TlsSession resume;
    private Steps steps;

    private String observedPin;
    private boolean captureOnly;

    PinnedClient13(TlsCrypto crypto, String host, Vector pins, String cipher, String group, boolean enforcePins,
            boolean preValidate, TlsSession resume) {
        super(crypto);
        this.host = host;
        this.pins = pins;
        this.cipher = cipher;
        this.group = group;
        this.enforcePins = enforcePins;
        this.preValidate = preValidate;
        this.resume = resume;
    }

    void setSteps(Steps steps) {
        this.steps = steps;
    }

    /** Stop at the server's certificate (only to show its pin; pins not enforced). */
    void setCaptureOnly(boolean c) {
        this.captureOnly = c;
    }

    private void step(String what) {
        if (steps != null) {
            steps.step(what);
        }
    }

    public void notifyHandshakeBeginning() throws IOException {
        step("handshake begins");
        super.notifyHandshakeBeginning();
    }

    public Hashtable getClientExtensions() throws IOException {
        step("building ClientHello");
        return super.getClientExtensions();
    }

    public void notifyServerVersion(ProtocolVersion v) throws IOException {
        step("ServerHello received");
        super.notifyServerVersion(v);
    }

    public Vector getClientSupplementalData() throws IOException {
        step("ServerHelloDone (key exchange signature verified)");
        return super.getClientSupplementalData();
    }

    public void notifyHandshakeComplete() throws IOException {
        step("handshake complete");
        super.notifyHandshakeComplete();
    }

    public ProtocolVersion[] getProtocolVersions() {
        return new ProtocolVersion[] { ProtocolVersion.TLSv12 };
    }

    protected int[] getSupportedCipherSuites() {
        int[] suites;
        if (CIPHER_CHACHA.equals(cipher)) {
            suites = new int[] { CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256 };
        } else if (CIPHER_AES128.equals(cipher)) {
            suites = new int[] { CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 };
        } else if (CIPHER_AES256.equals(cipher)) {
            suites = new int[] { CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 };
        } else {
            suites = new int[] { CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 };
        }
        return TlsUtils.getSupportedCipherSuites(getCrypto(), suites);
    }

    protected Vector getSupportedGroups(Vector namedGroupRoles) {
        Vector groups = new Vector();
        if (!GROUP_P256.equals(group)) {
            groups.addElement(new Integer(NamedGroup.x25519));
        }
        if (!GROUP_X25519.equals(group)) {
            groups.addElement(new Integer(NamedGroup.secp256r1));
        }
        return groups;
    }

    protected Vector getSNIServerNames() {
        if (isIpLiteral(host)) {
            return null;
        }
        Vector names = new Vector();
        names.addElement(new ServerName(NameType.host_name, Strings.toByteArray(host)));
        return names;
    }

    public TlsSession getSessionToResume() {
        return resume;
    }

    public TlsAuthentication getAuthentication() throws IOException {
        return new TlsAuthentication() {
            public void notifyServerCertificate(TlsServerCertificate serverCertificate) throws IOException {
                step("Certificate received");
                Certificate chain = serverCertificate.getCertificate();
                if (chain == null || chain.isEmpty()) {
                    throw new TlsFatalAlert(AlertDescription.bad_certificate);
                }
                byte[] leaf = chain.getCertificateAt(0).getEncoded();
                observedPin = pinOf(leaf);
                if (captureOnly) {
                    throw new TlsFatalAlert(AlertDescription.user_canceled);
                }
                if (enforcePins && !pins.contains(observedPin)) {
                    throw new TlsFatalAlert(AlertDescription.bad_certificate);
                }
                step("pin checked");
                if (enforcePins && preValidate && preValidateRsa(leaf)) {
                    step("pinned RSA key marked validated");
                }
            }

            public TlsCredentials getClientCredentials(CertificateRequest request) {
                return null;
            }
        };
    }

    /** Base64 SHA-256 of the certificate's SubjectPublicKeyInfo (curl's sha256// form). */
    static String pinOf(byte[] leafDer) throws IOException {
        org.bouncycastle.asn1.x509.Certificate cert = org.bouncycastle.asn1.x509.Certificate.getInstance(leafDer);
        byte[] spki = cert.getSubjectPublicKeyInfo().getEncoded();
        SHA256Digest d = new SHA256Digest();
        d.update(spki, 0, spki.length);
        byte[] h = new byte[32];
        d.doFinal(h, 0);
        return Strings.fromByteArray(Base64.encode(h));
    }

    /**
     * Marks the pinned RSA modulus as validated so BC skips its Miller-Rabin
     * check (about 10 s on a Pentium III with Java 5). Safe only because the
     * key has just matched a pin. See PinnedTlsClient in the core module.
     */
    private static boolean preValidateRsa(byte[] leafDer) {
        org.bouncycastle.asn1.x509.Certificate cert = org.bouncycastle.asn1.x509.Certificate.getInstance(leafDer);
        SubjectPublicKeyInfo spki = cert.getSubjectPublicKeyInfo();
        if (!PKCSObjectIdentifiers.rsaEncryption.equals(spki.getAlgorithm().getAlgorithm())) {
            return false;
        }
        try {
            RSAPublicKey key = RSAPublicKey.getInstance(spki.parsePublicKey());
            PRE_VALIDATED.addElement(new RSAKeyParameters(false, key.getModulus(), key.getPublicExponent(), true));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    String getObservedPin() {
        return observedPin;
    }

    boolean pinMismatch() {
        return observedPin != null && !pins.contains(observedPin);
    }

    TlsSession getResumableSession() {
        return context == null ? null : context.getResumableSession();
    }

    boolean wasResumed() {
        return context.getSecurityParametersConnection().isResumedSession();
    }

    String describe() {
        SecurityParameters sp = context.getSecurityParametersConnection();
        StringBuffer sb = new StringBuffer();
        sb.append(sp.getNegotiatedVersion()).append(", ").append(suiteName(sp.getCipherSuite()));
        int g = sp.getNegotiatedGroup();
        if (g >= 0) {
            sb.append(", ").append(NamedGroup.getName(g));
        }
        return sb.toString();
    }

    static String suiteName(int suite) {
        switch (suite) {
        case CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256:
            return "ECDHE-ECDSA-CHACHA20-POLY1305";
        case CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256:
            return "ECDHE-RSA-CHACHA20-POLY1305";
        case CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256:
            return "ECDHE-ECDSA-AES128-GCM-SHA256";
        case CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256:
            return "ECDHE-RSA-AES128-GCM-SHA256";
        case CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384:
            return "ECDHE-ECDSA-AES256-GCM-SHA384";
        case CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384:
            return "ECDHE-RSA-AES256-GCM-SHA384";
        default:
            return "suite 0x" + Integer.toHexString(suite);
        }
    }

    static boolean isIpLiteral(String h) {
        if (h.indexOf(':') >= 0) {
            return true;
        }
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (c != '.' && (c < '0' || c > '9')) {
                return false;
            }
        }
        return true;
    }
}
