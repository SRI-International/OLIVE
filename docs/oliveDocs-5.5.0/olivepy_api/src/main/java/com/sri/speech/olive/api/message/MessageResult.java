package com.sri.speech.olive.api.message;

/**
 */
public class MessageResult<RESPONSE> {

    private final RESPONSE response;
    private final String errorMsg;
    private boolean isError;

    /**
     * Constructor for a non-error response
     *
     * @param rsp the response
     */
    public MessageResult(RESPONSE rsp){
        this.response = rsp;
        this.errorMsg = null;
        isError = false;
    }

    /**
     * Constructor for an error  response
     *
     * @param errorMsg the error message
     */
    public MessageResult(String errorMsg){
        this.response = null;
        this.errorMsg = errorMsg;
        isError = true;
    }

    /**
     *
     * @return the message response, or NULL if error
     */
    public RESPONSE getResponse(){
        return response;
    }

    public boolean isError(){
        return isError;
    }

    public String getError(){
        if(isError){
            return  errorMsg;
        }
        return  null;

    }


}
