package com.glitchwrks.paste98.tls;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.junit.Test;

public class PinFetchTest {

    /** Pins computed by openssl when the test certificates were made (see certs/README.txt). */
    private static Map<String, String> opensslPins() throws IOException {
        Map<String, String> m = new HashMap<String, String>();
        BufferedReader r = new BufferedReader(new InputStreamReader(resource("pins.txt"), "US-ASCII"));
        String line;
        while ((line = r.readLine()) != null) {
            String[] parts = line.trim().split(" ");
            if (parts.length == 2) {
                m.put(parts[0], parts[1]);
            }
        }
        r.close();
        return m;
    }

    private static InputStream resource(String name) {
        InputStream in = PinFetchTest.class.getResourceAsStream("/certs/" + name);
        if (in == null) {
            throw new IllegalStateException("missing test resource certs/" + name);
        }
        return in;
    }

    private static byte[] bytes(String name) throws IOException {
        InputStream in = resource(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    @Test
    public void rsaCertificateWithSubjectAltNames() throws Exception {
        ServerKeyInfo i = ServerKeyInfo.fromCertificate(bytes("rsa-san.der"), "pastes.example.com", 443, 1);
        assertEquals(opensslPins().get("rsa-san.der"), i.getPin());
        assertEquals("RSA 2048-bit", i.getKeyDescription());
        assertEquals(Arrays.asList("pastes.example.com", "*.pastes.example.com", "192.0.2.7"), i.getNames());
        assertTrue(i.getSubject(), i.getSubject().indexOf("CN=pastes.example.com") >= 0);
        assertTrue(i.namesHost());
        assertTrue(i.getNotAfter().after(i.getNotBefore()));

        assertTrue(ServerKeyInfo.fromCertificate(bytes("rsa-san.der"), "www.pastes.example.com", 443, 1).namesHost());
        assertTrue(ServerKeyInfo.fromCertificate(bytes("rsa-san.der"), "192.0.2.7", 443, 1).namesHost());
        assertFalse(ServerKeyInfo.fromCertificate(bytes("rsa-san.der"), "a.b.pastes.example.com", 443, 1).namesHost());
        assertFalse(ServerKeyInfo.fromCertificate(bytes("rsa-san.der"), "evil.example", 443, 1).namesHost());
    }

    @Test
    public void ecCertificateFallsBackToCommonName() throws Exception {
        ServerKeyInfo i = ServerKeyInfo.fromCertificate(bytes("ec-cn-only.der"), "CN-ONLY.example.org", 443, 1);
        assertEquals(opensslPins().get("ec-cn-only.der"), i.getPin());
        assertEquals("EC P-256", i.getKeyDescription());
        assertEquals(Arrays.asList("cn-only.example.org"), i.getNames());
        assertTrue(i.namesHost());
    }

    @Test
    public void ed25519Certificate() throws Exception {
        ServerKeyInfo i = ServerKeyInfo.fromCertificate(bytes("ed25519.der"), "ed.example.net", 443, 1);
        assertEquals(opensslPins().get("ed25519.der"), i.getPin());
        assertEquals("Ed25519", i.getKeyDescription());
    }

    @Test
    public void garbageIsReportedAsIoException() {
        try {
            ServerKeyInfo.fromCertificate(new byte[] { 1, 2, 3 }, "h", 443, 1);
            fail();
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void wildcardRules() {
        assertTrue(ServerKeyInfo.hostMatches("*.example.com", "www.example.com"));
        assertFalse(ServerKeyInfo.hostMatches("*.example.com", "example.com"));
        assertFalse(ServerKeyInfo.hostMatches("*.example.com", "a.www.example.com"));
        assertFalse(ServerKeyInfo.hostMatches("*.", "x."));
        assertTrue(ServerKeyInfo.hostMatches("example.com", "example.com"));
    }

    /**
     * Fetches the pin from a real TLS server (the JDK's, with the test key)
     * and checks it equals the pin openssl computed from the certificate,
     * and that the fetch stops before the key exchange.
     */
    @Test
    public void fetchesThePinFromALiveServerWithoutCompletingTheHandshake() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(resource("test-server.p12"), "testonly".toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "testonly".toCharArray());
        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(kmf.getKeyManagers(), null, null);
        final SSLServerSocket server = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket(0);
        server.setEnabledProtocols(new String[] { "TLSv1.2" });
        final boolean[] handshakeCompleted = { false };
        Thread t = new Thread("test-tls-server") {
            public void run() {
                try {
                    Socket s = server.accept();
                    try {
                        ((SSLSocket) s).startHandshake();
                        handshakeCompleted[0] = true;
                    } catch (IOException expected) {
                        // the client walks away after the certificate
                    } finally {
                        s.close();
                    }
                } catch (IOException e) {
                    // server closed
                }
            }
        };
        t.start();
        try {
            TlsOptions o = new TlsOptions().setHost("localhost").setPort(server.getLocalPort()).setTimeoutMs(10000);
            ServerKeyInfo i = PinFetcher.fetch(o);
            assertEquals(opensslPins().get("test-server.p12"), i.getPin());
            assertEquals("RSA 2048-bit", i.getKeyDescription());
            assertTrue(i.namesHost());
            assertEquals(1, i.getChainLength());
            t.join(5000);
            assertFalse("the handshake must stop at the certificate", handshakeCompleted[0]);

            // And a pinned connection to the same server succeeds with that pin.
            final ServerSocket again = server;
            Thread t2 = new Thread("test-tls-server-2") {
                public void run() {
                    try {
                        Socket s = again.accept();
                        try {
                            ((SSLSocket) s).startHandshake();
                            s.getOutputStream().flush();
                            Thread.sleep(500);
                        } finally {
                            s.close();
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            };
            t2.start();
            PinnedConnection c = PinnedConnection.open(o.addPin(i.getPin()));
            c.close();
            t2.join(5000);
        } finally {
            server.close();
        }
    }

    @Test
    public void unreachableServerIsAnIoException() throws Exception {
        ServerSocket s = new ServerSocket(0);
        int port = s.getLocalPort();
        s.close(); // nothing listens there now
        try {
            PinFetcher.fetch(new TlsOptions().setHost("127.0.0.1").setPort(port).setTimeoutMs(3000));
            fail();
        } catch (IOException expected) {
            // ok
        }
    }
}
