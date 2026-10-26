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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.bouncycastle.tls.TlsClientProtocol;
import org.bouncycastle.tls.TlsSession;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

import com.glitchwrks.paste98.tls.PinnedTlsClient;
import com.glitchwrks.paste98.tls.SpkiPin;
import com.glitchwrks.paste98.tls.TlsOptions;

/**
 * Benchmark for the Win98 / Java 5 paste client's TLS layer.
 *
 * Measures JVM startup, SecureRandom seeding, offline crypto primitives, and
 * full / resumed TLS handshakes against a server authenticated by SPKI pin.
 * Uses Bouncy Castle's lightweight crypto (BcTlsCrypto), so it does not depend
 * on Java 5's JCE provider or its export-strength policy files.
 */
public final class PasteBench {

    private static final String VERSION = "0.3.0";

    // options
    private String host = "pastes.glitchwrks.com";
    private int port = 443;
    private final List<String> pins = new ArrayList<String>();
    private int fullCount = 3;
    private int resumeCount = 3;
    private String getPath = null;
    private String cipher = TlsOptions.CIPHER_AUTO;
    private String group = TlsOptions.GROUP_AUTO;
    private boolean tls13 = false;
    private boolean showPin = false;
    private boolean offline = false;
    private boolean skipCrypto = false;
    private long budgetMs = 2000;
    private int timeoutMs = 30000;

    private int warmupTypingMs = -1;
    private int profileIntervalMs = 0;
    private boolean preValidate = true;

    private final PrintStream out = System.out;
    private int failures = 0;
    private long jvmStart;

    public static void main(String[] args) {
        long mainEntered = System.currentTimeMillis();
        PasteBench b = new PasteBench();
        try {
            b.parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println();
            usage(System.err);
            System.exit(1);
            return;
        }
        int rc;
        try {
            rc = b.run(mainEntered);
        } catch (Exception e) {
            System.err.println("fatal: " + e);
            e.printStackTrace();
            rc = 3;
        }
        System.exit(rc);
    }

    private int run(long mainEntered) throws Exception {
        out.println("pastebench " + VERSION + " - TLS benchmark for the Java 5 paste client");
        out.println();
        out.println("== Environment");
        out.println("  java      " + System.getProperty("java.version") + " (" + System.getProperty("java.vm.name")
                + ", " + System.getProperty("java.vendor") + ")");
        out.println("  os        " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                + System.getProperty("os.arch"));
        out.println("  cpus      " + Runtime.getRuntime().availableProcessors());
        out.println("  max heap  " + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + " MB");
        out.println("  jvm args  " + ManagementFactory.getRuntimeMXBean().getInputArguments());
        out.println("  classpath " + System.getProperty("java.class.path"));

        jvmStart = ManagementFactory.getRuntimeMXBean().getStartTime();
        out.println();
        out.println("== Startup");
        out.println("  JVM start -> main()                 " + (mainEntered - jvmStart)
                + " ms  (wall clock; ~55 ms resolution on Win9x)");

        long t0 = System.nanoTime();
        SecureRandom random = new SecureRandom();
        random.nextBytes(new byte[32]); // forces seeding
        out.println("  SecureRandom first seed             " + ms(System.nanoTime() - t0) + " ms  ("
                + random.getAlgorithm() + ")");

        t0 = System.nanoTime();
        BcTlsCrypto crypto = new BcTlsCrypto(random);
        out.println("  BC TLS crypto init                  " + ms(System.nanoTime() - t0) + " ms");

        if (!skipCrypto) {
            new CryptoBench(out, random, budgetMs).runAll();
        }

        if (!offline) {
            runNetwork(crypto);
        }

        Runtime rt = Runtime.getRuntime();
        out.println();
        out.println("== Totals");
        out.println("  heap in use at end                  " + ((rt.totalMemory() - rt.freeMemory()) / 1024) + " KB of "
                + (rt.totalMemory() / 1024) + " KB committed");
        out.println("  JVM start -> done                   " + (System.currentTimeMillis() - jvmStart) + " ms");
        if (failures > 0) {
            out.println("  " + failures + " connection(s) failed; see above");
            return 2;
        }
        return 0;
    }

    // ---- network ---------------------------------------------------------------

