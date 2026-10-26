/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.bench;

import java.io.PrintStream;
import java.math.BigInteger;
import java.security.SecureRandom;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.modes.AEADCipher;
import org.bouncycastle.crypto.modes.ChaCha20Poly1305;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.crypto.signers.RSADigestSigner;
import org.bouncycastle.util.encoders.Hex;

/**
 * Offline timings of the primitives a TLS 1.2 handshake and record layer use.
 * Needs no network, so it can be run on the Win98 box before any server setup.
 */
final class CryptoBench {

    /** Work interface; Java 5 has no lambdas. */
    interface Op {
        void run() throws Exception;
    }

    /** Fixed RSA-2048 public key (e = 65537) and a PKCS#1 v1.5 SHA-256 signature by it. */
    private static final String RSA_MODULUS_HEX =
        "AA6F4C071953EFF298D252470C991A0D1FA014FD7EF846E4565CB4ADC3A7A647"
      + "BA65CA37F03B9FD2B7EFE34B0386B3986781B552A32649058FA34951418B8478"
      + "D0C148B690F52F753535E9411FC97948E11216592F86AF15EDBC7DBCFEB1F7A5"
      + "871D24116A2240D08545444C8D81E401F5BE891FA307761B15B35DAF400C6776"
      + "AFCDC258BF9EA7E2F818BC584CA5A381E12DEA6C7D990DEB61619812A052069A"
      + "0BF2EEA6E85FA56459380B36CAED7F8412B9326BAB5193DEA0DBAB6575DBA582"
      + "07355F5FDAAFD5B15D8140987BBF2BA4EE912374F064D7236578B478D587A3E8"
      + "1894194B52D3DDFB17527BEF0D2CAA2A59489A1E43E83F15D004CD0018E2B1D7";
    private static final String RSA_SIG_HEX =
        "82b7291f64a641a7d01213e4046ee523d22a8e02a9837b514d80c125a0aa333b"
      + "f7aac92283bbe48c48be2741cd05206640bf012f0c0d39ccaf740f703199d0dd"
      + "a28135e04bd1623d40a91c0e267a554c6d0a49adad0b33065c9a33a67e974ad7"
      + "077a0f2b8c9bb838432b370b312f182570cba8ed098f98e8b1c70eec5790a592"
      + "866de81e6c3721db2dd628b3493168e24733f685dde5c31829a521a3d30726f6"
      + "6280fa9c339179258740fa06ad62927f3221a14dd1676df037187ed91b93f8df"
      + "38c3ec876c11321f51e143f2b0018726ab593f1f26b0dd0630abfb6ae912b07d"
      + "5a2eee5833a7f89cd0639a44ebc060cb9230f960579f17b8dc908d14baef6803";
    private static final byte[] RSA_MSG = asciiBytes("pastebench rsa verify vector");

    /** One TLS record's worth of plaintext. */
    private static final int BULK_CHUNK = 16384;

    private final PrintStream out;
    private final SecureRandom random;
    private final long budgetMs;

    CryptoBench(PrintStream out, SecureRandom random, long budgetMs) {
        this.out = out;
        this.random = random;
        this.budgetMs = budgetMs;
    }

