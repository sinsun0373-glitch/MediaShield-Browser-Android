package com.example.bravemediasaver;

/** Inclusive byte ranges, covering exactly [0, totalBytes-1] with no overlap. */
final class RangePlanner {
    private RangePlanner() {}
    static long[][] split(long totalBytes, int partCount) {
        if(totalBytes<=0||partCount<=0||partCount>16||totalBytes<partCount)
            throw new IllegalArgumentException("Invalid range configuration");
        long[][] ranges=new long[partCount][2];
        long base=totalBytes/partCount, remainder=totalBytes%partCount;
        long cursor=0;
        for(int i=0;i<partCount;i++) {
            long length=base+(i<remainder?1:0);
            ranges[i][0]=cursor;ranges[i][1]=cursor+length-1;
            cursor+=length;
        }
        return ranges;
    }
}
