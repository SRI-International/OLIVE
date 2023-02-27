package com.sri.speech.olive.api.utils;

/**
 * A region with a start and end time.  Times are in milliseconds.
 */
public class RegionWord {

    public int start; // in MS
    public int end; // in MS

    public RegionWord(int start_ms, int end_ms) {
        this.start = start_ms;
        this.end = end_ms;
    }


    public float getStartTimeSeconds() {
        return (float)(start / 1000.0);
    }

    public float getEndTimeSeconds() {
        return (float)(end / 1000.0);
    }

    public void setStartTimeSeconds(double seconds) {
         start = (int)(seconds*1000);
    }

    public void setEndTimeSeconds(double seconds) {
        end = (int)(seconds*1000.0);
    }


    public void setStartTime(int ms) {
        start = ms;
    }

    public void setEndTime(int ms) {
        end = ms;
    }
}
