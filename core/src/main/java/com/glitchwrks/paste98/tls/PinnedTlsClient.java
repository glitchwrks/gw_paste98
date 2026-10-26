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
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
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

/**
 * TLS client that authenticates the server only by SHA-256 SPKI pin on the
 * leaf certificate. No CA chain, hostname or expiry checks are done: the pin
 * is the trust anchor, which sidesteps Java 5's outdated cacerts entirely.
 *
 * Bouncy Castle still verifies the server's handshake signature against the
 * pinned key, so a party without the private key cannot complete the
 * handshake even if it replays the real certificate.
 */
public final class PinnedTlsClient extends DefaultTlsClient {

    /**
     * Strong references to moduli marked as validated. BC's cache of validated
     * moduli is a WeakHashMap, so without these the entries could be collected.
     */
    private static final List PRE_VALIDATED = new ArrayList();

    private final TlsOptions options;
    private final boolean enforcePins;
    private final TlsSession sessionToResume;
    private HandshakeListener listener;

    private String observedPin;
    private String observedCert;
    private boolean captureOnly;
    private byte[] capturedLeaf;
    private int capturedChainLength;

    /**
     * @param enforcePins true for every real connection; false only to display
     *        the server's pin, in which case no data must be sent
     * @param sessionToResume a session from an earlier connection, or null
     */
    public PinnedTlsClient(TlsCrypto crypto, TlsOptions options, boolean enforcePins, TlsSession sessionToResume) {
        super(crypto);
        this.options = options;
        this.enforcePins = enforcePins;
        this.sessionToResume = sessionToResume;
    }

    /**
     * Stop the handshake as soon as the server's certificate arrives, keeping
     * a copy of it. Used only to show a person the server's key; requires
     * enforcePins false. Nothing past the certificate is processed: no key
     * exchange, no signature check, and no data can be sent.
     */
    void setCaptureOnly(boolean captureOnly) {
        if (captureOnly && enforcePins) {
            throw new IllegalStateException("capture-only mode cannot enforce pins");
        }
        this.captureOnly = captureOnly;
    }

    /** The leaf certificate captured in capture-only mode, or null. */
    byte[] getCapturedLeaf() {
        return capturedLeaf;
    }

    int getCapturedChainLength() {
        return capturedChainLength;
    }

    public void setListener(HandshakeListener listener) {
        this.listener = listener;
    }

    private void step(String description) {
        if (listener != null) {
            listener.step(description);
        }
    }

    // ---- handshake progress (for listeners) ------------------------------------

    public void notifyHandshakeBeginning() throws IOException {
        step("handshake begins (context set up)");
        super.notifyHandshakeBeginning();
    }

    public Hashtable getClientExtensions() throws IOException {
        step("building ClientHello");
        return super.getClientExtensions();
    }

    public void notifyServerVersion(ProtocolVersion serverVersion) throws IOException {
        step("ServerHello received");
        super.notifyServerVersion(serverVersion);
    }

    public Vector getClientSupplementalData() throws IOException {
        step("ServerHelloDone received (ServerKeyExchange signature verified)");
        return super.getClientSupplementalData();
    }

    public void notifyHandshakeComplete() throws IOException {
        step("handshake complete (client key exchange, Finished exchanged)");
        super.notifyHandshakeComplete();
    }

    // ---- protocol parameters -------------------------------------------------

    public ProtocolVersion[] getProtocolVersions() {
        if (options.isOfferTls13()) {
            return ProtocolVersion.TLSv13.downTo(ProtocolVersion.TLSv12);
        }
        return new ProtocolVersion[] { ProtocolVersion.TLSv12 };
    }

    protected int[] getSupportedCipherSuites() {
        String cipher = options.getCipher();
        int[] tls12;
        int[] tls13;
        if (TlsOptions.CIPHER_CHACHA.equals(cipher)) {
            tls12 = new int[] {
                CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256 };
            tls13 = new int[] { CipherSuite.TLS_CHACHA20_POLY1305_SHA256 };
        } else if (TlsOptions.CIPHER_AES128.equals(cipher)) {
            tls12 = new int[] {
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 };
            tls13 = new int[] { CipherSuite.TLS_AES_128_GCM_SHA256 };
        } else if (TlsOptions.CIPHER_AES256.equals(cipher)) {
            tls12 = new int[] {
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 };
            tls13 = new int[] { CipherSuite.TLS_AES_256_GCM_SHA384 };
        } else {
            // ChaCha20-Poly1305 first: faster than AES-GCM in pure software on
            // CPUs without AES instructions. Forward-secret AEAD suites only.
            tls12 = new int[] {
                CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 };
            tls13 = new int[] {
                CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_AES_128_GCM_SHA256,
                CipherSuite.TLS_AES_256_GCM_SHA384 };
        }
        int[] all;
        if (options.isOfferTls13()) {
            all = new int[tls13.length + tls12.length];
            System.arraycopy(tls13, 0, all, 0, tls13.length);
            System.arraycopy(tls12, 0, all, tls13.length, tls12.length);
        } else {
            all = tls12;
        }
        return TlsUtils.getSupportedCipherSuites(getCrypto(), all);
    }

    protected Vector getSupportedGroups(Vector namedGroupRoles) {
        String group = options.getGroup();
        Vector groups = new Vector();
        if (TlsOptions.GROUP_X25519.equals(group)) {
            groups.addElement(new Integer(NamedGroup.x25519));
        } else if (TlsOptions.GROUP_P256.equals(group)) {
            groups.addElement(new Integer(NamedGroup.secp256r1));
        } else {
            groups.addElement(new Integer(NamedGroup.x25519));
            groups.addElement(new Integer(NamedGroup.secp256r1));
        }
        return groups;
    }

