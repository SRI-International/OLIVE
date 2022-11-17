package com.sri.speech.olive.api.workflow.wrapper;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Workflow;
import com.sri.speech.olive.api.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used as a sorta wrapper aound a WorkflowDataResult, except the internal data message is deserialized into an
 * actual message and not a byte stream.
 *
 */
public class DataResult {

    private static Logger log = LoggerFactory.getLogger(DataResult.class);

    // If this task failed, then it will have an error message:
    boolean isError;

    public String getErrMsg() {
        return errMsg;
    }

    public String getJobName() {
        return jobName;
    }

    public String getDataName() {
        return dataName;
    }

    public Olive.MessageType getDataType() {
        return dataType;
    }




    String errMsg;

    String jobName;
    String dataName;
    Olive.MessageType dataType;
    Message dataMessage;

    public DataResult(Workflow.WorkflowDataResult dataResult, String jobName ){

        this.jobName = jobName;
        dataName = dataResult.getDataId();

        dataType = dataResult.getMsgType();

        if (dataResult.hasError()){
            this.isError = true;
            this.errMsg = dataResult.getError();
            return;
        }
        else {
            this.isError = false;
        }

        // May not have a task message if there was an error, of if the protobuf definition changes then we could
        // also have an error
        try {
            dataMessage = Server.deserialzieMessage(dataResult.getMsgType(), dataResult.getResultData());
        } catch (InvalidProtocolBufferException e) {

            this.isError = true;
            this.errMsg = String.format("Unable to deserialize a workflow data message having type: %s", dataResult.getMsgType());
            log.error(this.errMsg);
        }


    }

    public boolean isError(){
        return isError;
    }

    public Message getDataMessage() {
        return dataMessage;
    }
}