    private void runNetwork(BcTlsCrypto crypto) {
        out.println();
        out.println("== Network: " + host + ":" + port + (showPin ? "  [show-pin mode: pin NOT enforced]" : ""));

        long t0 = System.nanoTime();
        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (IOException e) {
            out.println("  DNS lookup failed: " + e);
            failures++;
            return;
        }
        out.println("  DNS lookup                          " + ms(System.nanoTime() - t0) + " ms  -> "
                + addr.getHostAddress());

        if (showPin) {
            Result r = connectOnce(crypto, addr, null, false);
            if (r.observedPin != null) {
                out.println();
                out.println("  leaf certificate: " + r.observedCert);
                out.println("  SPKI pin:         " + r.observedPin);
                out.println();
                out.println("  This pin was NOT verified. Confirm it against the key on the server before trusting it");
                out.println("  (see README for the openssl command), then pass it with --pin.");
            }
            return;
        }

        out.println("  offering " + (tls13 ? "TLS 1.3 + 1.2" : "TLS 1.2 only") + ", cipher=" + cipher + ", group="
                + group + ", pinned RSA key pre-validation " + (preValidate ? "ON" : "OFF"));

        if (warmupTypingMs >= 0) {
            runWarmup(crypto, addr);
            return;
        }

        out.println();
        out.println("  #  kind     tcp ms  handshake ms  request ms  negotiated");

        TlsSession session = null;
        VmCounters before = null;
        VmCounters after = null;
        long firstDoneAt = 0;
        Timeline firstTimeline = null;
        Timeline secondTimeline = null;
        Sampler sampler = null;
        for (int i = 1; i <= fullCount; i++) {
            Timeline tl = null;
            if (i == 1) {
                before = new VmCounters();
                tl = firstTimeline = new Timeline();
                if (profileIntervalMs > 0) {
                    sampler = new Sampler(Thread.currentThread(), profileIntervalMs);
                    sampler.start();
                }
            } else if (i == 2) {
                tl = secondTimeline = new Timeline();
            }
            Result r = connectOnce(crypto, addr, null, true, tl);
            if (i == 1) {
                if (sampler != null) {
                    sampler.finish();
                }
                after = new VmCounters();
                firstDoneAt = System.currentTimeMillis();
            }
            printResult(i, i == 1 ? "full*" : "full", r);
            if (r.session != null && r.session.isResumable()) {
                session = r.session;
            }
        }
        out.println("     first connection: " + after.describeSince(before) + "; JVM start -> done "
                + (firstDoneAt - jvmStart) + " ms");
        out.println();
        firstTimeline.print(out, "first handshake");
        if (secondTimeline != null) {
            out.println();
            secondTimeline.print(out, "second handshake, for comparison");
        }
        if (sampler != null) {
            out.println();
            sampler.print(out);
        }
        out.println();
        if (resumeCount > 0) {
            if (session == null) {
                out.println("     (server offered no resumable session; skipping resumption tests)");
            } else {
                for (int i = 1; i <= resumeCount; i++) {
                    Result r = connectOnce(crypto, addr, session, true);
                    printResult(fullCount + i, "resume", r);
                    if (r.session != null && r.session.isResumable()) {
                        session = r.session;
                    }
                }
            }
        }
        out.println();
        out.println("  * first handshake includes loading the TLS classes, so it is slower than later ones.");
        out.println("  'resume' rows marked (not resumed) fell back to a full handshake.");
    }

