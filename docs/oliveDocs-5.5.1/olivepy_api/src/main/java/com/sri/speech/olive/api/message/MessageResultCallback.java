package com.sri.speech.olive.api.message;


/**
 */
public interface MessageResultCallback<RESPONSE> {

    public void result(final MessageResult<RESPONSE> rep);

}
