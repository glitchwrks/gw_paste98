/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.swing.SwingUtilities;

import com.glitchwrks.paste98.api.PasteClient;
import com.glitchwrks.paste98.api.PasteException;
import com.glitchwrks.paste98.api.PasteResult;
import com.glitchwrks.paste98.api.PinnedTransport;
import com.glitchwrks.paste98.tls.PinMismatchException;
import com.glitchwrks.paste98.tls.PinnedConnection;

/**
 * Network work, on a single background thread: the TLS warm-up at launch,
 * then each paste in turn. Callbacks run on the Swing event thread.
 *
 * The warm-up is a throwaway pinned handshake. It loads and runs the TLS code
 * once (about 1.7 s on a Pentium III with Java 5) so that later pastes take
 * about 0.4 s. Because everything shares one thread, a paste sent before the
 * warm-up finishes simply waits for it.
 */
public final class PasteService {

    /** Result of a send, delivered on the Swing event thread. */
    public interface Callback {
        void succeeded(PasteResult result);

        void failed(PasteException e);
    }

    /** Status changes, delivered on the Swing event thread. */
    public interface StatusListener {
        void statusChanged(String shortStatus, String detail);
    }

    private final Config config;
    private PasteClient client; // created on the worker thread: seeding SecureRandom is slow
    private final ExecutorService worker;
    private StatusListener listener;

    private String shortStatus = "Starting";
    private String detail = "Connecting to the server to warm up the encryption code...";
    private String lastUrl;
    private String connectionInfo; // e.g. "TLS 1.2, ECDHE-RSA-AES256-GCM-SHA384"

    /** Password for this session only; never written anywhere. */
    private char[] password;

    public PasteService(Config config) {
        this.config = config;
        this.worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "paste-worker");
                t.setDaemon(true);
                return t;
            }
        });
    }

    /** Only called on the worker thread. */
    private PasteClient client() {
        if (client == null) {
            client = new PasteClient(new PinnedTransport(config.getTlsOptions()),
                    PinnedConnection.crypto().getSecureRandom(), Version.userAgent());
        }
        return client;
    }

    public void setStatusListener(StatusListener listener) {
        this.listener = listener;
    }

    public synchronized String getShortStatus() {
        return shortStatus;
    }

    public synchronized String getDetail() {
        return detail;
    }

    public synchronized String getLastUrl() {
        return lastUrl;
    }

    public Config getConfig() {
        return config;
    }

    // ---- credentials -----------------------------------------------------------

    public synchronized boolean hasCredentials() {
        return config.getUsername() != null && password != null;
    }

    public synchronized void setPassword(char[] pw) {
        clearPassword();
        password = pw;
    }

    public synchronized void clearPassword() {
        if (password != null) {
            Arrays.fill(password, '\0');
            password = null;
        }
    }

    // ---- work ----------------------------------------------------------------

    /** Starts the background warm-up handshake. */
    public void warmUp() {
        worker.submit(new Runnable() {
            public void run() {
                long t0 = System.currentTimeMillis();
                try {
                    PinnedConnection c = PinnedConnection.open(config.getTlsOptions());
                    String how = c.describe();
                    c.close();
                    long ms = System.currentTimeMillis() - t0;
                    Log.info("warm-up handshake done in " + ms + " ms: " + how);
                    synchronized (PasteService.this) {
                        connectionInfo = how;
                    }
                    setStatus("Ready", "Ready. Connected to " + config.getTlsOptions().getHost()
                            + " with " + how + " (warm-up took " + ms + " ms).");
                } catch (PinMismatchException e) {
                    Log.info("warm-up: " + e.getMessage());
                    setStatus("KEY MISMATCH", "The server's key does not match the pin in " + Config.SETTINGS_FILE
                            + ". Pastes will not be sent.\n\nServer presented: " + e.getPresentedPin());
                } catch (IOException e) {
                    Log.info("warm-up failed: " + e);
                    setStatus("Offline", "Could not reach " + config.getTlsOptions().getHost() + ": "
                            + e.getMessage() + "\n\nPastes will retry the connection.");
                } catch (RuntimeException e) {
                    Log.error("warm-up failed", e);
                    setStatus("Error", "Unexpected error while connecting: " + e);
                }
            }
        });
    }

    /** Sends a paste in the background; credentials must be set. */
    public void send(final String content, final Callback callback) {
        final String user;
        final char[] pw;
        synchronized (this) {
            user = config.getUsername();
            pw = password == null ? null : (char[]) password.clone();
        }
        if (user == null || pw == null) {
            throw new IllegalStateException("no credentials");
        }
        setStatus("Sending", "Sending a paste of " + content.length() + " characters...");
        worker.submit(new Runnable() {
            public void run() {
                long t0 = System.currentTimeMillis();
                try {
                    final PasteResult r = client().send(content, user, pw);
                    Log.info("paste created in " + (System.currentTimeMillis() - t0) + " ms: " + r.getUrl()
                            + (r.getAttempts() > 1 ? " (" + r.getAttempts() + " name attempts)" : ""));
                    synchronized (PasteService.this) {
                        lastUrl = r.getUrl();
                    }
                    setStatus("Ready", readyDetail());
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            callback.succeeded(r);
                        }
                    });
                } catch (final PasteException e) {
                    Log.info("paste failed (" + e.getKind() + "): " + e.getMessage());
                    if (e.getKind() == PasteException.Kind.AUTH) {
                        clearPassword();
                    }
                    setStatus(e.getKind() == PasteException.Kind.PIN ? "KEY MISMATCH" : "Ready",
                            "Last paste failed: " + e.getMessage());
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            callback.failed(e);
                        }
                    });
                } catch (final RuntimeException e) {
                    Log.error("paste failed unexpectedly", e);
                    setStatus("Error", "Unexpected error: " + e);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            callback.failed(new PasteException(PasteException.Kind.SERVER, "Unexpected error: " + e, e));
                        }
                    });
                } finally {
                    Arrays.fill(pw, '\0');
                }
            }
        });
    }

    private synchronized String readyDetail() {
        return "Ready. Connected to " + config.getTlsOptions().getHost()
                + (connectionInfo != null ? " with " + connectionInfo : "") + ".";
    }

    public void shutdown() {
        worker.shutdownNow();
        clearPassword();
    }

    private void setStatus(final String s, final String d) {
        synchronized (this) {
            shortStatus = s;
            detail = d;
        }
        final StatusListener l = listener;
        if (l != null) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    l.statusChanged(s, d);
                }
            });
        }
    }
}