    void runAll() throws Exception {
        out.println();
        out.println("== Offline crypto (first call includes class loading; avg is steady state)");

        // --- key exchange -----------------------------------------------------
        final X25519KeyPairGenerator xGen = new X25519KeyPairGenerator();
        xGen.init(new X25519KeyGenerationParameters(random));
        final AsymmetricCipherKeyPair xPeer = xGen.generateKeyPair();
        timeOp("X25519 keygen + shared secret (client side of ECDHE)", new Op() {
            public void run() {
                AsymmetricCipherKeyPair kp = xGen.generateKeyPair();
                X25519Agreement a = new X25519Agreement();
                a.init(kp.getPrivate());
                byte[] secret = new byte[a.getAgreementSize()];
                a.calculateAgreement(xPeer.getPublic(), secret, 0);
            }
        });

        X9ECParameters p256 = CustomNamedCurves.getByName("secp256r1");
        final ECDomainParameters dom = new ECDomainParameters(p256.getCurve(), p256.getG(), p256.getN(),
                p256.getH(), p256.getSeed());
        final ECKeyPairGenerator ecGen = new ECKeyPairGenerator();
        ecGen.init(new ECKeyGenerationParameters(dom, random));
        final AsymmetricCipherKeyPair ecPeer = ecGen.generateKeyPair();
        timeOp("P-256 keygen + shared secret (client side of ECDHE)", new Op() {
            public void run() {
                AsymmetricCipherKeyPair kp = ecGen.generateKeyPair();
                ECDHBasicAgreement a = new ECDHBasicAgreement();
                a.init(kp.getPrivate());
                a.calculateAgreement(ecPeer.getPublic());
            }
        });

        // --- server signature verification ------------------------------------
        final BigInteger n = new BigInteger(RSA_MODULUS_HEX, 16);
        // BC proves every new RSA modulus composite (Miller-Rabin) once per JVM.
        // This is a one-off cost per key, so time it once rather than averaging.
        long tv = System.nanoTime();
        final RSAKeyParameters rsaPub = new RSAKeyParameters(false, n, BigInteger.valueOf(65537));
        out.println(String.format("  %-52s once  %9.2f ms   (BC, first use of each RSA key per JVM)",
                "RSA-2048 public key validation", Double.valueOf((System.nanoTime() - tv) / 1e6)));
        final byte[] rsaSig = Hex.decode(RSA_SIG_HEX);
        timeOp("RSA-2048 signature verify (RSA server cert)", new Op() {
            public void run() {
                RSADigestSigner v = new RSADigestSigner(new SHA256Digest());
                v.init(false, rsaPub);
                v.update(RSA_MSG, 0, RSA_MSG.length);
                if (!v.verifySignature(rsaSig)) {
                    throw new IllegalStateException("RSA test vector failed to verify");
                }
            }
        });

        final byte[] hash = sha256(RSA_MSG);
        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        signer.init(true, new ParametersWithRandom(ecPeer.getPrivate(), random));
        final BigInteger[] ecSig = signer.generateSignature(hash);
        final ECPublicKeyParameters ecPub = (ECPublicKeyParameters) ecPeer.getPublic();
        timeOp("ECDSA P-256 signature verify (ECDSA server cert)", new Op() {
            public void run() {
                ECDSASigner v = new ECDSASigner();
                v.init(false, ecPub);
                if (!v.verifySignature(hash, ecSig[0], ecSig[1])) {
                    throw new IllegalStateException("ECDSA self-test failed to verify");
                }
            }
        });

        // --- record layer -----------------------------------------------------
        final byte[] buf = new byte[BULK_CHUNK];
        final byte[] ct = new byte[BULK_CHUNK + 16];
        random.nextBytes(buf);

        timeThroughput("SHA-256", new Op() {
            public void run() {
                SHA256Digest d = new SHA256Digest();
                d.update(buf, 0, buf.length);
                d.doFinal(ct, 0);
            }
        });

        final byte[] key16 = new byte[16];
        final byte[] key32 = new byte[32];
        random.nextBytes(key16);
        random.nextBytes(key32);
        final AEADCipher gcm = GCMBlockCipher.newInstance(AESEngine.newInstance());
        final byte[] gcmNonce = new byte[12];
        timeThroughput("AES-128-GCM encrypt", new Op() {
            public void run() throws Exception {
                bump(gcmNonce);
                gcm.init(true, new AEADParameters(new KeyParameter(key16), 128, gcmNonce));
                int len = gcm.processBytes(buf, 0, buf.length, ct, 0);
                gcm.doFinal(ct, len);
            }
        });

        final ChaCha20Poly1305 chacha = new ChaCha20Poly1305();
        final byte[] ccNonce = new byte[12];
        timeThroughput("ChaCha20-Poly1305 encrypt", new Op() {
            public void run() throws Exception {
                bump(ccNonce);
                chacha.init(true, new AEADParameters(new KeyParameter(key32), 128, ccNonce));
                int len = chacha.processBytes(buf, 0, buf.length, ct, 0);
                chacha.doFinal(ct, len);
            }
        });
    }

    /** Times one cold call, then repeats until the time budget is used. */
    void timeOp(String label, Op op) throws Exception {
        long t0 = System.nanoTime();
        op.run();
        double firstMs = (System.nanoTime() - t0) / 1e6;

        int iters = 0;
        long start = System.nanoTime();
        long deadline = start + budgetMs * 1000000L;
        long now;
        do {
            op.run();
            iters++;
            now = System.nanoTime();
        } while (now < deadline && iters < 100000);
        double avgMs = (now - start) / 1e6 / iters;

        out.println(String.format("  %-52s first %9.2f ms   avg %9.3f ms  (n=%d)", label, Double.valueOf(firstMs),
                Double.valueOf(avgMs), Integer.valueOf(iters)));
    }

    /** Runs a 16 KB operation repeatedly and reports MB/s. */
    void timeThroughput(String label, Op op) throws Exception {
        op.run(); // warm-up / class loading
        long bytes = 0;
        long start = System.nanoTime();
        long deadline = start + budgetMs * 1000000L;
        long now;
        do {
            op.run();
            bytes += BULK_CHUNK;
            now = System.nanoTime();
        } while (now < deadline);
        double secs = (now - start) / 1e9;
        double mbps = bytes / secs / (1024.0 * 1024.0);
        double msPer16k = secs * 1000.0 / (bytes / BULK_CHUNK);
        out.println(String.format("  %-52s %9.2f MB/s   (%.3f ms per 16 KB record)", label, Double.valueOf(mbps),
                Double.valueOf(msPer16k)));
    }

    private static void bump(byte[] nonce) {
        for (int i = nonce.length - 1; i >= 0; i--) {
            if (++nonce[i] != 0) {
                return;
            }
        }
    }

    private static byte[] sha256(byte[] in) {
        SHA256Digest d = new SHA256Digest();
        d.update(in, 0, in.length);
        byte[] o = new byte[32];
        d.doFinal(o, 0);
        return o;
    }

    static byte[] asciiBytes(String s) {
        byte[] b = new byte[s.length()];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) s.charAt(i);
        }
        return b;
    }
}
