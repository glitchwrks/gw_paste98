package com.glitchwrks.paste98.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.SecureRandom;

import org.junit.Test;

import com.glitchwrks.paste98.tls.SpkiPin;

public class SmallPiecesTest {

    // ---- Json ----------------------------------------------------------------

    @Test
    public void jsonEscapesSpecialAndNonAsciiCharacters() {
        assertEquals("\"plain\"", Json.quote("plain"));
        assertEquals("\"q\\\"b\\\\n\\nr\\rt\\t\"", Json.quote("q\"b\\n\nr\rt\t"));
        assertEquals("\"\\u0000\\u001f\\u007f\\u00e9\\u20ac\"", Json.quote("\u0000\u001f\u007f\u00e9\u20ac"));
    }

    @Test
    public void jsonOutputIsPureAscii() {
        String s = Json.pasteBody("abcd1234", "caf\u00e9 \u2603 \ud83d\ude00");
        for (int i = 0; i < s.length(); i++) {
            assertTrue(s.charAt(i) >= 0x20 && s.charAt(i) <= 0x7e);
        }
    }

    // ---- PasteNames ----------------------------------------------------------

    @Test
    public void generatedNamesMatchServerRules() {
        SecureRandom r = new SecureRandom();
        for (int i = 0; i < 1000; i++) {
            String n = PasteNames.generate(r);
            assertTrue(n, PasteNames.isValid(n));
            assertEquals(8, n.length());
        }
        assertFalse(PasteNames.isValid("abc"));
        assertFalse(PasteNames.isValid("abcd123!"));
        assertTrue(PasteNames.isValid("AbCd1234"));
    }

    // ---- ContentCheck --------------------------------------------------------

    @Test
    public void contentCheckCountsNonLatin1() {
        ContentCheck c = ContentCheck.of("caf\u00e9 \u20ac\u2603");
        assertEquals(2, c.getNonLatin1Count());
        assertTrue(c.isSendable());
        assertFalse(ContentCheck.of("").isSendable());
    }

    // ---- HttpResponse --------------------------------------------------------

    @Test
    public void parsesContentLengthBody() throws IOException {
        HttpResponse r = read("HTTP/1.1 422 Unprocessable Content\r\nContent-Type: text/plain\r\n"
                + "Content-Length: 5\r\n\r\nhelloEXTRA");
        assertEquals(422, r.getStatus());
        assertEquals("text/plain", r.getHeader("Content-Type"));
        assertEquals("hello", r.getBodyText());
    }

    @Test
    public void parsesChunkedBody() throws IOException {
        HttpResponse r = read("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                + "5\r\nhello\r\n7;ext=1\r\n, world\r\n0\r\nX-Trailer: y\r\n\r\n");
        assertEquals("hello, world", r.getBodyText());
    }

    @Test
    public void parsesBodyUntilClose() throws IOException {
        assertEquals("rest", read("HTTP/1.0 200 OK\n\nrest").getBodyText());
    }

    @Test
    public void rejectsGarbage() {
        try {
            read("SSH-2.0-OpenSSH\r\n\r\n");
            fail();
        } catch (IOException expected) {
            // ok
        }
        try {
            read("");
            fail();
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void nameTakenDetection() throws IOException {
        assertTrue(PasteClient.isNameTaken(read("HTTP/1.1 422 X\r\nContent-Type: application/json\r\n"
                + "Content-Length: 37\r\n\r\n{\"name\":[\"has already been taken\"]}\r\n")));
        assertFalse(PasteClient.isNameTaken(read("HTTP/1.1 422 X\r\nContent-Type: text/plain\r\n"
                + "Content-Length: 25\r\n\r\nUnprocessable JSON entity")));
        assertFalse(PasteClient.isNameTaken(read("HTTP/1.1 422 X\r\nContent-Type: application/json\r\n"
                + "Content-Length: 27\r\n\r\n{\"content\":[\"can't be blank\"]}")));
    }

    // ---- SpkiPin -------------------------------------------------------------

    @Test
    public void pinNormalisation() {
        String pin = "Jisk0gqPSNvZGT2YXdsQm/slV6XN/1gv6UWXWHwXyeM=";
        assertEquals(pin, SpkiPin.normalise(pin));
        assertEquals(pin, SpkiPin.normalise("  sha256//" + pin + " "));
        try {
            SpkiPin.normalise("REPLACE_WITH_LIVE_KEY_PIN");
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            SpkiPin.normalise("AAAA");
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    private static HttpResponse read(String raw) throws IOException {
        return HttpResponse.read(new ByteArrayInputStream(raw.getBytes("ISO-8859-1")));
    }
}
