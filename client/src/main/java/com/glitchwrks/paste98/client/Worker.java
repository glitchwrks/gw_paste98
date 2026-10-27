/*
 * Paste Jar - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 */
package com.glitchwrks.paste98.client;

import java.util.Vector;

/**
 * One background thread running jobs in order (java.util.concurrent is
 * Java 5). A daemon thread: it never keeps the program alive on its own.
 */
public final class Worker {

    private final Vector queue = new Vector();
    private boolean stopped;

    public Worker(String name) {
        Thread t = new Thread(name) {
            public void run() {
                loop();
            }
        };
        t.setDaemon(true);
        t.start();
    }

    /** Queues a job; ignored after shutdownNow(). */
    public synchronized void submit(Runnable job) {
        if (!stopped) {
            queue.addElement(job);
            notifyAll();
        }
    }

    /** Drops queued jobs and ends the thread after the current job. */
    public synchronized void shutdownNow() {
        stopped = true;
        queue.removeAllElements();
        notifyAll();
    }

    private void loop() {
        while (true) {
            Runnable job;
            synchronized (this) {
                while (queue.isEmpty() && !stopped) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // keep waiting
                    }
                }
                if (stopped) {
                    return;
                }
                job = (Runnable) queue.elementAt(0);
                queue.removeElementAt(0);
            }
            try {
                job.run();
            } catch (Throwable t) {
                Log.error("background job failed", t);
            }
        }
    }
}