    /**
     * Simulates the resident client: a throwaway handshake warms the TLS stack
     * in a background thread while the user composes a paste, then the real
     * submission is timed as the user would experience it.
     */
    private void runWarmup(final BcTlsCrypto crypto, final InetAddress addr) {
        out.println();
        out.println("== Warm-up simulation (resident client)");
        out.println("  background warm-up handshake starts at launch; simulated typing for " + warmupTypingMs + " ms");

        final Result[] warm = new Result[1];
        final long[] warmDoneAt = new long[1];
        final VmCounters before = new VmCounters();
        final long warmStart = System.nanoTime();
        Thread t = new Thread("tls-warmup") {
            public void run() {
                warm[0] = connectOnce(crypto, addr, null, true);
                warmDoneAt[0] = System.nanoTime();
            }
        };
        t.setDaemon(true);
        t.start();

        try {
            Thread.sleep(warmupTypingMs); // the user is typing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long waitStart = System.nanoTime();
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        double waitedMs = (System.nanoTime() - waitStart) / 1e6;
        VmCounters after = new VmCounters();

        if (warm[0] == null || warm[0].error != null) {
            failures++;
            out.println("  warm-up FAILED: " + (warm[0] == null ? "no result" : warm[0].error));
            return;
        }
        out.println("  warm-up connection finished after   " + fmt((warmDoneAt[0] - warmStart) / 1e6) + " ms  ("
                + after.describeSince(before) + ")");
        out.println("  user waited for warm-up             " + fmt(waitedMs) + " ms"
                + (waitedMs < 1 ? "  (warm-up finished while typing)" : "  (typing ended before warm-up finished)"));

        Result sub = connectOnce(crypto, addr, null, true);
        if (sub.error != null) {
            failures++;
            out.println("  submission FAILED: " + sub.error);
            return;
        }
        double total = waitedMs + sub.tcpMs + sub.handshakeMs + (sub.requestMs < 0 ? 0 : sub.requestMs);
        out.println("  submission: tcp " + fmt(sub.tcpMs) + " ms, handshake " + fmt(sub.handshakeMs) + " ms, request "
                + (sub.requestMs < 0 ? "-" : fmt(sub.requestMs) + " ms") + "  (" + sub.negotiated
                + (sub.status != null ? ", HTTP " + sub.status : "") + ")");
        out.println("  user-visible latency after typing   " + fmt(total) + " ms");
    }

    /** Class-loading and JIT counters from the Java 5 management API. */
    private static final class VmCounters {
        final long classes = ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount();
        final long jitMs;
        final long at = System.nanoTime();

        VmCounters() {
            java.lang.management.CompilationMXBean c = ManagementFactory.getCompilationMXBean();
            jitMs = (c != null && c.isCompilationTimeMonitoringSupported()) ? c.getTotalCompilationTime() : -1;
        }

        String describeSince(VmCounters before) {
            String s = (classes - before.classes) + " classes loaded";
            if (jitMs >= 0 && before.jitMs >= 0) {
                s += ", " + (jitMs - before.jitMs) + " ms JIT compiling";
            }
            return s;
        }
    }

    private void printResult(int n, String kind, Result r) {
        if (r.error != null) {
            failures++;
            out.println(String.format("  %-2d %-7s  %s", Integer.valueOf(n), kind, "FAILED: " + r.error));
            return;
        }
        String req = r.requestMs < 0 ? "-" : fmt(r.requestMs);
        String neg = r.negotiated + (r.resumed ? ", resumed" : (kind.equals("resume") ? " (not resumed)" : ""));
        if (r.status != null) {
            neg = neg + ", HTTP " + r.status + " (" + r.responseBytes + " bytes)";
        }
        out.println(String.format("  %-2d %-7s %7s  %12s  %10s  %s", Integer.valueOf(n), kind, fmt(r.tcpMs),
                fmt(r.handshakeMs), req, neg));
    }

    /** Outcome of a single connection. */
    private static final class Result {
        double tcpMs;
        double handshakeMs;
        double requestMs = -1;
        String negotiated;
        boolean resumed;
        TlsSession session;
        String status;
        long responseBytes;
        String observedPin;
        String observedCert;
        String error;
    }

    /** Options for the shared PinnedTlsClient, from this run's arguments. */
    private TlsOptions tlsOptions() {
        TlsOptions o = new TlsOptions().setHost(host).setPort(port).setCipher(cipher).setGroup(group)
                .setOfferTls13(tls13).setPreValidatePinnedKey(preValidate).setTimeoutMs(timeoutMs);
        for (int i = 0; i < pins.size(); i++) {
            o.addPin(pins.get(i));
        }
        return o;
    }

    private Result connectOnce(BcTlsCrypto crypto, InetAddress addr, TlsSession resume, boolean enforce) {
        return connectOnce(crypto, addr, resume, enforce, null);
    }

    private Result connectOnce(BcTlsCrypto crypto, InetAddress addr, TlsSession resume, boolean enforce,
            Timeline timeline) {
        Result r = new Result();
        Socket sock = new Socket();
        TlsClientProtocol proto = null;
        PinnedTlsClient client = new PinnedTlsClient(crypto, tlsOptions(), enforce, resume);
        try {
            long t0 = System.nanoTime();
            sock.connect(new InetSocketAddress(addr, port), timeoutMs);
            sock.setSoTimeout(timeoutMs);
            sock.setTcpNoDelay(true);
            long t1 = System.nanoTime();
            r.tcpMs = (t1 - t0) / 1e6;

            InputStream sockIn = sock.getInputStream();
            if (timeline != null) {
                sockIn = timeline.wrap(sockIn);
                client.setListener(timeline);
            }
            proto = new TlsClientProtocol(sockIn, sock.getOutputStream());
            proto.connect(client);
            long t2 = System.nanoTime();
            r.handshakeMs = (t2 - t1) / 1e6;
            r.negotiated = client.describeConnection();
            r.resumed = client.wasResumed();
            r.session = client.getResumableSession();
            r.observedPin = client.getObservedPin();
            r.observedCert = client.getObservedCertificate();

            if (getPath != null) {
                doGet(proto, r);
                r.requestMs = (System.nanoTime() - t2) / 1e6;
            }
            proto.close();
        } catch (IOException e) {
            r.observedPin = client.getObservedPin();
            r.observedCert = client.getObservedCertificate();
            if (enforce && client.pinMismatch()) {
                r.error = "server key does not match any --pin; server presented " + r.observedPin;
            } else {
                r.error = e.toString();
            }
        } finally {
            try {
                sock.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
        return r;
    }

    private void doGet(TlsClientProtocol proto, Result r) throws IOException {
        String req = "GET " + getPath + " HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + "User-Agent: pastebench/" + VERSION + " (Java " + System.getProperty("java.version") + ")\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream os = proto.getOutputStream();
        os.write(CryptoBench.asciiBytes(req));
        os.flush();

        InputStream is = proto.getInputStream();
        byte[] buf = new byte[4096];
        StringBuilder statusLine = new StringBuilder();
        boolean statusDone = false;
        long total = 0;
        try {
            int n;
            while ((n = is.read(buf)) > 0) {
                for (int i = 0; i < n && !statusDone; i++) {
                    char c = (char) (buf[i] & 0xff);
                    if (c == '\r' || c == '\n') {
                        statusDone = true;
                    } else {
                        statusLine.append(c);
                    }
                }
                total += n;
            }
        } catch (IOException e) {
            // Some servers close without TLS close_notify after "Connection: close".
            // The response was already received, so treat this as end of stream.
            if (!statusDone) {
                throw e;
            }
        }
        r.responseBytes = total;
        String s = statusLine.toString();
        int sp = s.indexOf(' ');
        r.status = sp > 0 && s.length() >= sp + 4 ? s.substring(sp + 1, sp + 4) : "?";
    }

    // ---- arguments -------------------------------------------------------------

    /**
     * Replaces any "@file" argument with the whitespace-separated words in that
     * file ('#' starts a comment). Win98's command.com limits command lines to
     * 127 characters, which two pins and a few options easily exceed.
     */
    private static String[] expandArgFiles(String[] args) {
        List<String> outArgs = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("@") || a.length() == 1) {
                outArgs.add(a);
                continue;
            }
            String name = a.substring(1);
            BufferedReader r = null;
            try {
                r = new BufferedReader(new InputStreamReader(new FileInputStream(name), "US-ASCII"));
                String line;
                while ((line = r.readLine()) != null) {
                    int hash = line.indexOf('#');
                    if (hash >= 0) {
                        line = line.substring(0, hash);
                    }
                    StringTokenizer st = new StringTokenizer(line);
                    while (st.hasMoreTokens()) {
                        outArgs.add(st.nextToken());
                    }
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("cannot read options file " + name + ": " + e.getMessage());
            } finally {
                if (r != null) {
                    try {
                        r.close();
                    } catch (IOException ignored) {
                        // nothing useful to do
                    }
                }
            }
        }
        return outArgs.toArray(new String[outArgs.size()]);
    }

    private void parseArgs(String[] rawArgs) {
        String[] args = expandArgFiles(rawArgs);
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--help") || a.equals("-h") || a.equals("/?")) {
                usage(System.out);
                System.exit(0);
            } else if (a.equals("--host")) {
                host = need(args, ++i, a);
            } else if (a.equals("--port")) {
                port = intArg(need(args, ++i, a), a, 1, 65535);
            } else if (a.equals("--pin")) {
                pins.add(SpkiPin.normalise(need(args, ++i, a)));
            } else if (a.equals("--full")) {
                fullCount = intArg(need(args, ++i, a), a, 1, 1000);
            } else if (a.equals("--resume")) {
                resumeCount = intArg(need(args, ++i, a), a, 0, 1000);
            } else if (a.equals("--get")) {
                getPath = need(args, ++i, a);
                if (!getPath.startsWith("/")) {
                    throw new IllegalArgumentException("--get path must start with '/'");
                }
            } else if (a.equals("--cipher")) {
                cipher = choice(need(args, ++i, a), a, new String[] { TlsOptions.CIPHER_AUTO,
                    TlsOptions.CIPHER_CHACHA, TlsOptions.CIPHER_AES128, TlsOptions.CIPHER_AES256 });
            } else if (a.equals("--group")) {
                group = choice(need(args, ++i, a), a, new String[] { TlsOptions.GROUP_AUTO,
                    TlsOptions.GROUP_X25519, TlsOptions.GROUP_P256 });
            } else if (a.equals("--tls13")) {
                tls13 = true;
            } else if (a.equals("--no-prevalidate")) {
                preValidate = false;
            } else if (a.equals("--profile")) {
                profileIntervalMs = intArg(need(args, ++i, a), a, 1, 10000);
            } else if (a.equals("--warmup")) {
                warmupTypingMs = intArg(need(args, ++i, a), a, 0, 600000);
            } else if (a.equals("--show-pin")) {
                showPin = true;
            } else if (a.equals("--offline")) {
                offline = true;
            } else if (a.equals("--skip-crypto")) {
                skipCrypto = true;
            } else if (a.equals("--budget-ms")) {
                budgetMs = intArg(need(args, ++i, a), a, 100, 600000);
            } else if (a.equals("--timeout-ms")) {
                timeoutMs = intArg(need(args, ++i, a), a, 1000, 600000);
            } else {
                throw new IllegalArgumentException("unknown option: " + a);
            }
        }
        if (!offline && !showPin && pins.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one --pin is required for network tests (or use --offline / --show-pin)");
        }
        if (warmupTypingMs >= 0 && (offline || showPin)) {
            throw new IllegalArgumentException("--warmup cannot be combined with --offline or --show-pin");
        }
        if (offline && skipCrypto) {
            throw new IllegalArgumentException("--offline with --skip-crypto leaves nothing to run");
        }
    }

