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
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;

/**
 * What a server presented, as seen by {@link PinFetcher}: the SPKI pin of its
 * key and the certificate details a person needs to judge it. None of this is
 * verified: it is whatever the server that answered sent.
 */
public final class ServerKeyInfo {

    private final String host;
    private final int port;
    private final String pin;
    private final String keyDescription;
    private final String subject;
    private final String commonName;
    private final List dnsNames;
    private final List ipAddresses;
    private final String issuer;
    private final Date notBefore;
    private final Date notAfter;
    private final int chainLength;

    private ServerKeyInfo(String host, int port, String pin, String keyDescription, String subject,
            String commonName, List dnsNames, List ipAddresses, String issuer, Date notBefore,
            Date notAfter, int chainLength) {
        this.host = host;
        this.port = port;
        this.pin = pin;
        this.keyDescription = keyDescription;
        this.subject = subject;
        this.commonName = commonName;
        this.dnsNames = dnsNames;
        this.ipAddresses = ipAddresses;
        this.issuer = issuer;
        this.notBefore = notBefore;
        this.notAfter = notAfter;
        this.chainLength = chainLength;
    }

    /**
     * Summarises a DER-encoded leaf certificate.
     *
     * @throws IOException if it is not a parseable X.509 certificate
     */
    public static ServerKeyInfo fromCertificate(byte[] leafDer, String host, int port, int chainLength)
            throws IOException {
        Certificate cert;
        try {
            cert = Certificate.getInstance(leafDer);
        } catch (RuntimeException e) {
            throw new IOException("the server's certificate could not be read: " + e.getMessage());
        }
        String pin = SpkiPin.fromCertificate(leafDer);

        List dns = new ArrayList();
        List ips = new ArrayList();
        Extensions ext = cert.getTBSCertificate().getExtensions();
        GeneralNames san = ext == null ? null : GeneralNames.fromExtensions(ext, Extension.subjectAlternativeName);
        if (san != null) {
            GeneralName[] names = san.getNames();
            for (int i = 0; i < names.length; i++) {
                if (names[i].getTagNo() == GeneralName.dNSName) {
                    dns.add(names[i].getName().toString());
                } else if (names[i].getTagNo() == GeneralName.iPAddress) {
                    ips.add(ipToString(ASN1OctetString.getInstance(names[i].getName()).getOctets()));
                }
            }
        }

        return new ServerKeyInfo(host, port, pin, describeKey(cert.getSubjectPublicKeyInfo()),
                cert.getSubject().toString(), commonName(cert.getSubject()), Collections.unmodifiableList(dns),
                Collections.unmodifiableList(ips), cert.getIssuer().toString(), cert.getStartDate().getDate(),
                cert.getEndDate().getDate(), chainLength);
    }

    /** E.g. "RSA 2048-bit", "EC P-256", "Ed25519". */
    static String describeKey(SubjectPublicKeyInfo spki) {
        ASN1ObjectIdentifier alg = spki.getAlgorithm().getAlgorithm();
        try {
            if (PKCSObjectIdentifiers.rsaEncryption.equals(alg)) {
                RSAPublicKey k = RSAPublicKey.getInstance(spki.parsePublicKey());
                return "RSA " + k.getModulus().bitLength() + "-bit";
            }
            if (X9ObjectIdentifiers.id_ecPublicKey.equals(alg)) {
                Object params = spki.getAlgorithm().getParameters();
                if (params instanceof ASN1ObjectIdentifier) {
                    String name = ECNamedCurveTable.getName((ASN1ObjectIdentifier) params);
                    if (name != null) {
                        return "EC " + (name.equals("prime256v1") || name.equals("secp256r1") ? "P-256"
                                : name.equals("secp384r1") ? "P-384" : name.equals("secp521r1") ? "P-521" : name);
                    }
                }
                return "EC (unnamed curve)";
            }
        } catch (IOException e) {
            // fall through to the OID
        } catch (RuntimeException e) {
            // fall through to the OID
        }
        if ("1.3.101.112".equals(alg.getId())) {
            return "Ed25519";
        }
        if ("1.3.101.113".equals(alg.getId())) {
            return "Ed448";
        }
        return "algorithm " + alg.getId();
    }

    private static String commonName(X500Name name) {
        RDN[] cns = name.getRDNs(BCStyle.CN);
        return cns.length == 0 || cns[0].getFirst() == null ? null : IETFUtils.valueToString(cns[0].getFirst().getValue());
    }

    private static String ipToString(byte[] b) {
        if (b.length == 4) {
            return (b[0] & 0xff) + "." + (b[1] & 0xff) + "." + (b[2] & 0xff) + "." + (b[3] & 0xff);
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i + 1 < b.length; i += 2) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(Integer.toHexString(((b[i] & 0xff) << 8) | (b[i + 1] & 0xff)));
        }
        return sb.toString();
    }

    /**
     * True if the certificate names the host we connected to: a DNS name
     * (with a single leading "*." wildcard label allowed) or IP address in
     * the subject alternative names, or the common name if there are none.
     * This is a sanity check for the person reading the details, not a trust
     * decision: the pin is what the client trusts.
     */
    public boolean namesHost() {
        String h = host.toLowerCase();
        if (PinnedTlsClient.isIpLiteral(host)) {
            return ipAddresses.contains(host);
        }
        List names = dnsNames;
        if (names.isEmpty() && commonName != null) {
            names = Collections.singletonList(commonName);
        }
        for (int i = 0; i < names.size(); i++) {
            if (hostMatches(((String) names.get(i)).toLowerCase(), h)) {
                return true;
            }
        }
        return false;
    }

    static boolean hostMatches(String pattern, String host) {
        if (pattern.startsWith("*.")) {
            int dot = host.indexOf('.');
            return dot > 0 && host.substring(dot + 1).equals(pattern.substring(2)) && pattern.length() > 2;
        }
        return pattern.equals(host);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    /** The SHA-256 SPKI pin, base64. */
    public String getPin() {
        return pin;
    }

    public String getKeyDescription() {
        return keyDescription;
    }

    public String getSubject() {
        return subject;
    }

    /** DNS names from the subject alternative names, then IP addresses; or the CN if there are none. */
    public List getNames() {
        List l = new ArrayList(dnsNames);
        l.addAll(ipAddresses);
        if (l.isEmpty() && commonName != null) {
            l.add(commonName);
        }
        return l;
    }

    public String getIssuer() {
        return issuer;
    }

    public Date getNotBefore() {
        return notBefore;
    }

    public Date getNotAfter() {
        return notAfter;
    }

    /** Certificates the server sent (leaf first). */
    public int getChainLength() {
        return chainLength;
    }
}
