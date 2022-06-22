package com.sri.speech.olive.api.workflow.wrapper;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Used as a sorta wrapper aound a WorkflowTaskResult, except the internal analysis message is deserialized into an
 * actual message and not a byte stream.
 *
 */
public class TaskResult {

    private static Logger log = LoggerFactory.getLogger(TaskResult.class);

    // If this task failed, then it will have an error message:
    boolean isError;

    String errMsg;

    String jobName;
    String taskName;
    String pluginID;
    String domainID;
    Olive.TraitType traitType;
    String taskType;
    Olive.MessageType messageType;
    Message taskMessage;
    List<DataResult> dataResults;

    // trait (global scorer), task type (SAD), error (if any), analysis (deserialized)
    public TaskResult(Olive.WorkflowTaskResult taskResult, String jobName, List<DataResult> dataResults ){

        this.jobName = jobName;
        taskName = taskResult.getTaskName();
        traitType = taskResult.getTaskTrait();
        taskType = taskResult.getTaskType();
        messageType = taskResult.getMessageType();
        this.dataResults = dataResults;

        if (taskResult.hasError()){
            this.isError = true;
            this.errMsg = taskResult.getError();
            return;
        }
        else {
            this.isError = false;
        }

        if (taskResult.hasPlugin()){
            pluginID = taskResult.getPlugin();
        }
        else{
            pluginID = "";
        }
        if (taskResult.hasDomain()){
            domainID = taskResult.getDomain();
        }
        else{
            domainID = "";
        }


        // May not have a task message if there was an error, of if the protobuf definition changes then we could
        // also have an error
        try {
            taskMessage = Server.deserialzieMessage(taskResult.getMessageType(), taskResult.getMessageData());
        } catch (InvalidProtocolBufferException e) {
            this.isError = true;
            this.errMsg = String.format("Unable to deserialize a workflow task message having type: %s", taskResult.getMessageType());
            log.error(this.errMsg);
        }
    }

    public String getErrMsg() {
        return errMsg;
    }

    public String getJobName() {
        return jobName;
    }

    public String getTaskName() {
        return taskName;
    }

    public Olive.TraitType getTraitType() {
        return traitType;
    }

    public String getTaskType() {
        return taskType;
    }

    public Message getTaskMessage() {
        return taskMessage;
    }
    public boolean isError(){
        return isError;
    }

    public String getPluginID() {
        return pluginID;
    }

    public String getDomainID(){
        return domainID;
    }
}
