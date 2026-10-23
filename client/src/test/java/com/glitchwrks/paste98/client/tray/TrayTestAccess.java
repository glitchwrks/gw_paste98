package com.glitchwrks.paste98.client.tray;

/** Exposes package-private NativeTray helpers to tests in other packages. */
public final class TrayTestAccess {

    private TrayTestAccess() {
    }

    public static String ascii(String s, int max) {
        return NativeTray.ascii(s, max);
    }
}
