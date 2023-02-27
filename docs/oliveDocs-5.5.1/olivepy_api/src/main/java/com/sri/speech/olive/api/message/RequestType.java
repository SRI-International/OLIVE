package com.sri.speech.olive.api.message;


/**
 */
public enum RequestType {

    SAD("SAD", "Speech Activity Detection"),
    LID("LID", "Language Identification"),
    SID("SID", "Speaker Identification"),
    KWS("KWS", "Keyword Spotting"),
    HIGHLIGHT("SPEAKER_HIGHLIGHT", "Speaker Highlighting");

    private String name;
    private String desc;

    RequestType(String name, String desc){
        this.name = name;
        this.desc = desc;
    }

    @Override
    public String toString() {
        return name;
    }

    public String getDescription(){
        return desc;
    }

    /*@Override
    public int getTypeValue() {
        return type;
    }*/

    // don't use getValueOf(string), use this instead
    public static RequestType getValueFromName(String value){

        for(RequestType type : values()){
            if(type.toString().equals(value)){
                return type;
            }
        }

        throw new IllegalArgumentException("No " +  RequestType.class.getName() + " defined for name: : " + value);

    }

}
