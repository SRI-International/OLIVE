package com.sri.speech.olive.api.client;

/**
 *
 */ // SAD words have 100 frames pers second
public class SADWord {

    int start;
    int end;

    public SADWord(int start, int end) {
        this.start = start;
        this.end = end;
    }


    public double getStartTimeSeconds() {
        return start / 100.;
    }

    public double getEndTimeSeconds() {
        return end / 100.;
    }
}
