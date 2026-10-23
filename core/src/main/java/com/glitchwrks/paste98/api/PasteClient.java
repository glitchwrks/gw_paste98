/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.api;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Random;

import org.bouncycastle.util.encoders.Base64;

import com.glitchwrks.paste98.tls.PinMismatchException;

/**
 * Creates pastes with POST /api/pastes on the Glitch Works pastes service.
 *
 * Server behaviour this relies on (glitchwrks/pastes, paste_app.rb):
 * 201 with an empty body on success; 401 if Basic Auth fails; 422 with the
 * text "Unprocessable JSON entity" if the body fails the JSON schema; 422
 * with a JSON object of per-field errors if the model is invalid, which for a
 * well-formed request means the name is already taken.
 */
public final class PasteClient {

    /** Attempts with fresh names before giving up on name clashes. */
    static final int MAX_NAME_ATTEMPTS = 5;

    private final Transport transport;
    private final Random random;
    private final String userAgent;

    /**
     * @param random source for paste names; use a SecureRandom so names are
     *        not guessable
     */
    public PasteClient(Transport transport, Random random, String userAgent) {
        this.transport = transport;
        this.random = random;
        this.userAgent = userAgent;
    }

    /**
     * Creates a paste and returns where it can be read.
     *
     * @throws PasteException with a {@link PasteException.Kind} describing the failure
     */
    public PasteResult send(String content, String username, char[] password) throws PasteException {
        ContentCheck check = ContentCheck.of(content);
        if (check.isEmpty()) {
            throw new PasteException(PasteException.Kind.CONTENT, "The paste is empty.");
        }
        if (check.isTooLong()) {
            throw new PasteException(PasteException.Kind.CONTENT, "The paste is " + check.getLength()
                    + " characters; the server stores at most " + ContentCheck.MAX_CHARS + ".");
        }
        String auth = basicAuth(username, password);

        for (int attempt = 1; attempt <= MAX_NAME_ATTEMPTS; attempt++) {
            String name = PasteNames.generate(random);
            HttpResponse response = post(Json.pasteBody(name, content), auth);
            int status = response.getStatus();
            if (status == 201) {
                return new PasteResult(name, transport.getBaseUrl() + name, attempt);
            }
            if (status == 401) {
                throw new PasteException(PasteException.Kind.AUTH, "The server rejected the username or password.");
            }
            if (status == 422) {
                if (isNameTaken(response)) {
                    continue; // try another name
                }
                throw new PasteException(PasteException.Kind.REJECTED, "The server rejected the paste: "
                        + summarise(response.getBodyText()));
            }
            throw new PasteException(PasteException.Kind.SERVER, "Unexpected response from the server: HTTP "
                    + status + " " + summarise(response.getBodyText()));
        }
        throw new PasteException(PasteException.Kind.SERVER, "Could not find a free paste name after "
                + MAX_NAME_ATTEMPTS + " tries.");
    }

    private HttpResponse post(String json, String auth) throws PasteException {
        Transport.Exchange ex;
        try {
            ex = transport.open();
        } catch (PinMismatchException e) {
            throw new PasteException(PasteException.Kind.PIN, "The server's key does not match the pinned key. "
                    + "Nothing was sent. Server presented: " + e.getPresentedPin(), e);
        } catch (IOException e) {
            throw new PasteException(PasteException.Kind.NETWORK, "Could not connect: " + e.getMessage(), e);
        }
        try {
            byte[] body = json.getBytes("US-ASCII"); // Json escapes everything outside ASCII
            String head = "POST /api/pastes HTTP/1.1\r\n"
                    + "Host: " + transport.getHostHeader() + "\r\n"
                    + "User-Agent: " + userAgent + "\r\n"
                    + "Authorization: Basic " + auth + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream out = ex.getOutputStream();
            out.write(head.getBytes("US-ASCII"));
            out.write(body);
            out.flush();
            return HttpResponse.read(ex.getInputStream());
        } catch (IOException e) {
            throw new PasteException(PasteException.Kind.NETWORK, "The connection failed: " + e.getMessage(), e);
        } finally {
            ex.close();
        }
    }

    /** A model error on "name" from a schema-valid request means the name is taken. */
    static boolean isNameTaken(HttpResponse r) {
        String ct = r.getHeader("content-type");
        String body = r.getBodyText();
        boolean json = (ct != null && ct.indexOf("json") >= 0) || body.trim().startsWith("{");
        return json && body.indexOf("\"name\"") >= 0;
    }

    static String basicAuth(String username, char[] password) throws PasteException {
        try {
            byte[] user = username.getBytes("UTF-8");
            byte[] pass = new String(password).getBytes("UTF-8");
            byte[] both = new byte[user.length + 1 + pass.length];
            System.arraycopy(user, 0, both, 0, user.length);
            both[user.length] = ':';
            System.arraycopy(pass, 0, both, user.length + 1, pass.length);
            String encoded = new String(Base64.encode(both), "US-ASCII");
            java.util.Arrays.fill(both, (byte) 0);
            java.util.Arrays.fill(pass, (byte) 0);
            return encoded;
        } catch (UnsupportedEncodingException e) {
            throw new PasteException(PasteException.Kind.CONTENT, "UTF-8 is not available: " + e);
        }
    }

    private static String summarise(String body) {
        String s = body.trim();
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }
}
