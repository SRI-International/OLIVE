package com.sri.speech.olive.api.workflow.wrapper;


import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Workflow;
import com.sri.speech.olive.api.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This a wrapper around a WorkflowTask message, where the internal message has been deserialized into an actual message
public class WorkflowTaskWrapper {

    private static Logger log = LoggerFactory.getLogger(JobDefinitionWrapper.class);



    String taskName;    // aka consumer_result_label
    Olive.TraitType traitType; // FrameScorere, GlobalScorer, etc...
    String taskType;  // SAD, SID, LID, etc...
    Olive.MessageType messageType; // Varies... such as a FrameScorerRequst, AbstractWorkflowPluginTask, ConditionalWorkflowPluginTask or OliveNodeWorkflow
    Message taskMessage;  // The deserialized message
    boolean resultOutput = false;
    String description;

    // Do we need: data label, option mappings, allow_failure

    // do we need the actual message?
    // The deserialized task may or may not have these:
    String pluginName;
    String domainName;

    public WorkflowTaskWrapper(Workflow.WorkflowTask task ){

        taskName = task.getConsumerResultLabel();
        traitType = task.getTraitOutput();
        taskType = task.getTask();
        messageType = task.getMessageType();
        if(task.hasReturnResult()){
            resultOutput = task.getReturnResult();
        }
        if(task.hasDescription()){
            description = task.getDescription();
        }
        else {
            description = "";
        }


        // deserialize the message
        try {
            taskMessage = Server.deserialzieMessage(task.getMessageType(), task.getMessageData());
            // Check if the message has a plugin/domain info
            if(Server.analysis_list.contains(messageType)){
                // get plugin/domain name is available
                Descriptors.FieldDescriptor pluginDescriptor = taskMessage.getDescriptorForType().findFieldByName("plugin");
                pluginName = (String)taskMessage.getField(pluginDescriptor);
                Descriptors.FieldDescriptor domainDescriptor = taskMessage.getDescriptorForType().findFieldByName("domain");
                domainName = (String)taskMessage.getField(domainDescriptor);
            }
            else{
                pluginName = "";
                domainName = "";
            }


        } catch (InvalidProtocolBufferException e) {
            log.error(String.format("Unable to deserialize a workflow task message having type: %s", task.getMessageType()));
        }
    }


    /**
     * The name of this task.  Task names are unique within a job, often this is same as the task name (i.e. SAD)
     *
     * @return the task name
     */
    public String getTaskName() {
        return taskName;
    }

    /**
     * The type of analysis or enrollment trait implemented by this task.  This indicates the type of output produced by
     * this task.
     *
     * @return the analysis or enrollment traint
     */
    public Olive.TraitType getTraitType() {
        return traitType;
    }

    /**
     * The type of task implemented by this task.  This does not imply trait, for example a SAD (task) may have either
     * a Frame Scorer or Region Scorer trait.  So it may be possible to have a workflow that has two SAD tasks, but
     * they would have different traits (SAD - frame scores, SAD - region scores)
     *
     * @return the type of task
     */
    public String getTaskType() {
        return taskType;
    }

    /**
     * The type of the task being requested (i.e REGION_SCORER_REQUEST)
     *
     * @return the
     */
    public Olive.MessageType getMessageType() {
        return messageType;
    }

    /**
     * The acutal task request... may not be useful for clients
     * @return the deserialzied task request
     */
    public Message getTaskMessage() {
        return taskMessage;
    }

    /**
     * True if the results from this task are returend in the workflow results sent to the client.  For example a
     * SAD/FrameScorer task may be a task in this workflow, but the frame scores would not be returned to the
     * client (the frame scores would only be used within the workflow, not as a Workflow output)
     *
     * @return true if this task's output is included in the Workflow results sent to the calling client
     */
    public boolean isResultOutput() {
        return resultOutput;
    }

    /**
     * The name of the plugin used by this task (if known).  Some tasks may not have a plugin name and going forward
     * an acutal plugin name may not be known until executed on the server
     *
     * @return the name of the plugin used by this task.  The plugin nmae may be an empty string if the plugin is not
     * (yet) known, or the task does not use a plugin
     */
    public String getPluginName() {
        return pluginName;
    }

    /**
     * The name of the domain used by this task (if known)
     *
     * @return the name of the domain used by this task.  The plugin name may be an empty string if the domain is not
     * (yet) known, or the task does not use a plugin/domain
     */
    public String getDomainName() {
        return domainName;
    }

    /**
     * Return the (optional) description of this task.  If the underlying Task message does not have a description
     * then an empty string is returned.
     *
     * @return the description, if any, of this task
     */
    public String getDescription() {
        return description;
    }

}
