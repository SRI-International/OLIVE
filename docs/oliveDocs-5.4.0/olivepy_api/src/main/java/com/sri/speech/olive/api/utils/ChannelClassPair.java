package com.sri.speech.olive.api.utils;

public class ChannelClassPair extends Pair<Integer, String> {

    boolean hasClass = false;

    /**
     * Default constructor for use when there are NO channel/class pairs
     */
    public ChannelClassPair(){
        super(-1, "");
        hasClass = false;
    }
    public ChannelClassPair(Integer channelNumber){
        super(channelNumber, "");
        hasClass = false;
    }
    public ChannelClassPair(Integer integer, String s) {
        super(integer, s);
        hasClass = true;
    }

    public Integer getChannel(){
        return getFirst();
    }

    public boolean hasClass(){
        return hasClass;
    }

    public String getClassID(){
        if(hasClass) return getSecond();

        return "";
    }
}
