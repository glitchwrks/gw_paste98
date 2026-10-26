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

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.StringTokenizer;
import java.util.Vector;

import org.bouncycastle.tls.TlsClientProtocol;
import org.bouncycastle.tls.TlsSession;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

/**
 * pastebench13: can a Java 1.3 runtime (the newest for Windows 95) do the
 * paste client's pinned TLS 1.2 handshake, and how fast? Same measurements
 * and options as the Java 5 pastebench where they apply, plus the cost of
 * Java 1.3's own SecureRandom seeding. Runs on any Java from 1.3 up, so the
 * same jar can be timed on Java 1.3 and Java 5 on one machine.
 *
 * Written in Java 1.3: no generics, no StringBuilder, no 1.4+ APIs. Bouncy
 * Castle's Java 1.4 build is adapted for 1.3 at build time (Retarget13).
 */
public final class Bench13 {

    static final String VERSION = "0.1.2";

    private String host = "pastes.glitchwrks.com";
    private int port = 443;
    private final Vector pins = new Vector();
    private int fullCount = 3;
    private int resumeCount = 3;
    private String getPath;
    private String cipher = PinnedClient13.CIPHER_AUTO;
    private String group = PinnedClient13.GROUP_AUTO;
    private boolean showPin;
    private boolean offline;
    private boolean skipCrypto;
    private boolean fastSeed;
    private boolean preValidate = true;
    private long budgetMs = 2000;
    private int timeoutMs = 30000;

    private final PrintStream out = System.out;
    private int failures;
    private BcTlsCrypto crypto;

    public static void main(String[] args) {
        long mainEntered = System.currentTimeMillis();
        Bench13 b = new Bench13();
        try {
            b.parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            usage(System.err);
            System.exit(2);
        } catch (IOException e) {
            System.err.println("error: " + e);
            System.exit(2);
        }
        int rc;
        try {
            rc = b.run(mainEntered);
        } catch (Throwable t) {
            System.out.println();
            System.out.println("FATAL: " + t);
            t.printStackTrace(System.out);
            rc = 3;
        }
        System.exit(rc);
    }

    static void usage(PrintStream p) {
        p.println("usage: java -jar PB13.JAR [options] [@optionsfile]");
        p.println("  --host H --port N        server (default pastes.glitchwrks.com 443)");
        p.println("  --pin BASE64             SHA-256 pin of the server's key (repeatable; required for network tests)");
        p.println("  --full N --resume N      full and resumed handshakes (default 3 and 3)");
        p.println("  --get PATH               also send GET PATH over each connection");
        p.println("  --cipher auto|chacha|aes128|aes256   --group auto|x25519|p256");
        p.println("  --showpin                print the server's pin (not verified!) and stop");
        p.println("  --offline                no network tests    --nocrypto   no offline crypto tests");
        p.println("  --fastseed               seed SecureRandom from timing jitter instead of Java's own seeding");
        p.println("  --nopreval               let BC validate the pinned RSA key (slow; for comparison)");
        p.println("  --budget MS              time per offline test (default 2000)");
        p.println("  --timeout MS             socket read timeout (default 30000)");
    }