    protected Vector getSNIServerNames() {
        String host = options.getHost();
        if (isIpLiteral(host)) {
            return null; // SNI must not carry IP addresses
        }
        Vector names = new Vector();
        names.addElement(new ServerName(NameType.host_name, Strings.toByteArray(host)));
        return names;
    }

    public TlsSession getSessionToResume() {
        return sessionToResume;
    }

    // ---- authentication ------------------------------------------------------

    public TlsAuthentication getAuthentication() throws IOException {
        return new TlsAuthentication() {
            public void notifyServerCertificate(TlsServerCertificate serverCertificate) throws IOException {
                step("Certificate received");
                Certificate chain = serverCertificate.getCertificate();
                if (chain == null || chain.isEmpty()) {
                    throw new TlsFatalAlert(AlertDescription.bad_certificate);
                }
                byte[] leaf = chain.getCertificateAt(0).getEncoded();
                observedPin = SpkiPin.fromCertificate(leaf);
                observedCert = SpkiPin.describe(leaf);
                if (captureOnly) {
                    capturedLeaf = leaf;
                    capturedChainLength = chain.getLength();
                    // Deliberately end the handshake here: we only wanted the certificate.
                    throw new TlsFatalAlert(AlertDescription.user_canceled);
                }
                if (enforcePins && !options.getPins().contains(observedPin)) {
                    throw new TlsFatalAlert(AlertDescription.bad_certificate);
                }
                step("pin checked");
                if (enforcePins && options.isPreValidatePinnedKey() && preValidatePinnedRsaKey(leaf)) {
                    step("pinned RSA key marked as validated");
                }
            }

            public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) {
                return null; // no client certificate; the API uses HTTP Basic Auth
            }
        };
    }

    /**
     * Bouncy Castle checks every new RSA public modulus with Miller-Rabin
     * rounds (4 for RSA-2048) to prove it is composite, caching the result for
     * the life of the JVM. Each round is a full-size modular exponentiation:
     * cheap on modern hardware, about 10 seconds in total on a Pentium III
     * with Java 5's BigInteger.
     *
     * The test exists to reject malformed keys, such as a prime "modulus" that
     * would make signatures forgeable. Here the key has just matched a SHA-256
     * pin of the server's real key, so it is already known to be a properly
     * generated key. Constructing RSAKeyParameters with isInternal=true adds
     * the modulus to BC's validated cache without running the test, so the
     * check BC does later for the ServerKeyExchange signature finds it cached.
     *
     * Only ever called after the pin has matched.
     *
     * @return true if the leaf key is RSA and was marked as validated
     */
    private static boolean preValidatePinnedRsaKey(byte[] leafDer) {
        org.bouncycastle.asn1.x509.Certificate cert = org.bouncycastle.asn1.x509.Certificate.getInstance(leafDer);
        SubjectPublicKeyInfo spki = cert.getSubjectPublicKeyInfo();
        if (!PKCSObjectIdentifiers.rsaEncryption.equals(spki.getAlgorithm().getAlgorithm())) {
            return false;
        }
        RSAPublicKey key;
        try {
            key = RSAPublicKey.getInstance(spki.parsePublicKey());
        } catch (IOException e) {
            return false; // leave it to BC's normal processing
        }
        synchronized (PRE_VALIDATED) {
            PRE_VALIDATED.add(new RSAKeyParameters(false, key.getModulus(), key.getPublicExponent(), true));
        }
        return true;
    }

    // ---- results -------------------------------------------------------------

    /** SPKI pin of the server's leaf certificate, or null if none was received. */
    public String getObservedPin() {
        return observedPin;
    }

    public String getObservedCertificate() {
        return observedCert;
    }

    /** True if the leaf key was received and did not match any configured pin. */
    public boolean pinMismatch() {
        return observedPin != null && !options.getPins().contains(observedPin);
    }

    public TlsSession getResumableSession() {
        return context == null ? null : context.getResumableSession();
    }

    public String describeConnection() {
        SecurityParameters sp = context.getSecurityParametersConnection();
        StringBuffer sb = new StringBuffer();
        sb.append(sp.getNegotiatedVersion()).append(", ");
        sb.append(suiteName(sp.getCipherSuite()));
        int group = sp.getNegotiatedGroup();
        if (group >= 0) {
            sb.append(", ").append(NamedGroup.getName(group));
        }
        return sb.toString();
    }

    public boolean wasResumed() {
        return context.getSecurityParametersConnection().isResumedSession();
    }

    /** Names for the suites this client offers; BC has no name lookup for cipher suites. */
    public static String suiteName(int suite) {
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
        case CipherSuite.TLS_CHACHA20_POLY1305_SHA256:
            return "TLS_CHACHA20_POLY1305_SHA256";
        case CipherSuite.TLS_AES_128_GCM_SHA256:
            return "TLS_AES_128_GCM_SHA256";
        case CipherSuite.TLS_AES_256_GCM_SHA384:
            return "TLS_AES_256_GCM_SHA384";
        default:
            return "suite 0x" + Integer.toHexString(suite);
        }
    }

    static boolean isIpLiteral(String h) {
        if (h.indexOf(':') >= 0) {
            return true; // IPv6
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
