package com.sri.speech.olive.api.workflow;

import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Workflow;
import com.sri.speech.olive.api.Server;
import com.sri.speech.olive.api.utils.Pair;
import com.sri.speech.olive.api.workflow.wrapper.JobDefinitionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * An actualized WorkflowDefinition that can be used to make analysis or enrollment requests
 */
public class ActivatedWorkflow extends BaseWorkflow {

    private static Logger log = LoggerFactory.getLogger(ActivatedWorkflow.class);

    private Server server;

    /**
     *Create an "executable" Workflow from a WorkflowDefinition that was activated/actualized by the specified server
     * @param actualizedWorkflow a WorkflowDefinition that has been actualized by an OLIVE server and can be executed
     * @param server the server used to actualize this workflow
     */
    public ActivatedWorkflow(Workflow.WorkflowDefinition actualizedWorkflow, Server server) throws WorkflowException {
        super(actualizedWorkflow);

        // Workflow must be actualized by a server
        if (!actualizedWorkflow.getActualized()){
            throw  new WorkflowException("Workflow not actualized");
        }

        this.server = server;
    }


    /**
     * Use this method to wrap data to be submitted in a Workflow in a WorkflowDataRequest message.
     *
     * @param audio the audio to submit
     * @param label a name or label to associate with this audio
     *
     * @return Audio wrapped in a WorkflowDataRequest, which can be submitted in a Workflow reqeusts
     *
     */
    public Workflow.WorkflowDataRequest packageAudio(Olive.Audio audio, String label){
        Workflow.WorkflowDataRequest.Builder builder = Workflow.WorkflowDataRequest.newBuilder()
                //.setConsumerDataLabel("audio")  // no need to set - should be audio
                .setDataId(label)
                .setDataType(Olive.InputDataType.AUDIO)
                .setWorkflowData(audio.toByteString());
        // We could bind this audio to a job, but not supporting that for now

        return builder.build();
    }

    /**
     * Use this method to wrap data to be submitted in a Workflow in a WorkflowDataRequest message.
     *
     * @param media the binary media (image, video, or non-decoded audio)  to submit
     * @param label a name or label to associate with this input
     *
     * @return Audio wrapped in a WorkflowDataRequest, which can be submitted in a Workflow reqeusts
     *
     */
    public Workflow.WorkflowDataRequest packageBinaryMedia(Olive.BinaryMedia media, String label){
        Workflow.WorkflowDataRequest.Builder builder = Workflow.WorkflowDataRequest.newBuilder()
                .setDataId(label)
                .setDataType(Olive.InputDataType.BINARY)
                .setWorkflowData(media.toByteString());

        return builder.build();
    }

    /**
     * Make an analysis reqeust
     * @param data
     * @param rc
     * @param options
     */
    public void analyze(List<Workflow.WorkflowDataRequest> data,
                        Server.ResultCallback<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> rc,
                        List<Pair<String, String>> options){


        Workflow.WorkflowAnalysisRequest.Builder msg = Workflow.WorkflowAnalysisRequest.newBuilder()
                .setWorkflowDefinition(workflowDefinition);

        for (Workflow.WorkflowDataRequest wdr : data){
            msg.addWorkflowDataInput(wdr);
        }

        // todo add options

        // Submit the request
        server.enqueueRequest(msg.build(), rc);

//        Workflow.WorkflowAnalysisRequest.Builder builder = Workflow.WorkflowAnalysisRequest.newBuilder()
//                .

    }

    /**
     * Request the current classes available for the tasks used in a workflow analysis
     *
     * @param rc
     */
    public void currentClasses(Server.ResultCallback<Workflow.WorkflowClassStatusRequest, Workflow.WorkflowClassStatusResult> rc,
                               Workflow.WorkflowType type){

        Workflow.WorkflowClassStatusRequest.Builder msg = Workflow.WorkflowClassStatusRequest.newBuilder()
                .setWorkflowDefinition(workflowDefinition)
                .setType(type);

        // Submit the request
        server.enqueueRequest(msg.build(), rc);
    }

    /**
     * Request the current classes available for the tasks used in a workflow analysis
     *
     * @param rc
     */
    public void currentClasses(Server.ResultCallback<Workflow.WorkflowClassStatusRequest, Workflow.WorkflowClassStatusResult> rc){
        currentClasses(rc, Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && this.server.equals( ((ActivatedWorkflow)o).server);
    }

    @Override
    public int hashCode() {
        return workflowDefinition.hashCode() + server.getHostAddress().hashCode();
    }


    // todo method to package audio, image, video, text

}