    private static String need(String[] args, int i, String opt) {
        if (i >= args.length) {
            throw new IllegalArgumentException(opt + " needs a value");
        }
        return args[i];
    }

    private static int intArg(String v, String opt, int min, int max) {
        int n;
        try {
            n = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(opt + " needs a number, got: " + v);
        }
        if (n < min || n > max) {
            throw new IllegalArgumentException(opt + " must be between " + min + " and " + max);
        }
        return n;
    }

    private static String choice(String v, String opt, String[] allowed) {
        for (int i = 0; i < allowed.length; i++) {
            if (allowed[i].equals(v)) {
                return v;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < allowed.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(allowed[i]);
        }
        throw new IllegalArgumentException(opt + " must be one of: " + sb);
    }

    private static void usage(PrintStream p) {
        p.println("usage: java -jar pastebench.jar [options] [@optionsfile]");
        p.println();
        p.println("  @FILE                read more options from FILE (whitespace separated, # comments)");
        p.println("  --offline            only run local timings (startup, RNG, crypto); no network");
        p.println("  --show-pin           connect once and print the server's SPKI pin (NOT verified)");
        p.println("  --host NAME          server (default pastes.glitchwrks.com)");
        p.println("  --port N             port (default 443)");
        p.println("  --pin BASE64         SHA-256 SPKI pin of the server key; repeat for a backup key");
        p.println("  --full N             full handshakes to run (default 3)");
        p.println("  --resume N           resumed handshakes to attempt (default 3, 0 to skip)");
        p.println("  --get PATH           also send GET PATH on each connection, e.g. /tstpaste");
        p.println("  --cipher C           auto | chacha | aes128 | aes256 (default auto, ChaCha20 first)");
        p.println("  --group G            auto | x25519 | p256 (default auto, X25519 first)");
        p.println("  --tls13              also offer TLS 1.3 (default TLS 1.2 only)");
        p.println("  --no-prevalidate     let BC run its RSA modulus check on the pinned key (slow on old CPUs)");
        p.println("  --profile MS         sample the first handshake's stack every MS ms (Win9x: ~55 ms minimum)");
        p.println("  --warmup MS          resident-client simulation: warm-up handshake in the background,");
        p.println("                       MS of simulated typing, then one timed submission");
        p.println("  --skip-crypto        skip the offline crypto timings");
        p.println("  --budget-ms N        time spent per crypto timing (default 2000)");
        p.println("  --timeout-ms N       socket connect/read timeout (default 30000)");
    }

    // ---- formatting ------------------------------------------------------------

    private static String ms(long nanos) {
        return fmt(nanos / 1e6);
    }

    private static String fmt(double ms) {
        return String.format("%.1f", Double.valueOf(ms));
    }
}
