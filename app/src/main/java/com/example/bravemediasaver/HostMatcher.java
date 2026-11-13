package com.example.bravemediasaver;

import java.util.Set;

/** Domain-match helper that never matches unrelated lookalike domain suffixes. */
final class HostMatcher {
    private HostMatcher() {}

    static boolean containsHostOrParent(Set<String> domains, String host) {
        if (host == null || host.isEmpty()) return false;
        String part = host;
        while (true) {
            if (domains.contains(part)) return true;
            int dot = part.indexOf('.');
            if (dot < 0) return false;
            part = part.substring(dot + 1);
        }
    }
}
