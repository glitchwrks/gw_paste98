/*
 * Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.tls;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;

/**
 * TCP connect with a timeout on any Java. Java 1.4+ has Socket.connect(address,
 * timeout), called here by reflection. Java 1.3 can only connect without a
 * timeout, so the connection is made on a helper thread that is abandoned
 * (and its socket closed when it finally completes) if the timeout passes.
 */
public final class Sockets {

    private static final Constructor ADDRESS;
    private static final Method CONNECT;

    static {
        Constructor address = null;
        Method connect = null;
        try {
            Class socketAddress = Class.forName("java.net.SocketAddress");
            address = Class.forName("java.net.InetSocketAddress").getConstructor(
                    new Class[] { String.class, Integer.TYPE });
            connect = Socket.class.getMethod("connect", new Class[] { socketAddress, Integer.TYPE });
        } catch (Exception e) {
            // Java 1.3
        }
        ADDRESS = address;
        CONNECT = connect;
    }

    private Sockets() {
    }

    /** True if this Java can time out a connect itself (1.4+). */
    public static boolean hasNativeConnectTimeout() {
        return CONNECT != null;
    }

    /**
     * Resolves host and connects, giving up after timeoutMs (DNS lookup
     * included on Java 1.3).
     *
     * @throws InterruptedIOException if the timeout passes
     */
    public static Socket connect(String host, int port, int timeoutMs) throws IOException {
        if (CONNECT != null) {
            Socket s = new Socket();
            try {
                Object address = ADDRESS.newInstance(new Object[] { host, new Integer(port) });
                CONNECT.invoke(s, new Object[] { address, new Integer(timeoutMs) });
                return s;
            } catch (InvocationTargetException e) {
                close(s);
                Throwable t = e.getTargetException();
                if (t instanceof IOException) {
                    throw (IOException) t;
                }
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                }
                throw new IOException(t.toString());
            } catch (IllegalAccessException e) {
                close(s);
                throw new IOException(e.toString());
            } catch (InstantiationException e) {
                close(s);
                throw new IOException(e.toString());
            }
        }
        return connectOnThread(host, port, timeoutMs);
    }

    /** The Java 1.3 path; package-private for tests. */
    static Socket connectOnThread(final String host, final int port, int timeoutMs) throws IOException {
        final Object lock = new Object();
        final Socket[] result = new Socket[1];
        final IOException[] error = new IOException[1];
        final boolean[] state = new boolean[2]; // [0] done, [1] abandoned
        Thread t = new Thread("connect " + host + ":" + port) {
            public void run() {
                Socket s = null;
                IOException e = null;
                try {
                    s = new Socket(InetAddress.getByName(host), port);
                } catch (IOException x) {
                    e = x;
                } catch (RuntimeException x) {
                    e = new IOException(x.toString());
                }
                synchronized (lock) {
                    if (state[1]) {
                        close(s); // the caller gave up waiting
                        return;
                    }
                    result[0] = s;
                    error[0] = e;
                    state[0] = true;
                    lock.notifyAll();
                }
            }
        };
        t.setDaemon(true);
        t.start();
        long end = System.currentTimeMillis() + timeoutMs;
        synchronized (lock) {
            while (!state[0]) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) {
                    state[1] = true;
                    throw new InterruptedIOException("connect to " + host + ":" + port + " timed out after "
                            + (timeoutMs / 1000) + " s");
                }
                try {
                    lock.wait(left);
                } catch (InterruptedException e) {
                    state[1] = true;
                    throw new InterruptedIOException("connect to " + host + ":" + port + " interrupted");
                }
            }
        }
        if (error[0] != null) {
            throw error[0];
        }
        return result[0];
    }

    private static void close(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }
}
