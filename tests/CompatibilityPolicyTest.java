package com.example.bravemediasaver;
public final class CompatibilityPolicyTest {
    static int checks;
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("check "+checks);}
    public static void main(String[] args){
        String page="https://example.com/video";
        check(!CompatibilityPolicy.block(page,"https://other.example/",true,false,false,false,true));
        check(CompatibilityPolicy.block(page,"https://other.example/",false,false,false,false,true));
        check(!CompatibilityPolicy.block(page,"https://other.example/",false,false,true,false,true));
        check(!CompatibilityPolicy.block(page,"https://other.example/",false,true,false,false,true));
        check(CompatibilityPolicy.block(page,"https://example.com/ad",false,false,false,true,true));
        check(!CompatibilityPolicy.block(page,"https://example.com/next",false,false,false,false,true));
        for(String uri:new String[]{"intent://video","javascript:alert(1)","file:///etc/passwd","http://example.com","https://user:password@example.com","https://",""})
            check(CompatibilityPolicy.block(page,uri,true,true,false,false,false));
        check(!CompatibilityPolicy.block(page,"https://other.example",false,false,false,false,false));
        check(CompatibilityPolicy.host(null).isEmpty());
        System.out.println("CompatibilityPolicyTest: "+checks+" checks passed");
    }
}
