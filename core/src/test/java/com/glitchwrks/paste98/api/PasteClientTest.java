package com.glitchwrks.paste98.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Random;

import org.junit.Test;

import com.glitchwrks.paste98.tls.PinMismatchException;

public class PasteClientTest {

    private static final String CREATED = "HTTP/1.1 201 Created\r\nContent-Type: text/plain\r\nContent-Length: 0\r\n\r\n";
    private static final String NAME_TAKEN = "HTTP/1.1 422 Unprocessable Content\r\nContent-Type: application/json\r\n"
            + "Content-Length: 37\r\n\r\n{\"name\":[\"has already been taken\"]}\r\n";
    private static final String SCHEMA_FAIL = "HTTP/1.1 422 Unprocessable Content\r\nContent-Type: text/plain\r\n"
            + "Content-Length: 25\r\n\r\nUnprocessable JSON entity";
    private static final String UNAUTHORIZED = "HTTP/1.1 401 Unauthorized\r\nContent-Type: text/plain\r\n"
            + "WWW-Authenticate: Basic realm=\"Restricted Area\"\r\nContent-Length: 15\r\n\r\nNot authorized\n";

    private PasteClient client(FakeTransport t) {
        return new PasteClient(t, new Random(42), "test/1.0");
    }

    @Test
    public void createsPasteAndSendsWellFormedRequest() throws Exception {
        FakeTransport t = new FakeTransport().respond(CREATED);
        PasteResult r = client(t).send("hello\nworld \u00e9", "user", "pa:ss".toCharArray());

        assertTrue(PasteNames.isValid(r.getName()));
        assertEquals("https://pastes.example/" + r.getName(), r.getUrl());
        assertEquals(1, r.getAttempts());

        String req = t.requests.get(0);
        assertTrue(req, req.startsWith("POST /api/pastes HTTP/1.1\r\n"));
        assertTrue(req, req.indexOf("Host: pastes.example\r\n") > 0);
        // base64("user:pa:ss")
        assertTrue(req, req.indexOf("Authorization: Basic dXNlcjpwYTpzcw==\r\n") > 0);
        assertTrue(req, req.indexOf("Content-Type: application/json\r\n") > 0);
        String body = req.substring(req.indexOf("\r\n\r\n") + 4);
        assertEquals("{\"name\":\"" + r.getName() + "\",\"content\":\"hello\\nworld \\u00e9\"}", body);
        assertTrue(req, req.indexOf("Content-Length: " + body.length() + "\r\n") > 0);
    }

    @Test
    public void retriesWithNewNameWhenNameTaken() throws Exception {
        FakeTransport t = new FakeTransport().respond(NAME_TAKEN).respond(NAME_TAKEN).respond(CREATED);
        PasteResult r = client(t).send("x", "u", "p".toCharArray());
        assertEquals(3, r.getAttempts());
        assertEquals(3, t.requests.size());
        assertTrue(!t.requests.get(0).equals(t.requests.get(1)));
    }

    @Test
    public void givesUpAfterRepeatedNameClashes() {
        FakeTransport t = new FakeTransport();
        for (int i = 0; i < PasteClient.MAX_NAME_ATTEMPTS; i++) {
            t.respond(NAME_TAKEN);
        }
        expect(t, "x", PasteException.Kind.SERVER);
        assertEquals(PasteClient.MAX_NAME_ATTEMPTS, t.requests.size());
    }

    @Test
    public void reportsAuthFailure() {
        expect(new FakeTransport().respond(UNAUTHORIZED), "x", PasteException.Kind.AUTH);
    }

    @Test
    public void reportsSchemaRejection() {
        expect(new FakeTransport().respond(SCHEMA_FAIL), "x", PasteException.Kind.REJECTED);
    }

    @Test
    public void reportsUnexpectedStatus() {
        expect(new FakeTransport().respond("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n"), "x",
                PasteException.Kind.SERVER);
    }

    @Test
    public void reportsPinMismatchWithoutSending() {
        FakeTransport t = new FakeTransport().fail(new PinMismatchException("abc="));
        expect(t, "x", PasteException.Kind.PIN);
        assertEquals(0, t.requests.size());
    }

    @Test
    public void reportsNetworkFailure() {
        expect(new FakeTransport().fail(new IOException("Connection refused")), "x", PasteException.Kind.NETWORK);
    }

    @Test
    public void rejectsEmptyAndOversizedContentWithoutConnecting() {
        FakeTransport t = new FakeTransport();
        expect(t, "", PasteException.Kind.CONTENT);
        char[] big = new char[ContentCheck.MAX_CHARS + 1];
        java.util.Arrays.fill(big, 'a');
        expect(t, new String(big), PasteException.Kind.CONTENT);
        assertEquals(0, t.requests.size());
    }

    @Test
    public void acceptsContentAtTheSizeLimit() throws Exception {
        char[] max = new char[ContentCheck.MAX_CHARS];
        java.util.Arrays.fill(max, 'a');
        client(new FakeTransport().respond(CREATED)).send(new String(max), "u", "p".toCharArray());
    }

    private void expect(FakeTransport t, String content, PasteException.Kind kind) {
        try {
            client(t).send(content, "u", "p".toCharArray());
            fail("expected " + kind);
        } catch (PasteException e) {
            assertEquals(e.getMessage(), kind, e.getKind());
        }
    }
}
