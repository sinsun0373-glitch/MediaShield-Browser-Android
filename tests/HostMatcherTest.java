package com.example.bravemediasaver;

import java.util.Set;

public final class HostMatcherTest {
    public static void main(String[] args) {
        Set<String> rules = Set.of("doubleclick.net", "track.example.com");
        check(HostMatcher.containsHostOrParent(rules,"doubleclick.net"),"exact");
        check(HostMatcher.containsHostOrParent(rules,"ad.doubleclick.net"),"subdomain");
        check(!HostMatcher.containsHostOrParent(rules,"notdoubleclick.net"),"lookalike");
        check(!HostMatcher.containsHostOrParent(rules,"doubleclick.net.evil.test"),"suffix impersonation");
        check(HostMatcher.containsHostOrParent(rules,"a.b.track.example.com"),"nested subdomain");
        check(!HostMatcher.containsHostOrParent(rules,null),"null");
        System.out.println("OK: 6 hostname matching checks");
    }
    private static void check(boolean correct, String name) {
        if(!correct) throw new AssertionError(name);
    }
}
