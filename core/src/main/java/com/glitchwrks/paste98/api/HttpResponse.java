/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Just enough HTTP/1.1 response parsing for the pastes API: status line,
 * headers, and a body framed by Content-Length, chunked encoding, or the
 * connection closing. Bodies are capped, since the API only returns short
 * messages.
 */
public final class HttpResponse {

    static final int MAX_BODY = 64 * 1024;
    private static final int MAX_LINE = 8192;

    private final int status;
    private final Map headers;
    private final byte[] body;

    private HttpResponse(int status, Map headers, byte[] body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    /** Header value by case-insensitive name, or null. */
    public String getHeader(String name) {
        return (String) headers.get(name.toLowerCase());
    }

    public String getBodyText() {
        try {
            return new String(body, "ISO-8859-1");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e.toString()); // ISO-8859-1 is always present
        }
    }

    public static HttpResponse read(InputStream in) throws IOException {
        String statusLine = readLine(in);
        if (statusLine == null) {
            throw new IOException("server closed the connection without a response");
        }
        int status = parseStatus(statusLine);
        Map headers = new HashMap();
        String line;
        while ((line = readLine(in)) != null && line.length() > 0) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
            }
        }
        byte[] body;
        String te = (String) headers.get("transfer-encoding");
        String cl = (String) headers.get("content-length");
        if (te != null && te.toLowerCase().indexOf("chunked") >= 0) {
            body = readChunked(in);
        } else if (cl != null) {
            int len;
            try {
                len = Integer.parseInt(cl.trim());
            } catch (NumberFormatException e) {
                throw new IOException("bad Content-Length: " + cl);
            }
            if (len < 0 || len > MAX_BODY) {
                throw new IOException("unexpected Content-Length: " + len);
            }
            body = readFully(in, len);
        } else {
            body = readToEnd(in);
        }
        return new HttpResponse(status, headers, body);
    }

    static int parseStatus(String statusLine) throws IOException {
        // "HTTP/1.1 201 Created"
        if (!statusLine.startsWith("HTTP/")) {
            throw new IOException("not an HTTP response: " + truncate(statusLine));
        }
        int sp = statusLine.indexOf(' ');
        if (sp < 0 || statusLine.length() < sp + 4) {
            throw new IOException("bad status line: " + truncate(statusLine));
        }
        try {
            return Integer.parseInt(statusLine.substring(sp + 1, sp + 4));
        } catch (NumberFormatException e) {
            throw new IOException("bad status line: " + truncate(statusLine));
        }
    }

    /** Reads a CRLF- or LF-terminated line as ISO-8859-1; null at end of stream. */
    private static String readLine(InputStream in) throws IOException {
        StringBuffer sb = new StringBuffer();
        int b;
        boolean any = false;
        while ((b = in.read()) >= 0) {
            any = true;
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                if (sb.length() >= MAX_LINE) {
                    throw new IOException("response line too long");
                }
                sb.append((char) b);
            }
        }
        return any ? sb.toString() : null;
    }

    private static byte[] readFully(InputStream in, int len) throws IOException {
        byte[] b = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(b, off, len - off);
            if (n < 0) {
                throw new IOException("connection closed mid-response");
            }
            off += n;
        }
        return b;
    }

    private static byte[] readChunked(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null) {
                throw new IOException("connection closed mid-response");
            }
            int semi = sizeLine.indexOf(';');
            String hex = (semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim();
            int size;
            try {
                size = Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw new IOException("bad chunk size: " + truncate(sizeLine));
            }
            if (size == 0) {
                // trailers until the empty line
                String t;
                while ((t = readLine(in)) != null && t.length() > 0) {
                    // ignored
                }
                return out.toByteArray();
            }
            if (size < 0 || out.size() + size > MAX_BODY) {
                throw new IOException("response body too large");
            }
            out.write(readFully(in, size));
            readLine(in); // CRLF after the chunk
        }
    }

    private static byte[] readToEnd(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (true) {
            int n;
            try {
                n = in.read(buf);
            } catch (IOException e) {
                // Some servers close without a TLS close_notify after the
                // response; treat that as the end of the body.
                break;
            }
            if (n <= 0) {
                break;
            }
            if (out.size() + n > MAX_BODY) {
                throw new IOException("response body too large");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static String truncate(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
