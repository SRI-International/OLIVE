package com.sri.speech.olive.api.utils.parser.pem;

/**
 * The underlying PEM data container
 */
public class PemRecord {

    public String getSourceID() {
        return sourceID;
    }

    public String getChannel() {
        return channel;
    }

    public String getClassLabel() {
        return classLabel;
    }

    public double getStartTimeSeconds() {
        return startTimeSeconds;
    }

    public double getEndTimeSeconds() {
        return endTimeSeconds;
    }

    public int getStartTimeMS() {
        return (int)(startTimeSeconds*1000);
    }

    public int getEndTimeMS() {
        return (int)(endTimeSeconds*1000);
    }

    private String sourceID;
    private String channel;
    private String classLabel;
    private float startTimeSeconds;
    private float endTimeSeconds;


    public PemRecord(String id, String ch, String classID, Float startTimeSeconds, Float endTimeSeconds) throws PemException {
        this.sourceID       = id.trim();
        this.channel        = ch.trim();
        this.classLabel     = classID.trim();
        this.startTimeSeconds = startTimeSeconds;
        this.endTimeSeconds = endTimeSeconds;

        // TODO HANDLE A RANGE OF CHANNELS?

        if (startTimeSeconds > endTimeSeconds){
            throw new PemException(String.format("Invalid start (%.2f) and end (%.2f) regions.  ", startTimeSeconds, endTimeSeconds));
        }


    }

    /**
     * Duration of this record in seconds
     *
     * @return duration in seconds
     */
    public double getDuration(){
        return  endTimeSeconds - startTimeSeconds;
    }

    @Override
    public String toString(){
        return String.format("%s %s %s %.2f %.2f", sourceID, channel, classLabel, startTimeSeconds, endTimeSeconds);
    }

    @Override
    public boolean equals(Object o){
        if (this == o) return  true;
        if (o == null || getClass() != o.getClass()) return false;

        PemRecord other = (PemRecord)o;

        if (sourceID.equals(other.sourceID))
            if (channel.equals(other.channel))
                if (classLabel.equals(other.classLabel))
                    if (startTimeSeconds == other.startTimeSeconds)
                        if (endTimeSeconds == other.endTimeSeconds){
                            return true;
                       }

        return false;
    }
}
