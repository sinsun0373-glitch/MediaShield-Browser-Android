package com.example.bravemediasaver;

final class RangePlannerTest {
    private static void check(long[][] ranges,long total) {
        long next=0;
        for(long[] r:ranges){if(r[0]!=next||r[1]<r[0])throw new AssertionError("gap/overlap");next=r[1]+1;}
        if(next!=total)throw new AssertionError("wrong total");
    }
    public static void main(String[] args) {
        check(RangePlanner.split(17,4),17);
        check(RangePlanner.split(10000000007L,4),10000000007L);
        check(RangePlanner.split(4,4),4);
        boolean rejected=false;try{RangePlanner.split(3,4);}catch(IllegalArgumentException expected){rejected=true;}
        if(!rejected)throw new AssertionError("invalid split allowed");
        System.out.println("RangePlannerTest: 4 tests passed");
    }
}