    void parseArgs(String[] args) throws IOException {
        Vector all = new Vector();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("@")) {
                readOptionsFile(args[i].substring(1), all);
            } else {
                all.addElement(args[i]);
            }
        }
        for (int i = 0; i < all.size(); i++) {
            String a = (String) all.elementAt(i);
            if (a.equals("--host")) {
                host = value(all, ++i, a);
            } else if (a.equals("--port")) {
                port = number(value(all, ++i, a), a);
            } else if (a.equals("--pin")) {
                pins.addElement(value(all, ++i, a));
            } else if (a.equals("--full")) {
                fullCount = number(value(all, ++i, a), a);
            } else if (a.equals("--resume")) {
                resumeCount = number(value(all, ++i, a), a);
            } else if (a.equals("--get")) {
                getPath = value(all, ++i, a);
            } else if (a.equals("--cipher")) {
                cipher = value(all, ++i, a);
            } else if (a.equals("--group")) {
                group = value(all, ++i, a);
            } else if (a.equals("--budget")) {
                budgetMs = number(value(all, ++i, a), a);
            } else if (a.equals("--timeout")) {
                timeoutMs = number(value(all, ++i, a), a);
            } else if (a.equals("--showpin")) {
                showPin = true;
            } else if (a.equals("--offline")) {
                offline = true;
            } else if (a.equals("--nocrypto")) {
                skipCrypto = true;
            } else if (a.equals("--fastseed")) {
                fastSeed = true;
            } else if (a.equals("--nopreval")) {
                preValidate = false;
            } else if (a.equals("--help") || a.equals("-h")) {
                usage(System.out);
                System.exit(0);
            } else if (a.equals("--tls13")) {
                throw new IllegalArgumentException("--tls13 is not offered by pastebench13 (TLS 1.2 only)");
            } else {
                throw new IllegalArgumentException("unknown option " + a);
            }
        }
        if (!offline && !showPin && pins.isEmpty()) {
            throw new IllegalArgumentException("network tests need --pin (or use --offline, or --showpin)");
        }
        for (int i = 0; i < pins.size(); i++) {
            String p = (String) pins.elementAt(i);
            if (p.length() != 44 || !p.endsWith("=")) {
                throw new IllegalArgumentException("not a base64 SHA-256 pin (44 characters ending in '='): " + p);
            }
        }
    }

    private static void readOptionsFile(String name, Vector into) throws IOException {
        BufferedReader r = new BufferedReader(new FileReader(name));
        try {
            String line;
            while ((line = r.readLine()) != null) {
                int hash = line.indexOf('#');
                if (hash >= 0) {
                    line = line.substring(0, hash);
                }
                StringTokenizer t = new StringTokenizer(line);
                while (t.hasMoreTokens()) {
                    into.addElement(t.nextToken());
                }
            }
        } finally {
            r.close();
        }
    }

    private static String value(Vector all, int i, String opt) {
        if (i >= all.size()) {
            throw new IllegalArgumentException(opt + " needs a value");
        }
        return (String) all.elementAt(i);
    }

    private static int number(String s, String opt) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(opt + ": not a number: " + s);
        }
    }

    int run(long mainEntered) throws Exception {
        out.println("pastebench13 " + VERSION + " - pinned TLS 1.2 on Java 1.3 and later");
        out.println("Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + "), "
                + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
        out.println(System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                + System.getProperty("os.arch") + ", heap now " + (Runtime.getRuntime().totalMemory() / 1024)
                + " KB");
        out.println("Timer: System.currentTimeMillis (about 55 ms steps on Windows 9x)");

        out.println();
        out.println("== Startup");
        long t = System.currentTimeMillis();
        Class.forName("org.bouncycastle.tls.TlsClientProtocol");
        Class.forName("org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto");
        line("Load TLS classes", System.currentTimeMillis() - t, null);

        SecureRandom random;
        t = System.currentTimeMillis();
        byte[] jitter = jitterSeed(32);
        long jitterMs = System.currentTimeMillis() - t;
        t = System.currentTimeMillis();
        SecureRandom fast = new SecureRandom(jitter);
        fast.nextBytes(new byte[1]);
        line("SecureRandom seeded from timing jitter", jitterMs + System.currentTimeMillis() - t,
                "(--fastseed)");
        if (fastSeed) {
            random = fast;
            out.println("  Java's own SecureRandom seeding: skipped (--fastseed)");
        } else {
            t = System.currentTimeMillis();
            random = new SecureRandom();
            random.nextBytes(new byte[1]);
            line("SecureRandom, Java's own seeding", System.currentTimeMillis() - t,
                    "(one-off per launch; used below)");
        }

        t = System.currentTimeMillis();
        crypto = new BcTlsCrypto(random);
        line("BcTlsCrypto", System.currentTimeMillis() - t, null);
        line("Total since main()", System.currentTimeMillis() - mainEntered, null);

        if (showPin) {
            showServerPin();
            return failures == 0 ? 0 : 1;
        }
        if (!skipCrypto) {
            new Crypto13(out, random, budgetMs).runAll();
        }
        if (!offline) {
            network();
        }
        out.println();
        out.println(failures == 0 ? "All tests completed." : failures + " test(s) FAILED.");
        return failures == 0 ? 0 : 1;
    }

    private void line(String label, long millis, String note) {
        out.println("  " + Fmt.left(label, 46) + Fmt.ms(millis) + (note == null ? "" : "   " + note));
    }

    /**
     * Seed material from scheduling jitter: how many loop passes fit in each
     * clock tick, mixed with the time and free memory. For the --fastseed
     * comparison only; Java's own seeding is the conservative choice.
     */
    static byte[] jitterSeed(int bytes) {
        byte[] seed = new byte[bytes];
        long now = System.currentTimeMillis();
        for (int i = 0; i < 8; i++) {
            seed[i] ^= (byte) (now >>> (8 * i));
        }
        long free = Runtime.getRuntime().freeMemory();
        for (int i = 0; i < 8; i++) {
            seed[8 + i] ^= (byte) (free >>> (8 * i));
        }
        for (int i = 0; i < bytes; i++) {
            long start = System.currentTimeMillis();
            int spins = 0;
            while (System.currentTimeMillis() == start) {
                spins++;
            }
            seed[i] ^= (byte) (spins ^ (spins >>> 8));
        }
        return seed;
    }

    // ---- network -----------------------------------------------------------------

    private void network() {
        out.println();
        out.println("== Network: " + host + ":" + port + ", TLS 1.2, cipher " + cipher + ", group " + group
                + (preValidate ? "" : ", pinned key NOT pre-validated"));
        InetAddress addr;
        long t = System.currentTimeMillis();
        try {
            addr = InetAddress.getByName(host);
        } catch (IOException e) {
            out.println("  DNS FAILED: " + e);
            failures++;
            return;
        }
        line("DNS " + host + " -> " + addr.getHostAddress(), System.currentTimeMillis() - t, null);

        TlsSession session = null;
        for (int i = 1; i <= fullCount; i++) {
            TlsSession s = connect(addr, "full" + (i == 1 ? "*" : " ") + " " + i, null, i == 1);
            if (s != null) {
                session = s;
            }
        }
        if (fullCount > 0) {
            out.println("  (* includes loading and first use of the handshake code)");
        }
        for (int i = 1; i <= resumeCount; i++) {
            if (session == null) {
                out.println("  resume: no session to resume");
                break;
            }
            TlsSession s = connect(addr, "resume " + i, session, false);
            if (s != null) {
                session = s;
            }
        }
    }

    /** One connection; returns its resumable session, or null on failure. */
    private TlsSession connect(InetAddress addr, String label, TlsSession resume, boolean timeline) {
        final long start = System.currentTimeMillis();
        final StringBuffer steps = new StringBuffer();
        Socket socket = null;
        TlsClientProtocol protocol = null;
        PinnedClient13 client = new PinnedClient13(crypto, host, pins, cipher, group, true, preValidate, resume);
        if (timeline) {
            client.setSteps(new PinnedClient13.Steps() {
                public void step(String what) {
                    steps.append("      ").append(Fmt.right(Long.toString(System.currentTimeMillis() - start), 6))
                            .append(" ms  ").append(what).append('\n');
                }
            });
        }
        try {
            socket = new Socket(addr, port);
            long tcp = System.currentTimeMillis() - start;
            socket.setSoTimeout(timeoutMs);
            socket.setTcpNoDelay(true);
            long hs0 = System.currentTimeMillis();
            protocol = new TlsClientProtocol(socket.getInputStream(), socket.getOutputStream());
            protocol.connect(client);
            long hs = System.currentTimeMillis() - hs0;
            String get = "";
            if (getPath != null) {
                long g0 = System.currentTimeMillis();
                String status = httpGet(protocol);
                get = ", GET " + (System.currentTimeMillis() - g0) + " ms (" + status + ")";
            }
            out.println("  " + Fmt.left(label, 9) + " tcp " + Fmt.ms(tcp) + ", handshake " + Fmt.ms(hs)
                    + get + "  " + client.describe() + (client.wasResumed() ? ", resumed" : ""));
            if (resume != null && !client.wasResumed()) {
                out.println("            (server did not resume the session: full handshake done)");
            }
            if (timeline) {
                out.print(steps.toString());
            }
            return client.getResumableSession();
        } catch (IOException e) {
            failures++;
            if (client.pinMismatch()) {
                out.println("  " + Fmt.left(label, 9) + " PIN MISMATCH: the server's key pin is "
                        + client.getObservedPin());
            } else {
                out.println("  " + Fmt.left(label, 9) + " FAILED after "
                        + (System.currentTimeMillis() - start) + " ms: " + e);
            }
            if (timeline) {
                out.print(steps.toString());
            }
            return null;
        } finally {
            closeQuietly(protocol, socket);
        }
    }

    /** Sends GET over the connection and reads the whole response; returns the status line. */
    private String httpGet(TlsClientProtocol protocol) throws IOException {
        OutputStream o = protocol.getOutputStream();
        o.write(Crypto13.ascii("GET " + getPath + " HTTP/1.1\r\nHost: " + host + "\r\nUser-Agent: pastebench13/"
                + VERSION + "\r\nConnection: close\r\n\r\n"));
        o.flush();
        InputStream in = protocol.getInputStream();
        StringBuffer first = new StringBuffer();
        boolean firstDone = false;
        long total = 0;
        byte[] buf = new byte[4096];
        int n;
        String end = "";
        try {
            while ((n = in.read(buf)) > 0) {
                for (int i = 0; i < n && !firstDone; i++) {
                    char c = (char) (buf[i] & 0xff);
                    if (c == '\r' || c == '\n') {
                        firstDone = true;
                    } else {
                        first.append(c);
                    }
                }
                total += n;
            }
        } catch (EOFException e) {
            // Some servers close without a TLS close_notify after "Connection: close";
            // after a complete response that is just the end of the connection.
            if (total == 0) {
                throw e;
            }
            end = ", no close_notify";
        }
        return first.toString() + ", " + total + " bytes" + end;
    }

    private void showServerPin() {
        out.println();
        out.println("== Server key pin (NOT verified: whatever answered on " + host + ":" + port + ")");
        Socket socket = null;
        TlsClientProtocol protocol = null;
        PinnedClient13 client = new PinnedClient13(crypto, host, pins, cipher, group, false, false, null);
        client.setCaptureOnly(true);
        try {
            socket = new Socket(InetAddress.getByName(host), port);
            socket.setSoTimeout(timeoutMs);
            protocol = new TlsClientProtocol(socket.getInputStream(), socket.getOutputStream());
            protocol.connect(client);
        } catch (IOException expected) {
            // capture-only mode ends the handshake on purpose once the certificate arrives
        } finally {
            closeQuietly(protocol, socket);
        }
        if (client.getObservedPin() == null) {
            out.println("  FAILED: no certificate received");
            failures++;
            return;
        }
        out.println("  --pin " + client.getObservedPin());
        if (!pins.isEmpty()) {
            out.println(pins.contains(client.getObservedPin()) ? "  matches a configured pin"
                    : "  does NOT match any configured pin");
        }
        out.println("  Check it against a trusted source (see README.TXT) before using it.");
    }

    private static void closeQuietly(TlsClientProtocol protocol, Socket socket) {
        try {
            if (protocol != null) {
                protocol.close();
            }
        } catch (IOException e) {
            // closing anyway
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            // closing anyway
        }
    }
}
