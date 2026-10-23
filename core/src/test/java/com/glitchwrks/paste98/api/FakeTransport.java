package com.glitchwrks.paste98.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** Replays canned HTTP responses and records the requests sent. */
final class FakeTransport implements Transport {

    private final LinkedList<Object> responses = new LinkedList<Object>();
    final List<String> requests = new ArrayList<String>();

    FakeTransport respond(String rawResponse) {
        responses.add(rawResponse);
        return this;
    }

    FakeTransport fail(IOException e) {
        responses.add(e);
        return this;
    }

    public Exchange open() throws IOException {
        Object next = responses.removeFirst();
        if (next instanceof IOException) {
            throw (IOException) next;
        }
        final byte[] response = ((String) next).getBytes("ISO-8859-1");
        final ByteArrayOutputStream sent = new ByteArrayOutputStream();
        return new Exchange() {
            public InputStream getInputStream() {
                return new ByteArrayInputStream(response);
            }

            public OutputStream getOutputStream() {
                return sent;
            }

            public void close() {
                try {
                    requests.add(sent.toString("ISO-8859-1"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public String getHostHeader() {
        return "pastes.example";
    }

    public String getBaseUrl() {
        return "https://pastes.example/";
    }
}
