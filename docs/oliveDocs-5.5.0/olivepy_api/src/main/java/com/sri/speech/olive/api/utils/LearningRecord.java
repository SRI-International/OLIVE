package com.sri.speech.olive.api.utils;


public class LearningRecord {

  String filename;
  String classID;
  RegionWord region;

  public LearningRecord( String filename ){
    // error if filename is null?
    this.filename = filename;
    classID = null;
    region = null;
  }

  public String getFilename(){
    return  filename;

  }

  public String getClassID(){
    // can be null;
    return classID;
  }

  public RegionWord getRegion(){
    return  region;
  }

  public boolean hasRegions(){
    return region != null;
  }

  public boolean hasClass(){
    return classID != null;
  }


}


/*



public class RegionWord {

    public int start; // in MS
    public int end; // in MS

    public RegionWord(int start_ms, int end_ms) {
        this.start = start_ms;
        this.end = end_ms;
    }


    public double getStartTimeSeconds() {
        return start / 1000.0;
    }

    public double getEndTimeSeconds() {
        return end / 1000.0;
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

 */