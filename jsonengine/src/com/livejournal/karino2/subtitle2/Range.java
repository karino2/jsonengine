package com.livejournal.karino2.subtitle2;

class Range {
    int begin;
    int end;
    public Range(int beg, int end) {
        this.begin = beg;
        this.end = end;
    }
    boolean inside(int idx) { return begin <= idx && idx <= end; }
    public int getEnd() { return end; }
}