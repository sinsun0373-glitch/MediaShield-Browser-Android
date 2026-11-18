package com.example.bravemediasaver;

import java.net.URI;

/** Navigation policy shared with the JVM regression tests. */
public final class CompatibilityPolicy {
    private CompatibilityPolicy() {}
    public static String host(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null) return "";
            return uri.getHost().toLowerCase(java.util.Locale.ROOT);
        } catch (Exception ignored) { return ""; }
    }
    public static boolean block(String current, String target, boolean gesture,
            boolean direct, boolean redirect, boolean newWindow, boolean lock) {
        String host = host(target);
        if (host.isEmpty()) return true;
        if (direct) return false;
        if (newWindow && !gesture) return true;
        return lock && !gesture && !redirect && !host(current).isEmpty()
                && !host.equals(host(current));
    }
}
