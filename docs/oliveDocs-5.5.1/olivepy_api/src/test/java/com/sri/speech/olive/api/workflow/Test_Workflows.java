package com.sri.speech.olive.api.workflow;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Workflow;
import com.sri.speech.olive.api.Server;
import com.sri.speech.olive.api.utils.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.file.*;

public class Test_Workflows {

    private static Logger log = LoggerFactory.getLogger(Test_Workflows.class);

    public static final int scenicPort      = 5588;
    public static final String scenicHost   = "localhost";
    private static final int TIMEOUT        = 10000;  // 10 seconds is enough?

    private Server server;


    public Test_Workflows() throws  Exception{

        // Create the server, skiping tests if we can't connect
        server = new Server();
        server.connect("Scenic_Test", scenicHost, scenicPort, scenicPort +1, 100);

        long start_t = System.currentTimeMillis();
        while (!server.getConnected().get() && System.currentTimeMillis() - start_t < TIMEOUT) {
            try {
                synchronized (server.getConnected()) {
                    server.getConnected().wait(TIMEOUT);
                }
            } catch (InterruptedException e) {
                // Keep waiting
            }
        }

        if (server.getConnected().get()) {
            //  request current plugins...
            //pluginList  = ClientUtils.requestPlugins(server);
            // todo do something?
            log.debug("connected to OLIVE server");
        }
    }

    private void init(){
        if (!server.getConnected().get()) {
            log.error("Unable to connect to the OLIVE server: {}", scenicHost);
            server.disconnect();
            throw new SkipException("Unable to connect to server");
        }

    }

    @Test
    /**
     * Create a full Workflow lifecycle where we create a (abstract) WorkflowDefinition (WD)
     */
    public void testWorkflowWithError() throws Exception {

//        Workflow.WorkflowDefinition wd = Workflow.WorkflowDefinition.parseFrom(new FileInputStream("/tmp/sad_lid.workflow"));


        // This WD /should/ be provided to this client as a client should not be creating WD
        // TASK 1: Request a SAD plugin that will return (fake) scores:
        Workflow.AbstractWorkflowPluginTask.Builder goodSAD = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("tel"));

        //TASK 2: (error) And request a SAD test plugin that will generate an exception
        Workflow.AbstractWorkflowPluginTask.Builder badSAD = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("exception").build());

        // TASK 3: Try including an intra-node that converts frame scores to region scores:
        Workflow.OliveNodeWorkflow.Builder oliveNode = Workflow.OliveNodeWorkflow.newBuilder()
                .setLabel("unused")
                .setNodeHandler("workflow_sad_frames_to_regions");
//                .setNodeResultHandler("tbd");

        // Create a WorkflowJobDefiniton  that includes two SAD reqeusts, one using a valid plugin and one using a SAD plugin that
        // that will generate an exception when scoring
        Workflow.DataHandlerProperty.Builder props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName("sad-frames,bad-sad_sad-regions")
                .setDataProperties(props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(goodSAD.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SAD_Good")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(badSAD.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD_Bad") //allow_failure is optional, but default this is TRUE
                    .setReturnResult(true)).
                addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.OLIVE_NODE)
                        .setMessageData(oliveNode.build().toByteString())
                        .setConsumerDataLabel("SAD_Good")
                        .setConsumerResultLabel("SAD_Frames_As_Regions")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        // Package the WorkflowJob in a WorkflowDefinition
        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        // SAVE workflow..
//        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/Users/e24652/audio/testSuite/workflows/sad_exception_frames_workflow.wkf").toFile());
//        wfd.build().writeTo(fos);

        // Have the server convert this WFD into a Workflow
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        // Make a sync request:
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }
        if(result.getRep().hasError()){
            //result.getRep()
            Assert.fail("Workflow request message failed: " + result.getRep().getError());
        }

        // Now we have our executable workflow - make an analysis request
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        // Send the analysis request:
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);

        // The first SAD request should have worked, but the second should have failed
        if(analysisResult.hasError()){
            Assert.fail("Workflow analysis request failed: " + analysisResult.getError());
        }
        if(analysisResult.getRep().hasError()){
            Assert.fail("Workflow analysis result failed: " + analysisResult.getRep().getError());
        }

        for(Workflow.WorkflowJobResult jobResult : analysisResult.getRep().getJobResultList()){
            // We only expect to have one job
            if(jobResult.hasError()){
                System.out.println(String.format("Job failed with error: %s", jobResult.getError()));
            }

            for(Workflow.WorkflowTaskResult wtr : jobResult.getTaskResultsList()) {
                if(wtr.hasError()){
                    System.out.println(String.format("Task '%s' failed with error: %s", wtr.getTaskName(), wtr.getError()));
                }
                else{
                        System.out.println(String.format("Task '%s' was successful", wtr.getTaskName()));
                    }
            }
            for(Workflow.WorkflowDataResult wdr : jobResult.getDataResultsList()){
                Message dataMsg = server.deserialzieMessage(wdr.getMsgType(), wdr.getResultData());
                System.out.println(String.format("Data '%s' processed: %s ", wdr.getDataId(), dataMsg.toString()));


            }
            // todo 'SAD_Bad' should have an error, while 'SAD_Good' should have produced a result
        }
    }

    @Test
    public void testWorkflowWithBadData() throws Exception {

        // TASK 1: Request a SAD plugin that will return (fake) scores:
        Workflow.AbstractWorkflowPluginTask.Builder goodSAD = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("tel"));
        // TASK 2: Try including an intra-node that converts frame scores to region scores:
        Workflow.OliveNodeWorkflow.Builder oliveNode = Workflow.OliveNodeWorkflow.newBuilder()
                .setLabel("unused")
                .setNodeHandler("workflow_sad_frames_to_regions");
//                .setNodeResultHandler("tbd");

        // Create a WorkflowJob  that includes two SAD reqeusts, one using a valid plugin and one using a SAD plugin that
        // that will generate an exception when scoring
        Workflow.DataHandlerProperty.Builder props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName("sad-good_sad-bad")
                .setDataProperties(props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(goodSAD.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SAD_Good")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                                .setTask("SAD")
                                .setTraitOutput(Olive.TraitType.REGION_SCORER)
                                .setMessageType(Olive.MessageType.OLIVE_NODE)
                                .setMessageData(oliveNode.build().toByteString())
                                .setConsumerDataLabel("SAD_Good")
                                .setConsumerResultLabel("SAD_Frames_As_Regions")
                                .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        // Package the WorkflowJob in a WorkflowDefinition
        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        // Have the server convert this WFD into a Workflow
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        // Make a sync request:
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }
        if(result.getRep().hasError()){
            //result.getRep()
            Assert.fail("Workflow request message failed: " + result.getRep().getError());
        }


        // Now we have our executable workflow - make an analysis request
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/movie_stars.m4a");
        // Send the analysis request:
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);

        // The audio can't be opened, so we should see an error in the :
        if(analysisResult.hasError()){
            System.out.println("Bad audio error message (EXPECTED): " + analysisResult.getError());
        }
        else {
            Assert.fail("Workflow analysis SHOULD have failed ");
        }
//        if(analysisResult.getRep().hasError()){
//            System.out.println("Workflow analysis result failed error message: " + analysisResult.getRep().getError());
//        }
//        else {
//            Assert.fail("Workflow analysis exptected to fail due to bad audio input");
//        }

    }

    @Test
    public void testWorkflowWithStereoFramesAndRegions() throws Exception {

        // TASK 1: Request a SAD plugin that will return (fake) scores:
        Workflow.AbstractWorkflowPluginTask.Builder goodSAD = Workflow.AbstractWorkflowPluginTask.newBuilder()

                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("tel"));

        // TASK 2: Try including an intra-node that converts frame scores to region scores:
        Workflow.OliveNodeWorkflow.Builder oliveNode = Workflow.OliveNodeWorkflow.newBuilder()
                .setLabel("unused")
                .setNodeHandler("workflow_sad_frames_to_regions");
//                .setNodeResultHandler("tbd");

        // Create a WorkflowJob  that includes two SAD reqeusts, one using a valid plugin and one using a SAD plugin that
        // that will generate an exception when scoring

        Workflow.DataHandlerProperty.Builder props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.SPLIT);


        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName("sad-good_F2R")
                .setDataProperties(props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(goodSAD.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SAD_Good")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.OLIVE_NODE)
                        .setMessageData(oliveNode.build().toByteString())
                        .setConsumerDataLabel("SAD_Good")
                        .setConsumerResultLabel("SAD_Frames_As_Regions")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        // Package the WorkflowJob in a WorkflowDefinition
        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        // Have the server convert this WFD into a Workflow
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        // Make a sync request:
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }
        if(result.getRep().hasError()){
            //result.getRep()
            Assert.fail("Workflow request message failed: " + result.getRep().getError());
        }

        // Now we have our executable workflow - make an analysis request
        // TODO SET DATA DIRECTORY?
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/test_8k_2ch.wav");
        // Send the analysis request:
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);

        // The first SAD request should have worked, but the second should have failed
        if(analysisResult.hasError()){
            Assert.fail("Workflow analysis message failed: " + analysisResult.getError());
        }

        for(Workflow.WorkflowJobResult jobResult : analysisResult.getRep().getJobResultList()){
            // We only expect to have one job
            if(jobResult.hasError()){
                System.out.println(String.format("Job failed with error: %s", jobResult.getError()));
            }

            for(Workflow.WorkflowTaskResult wtr : jobResult.getTaskResultsList()) {
                if(wtr.hasError()){
                    System.out.println(String.format("Task '%s' failed with error: %s", wtr.getTaskName(), wtr.getError()));
                }
                else{
                    System.out.println(String.format("Task '%s' was successful", wtr.getTaskName()));
                }
            }
            for(Workflow.WorkflowDataResult wdr : jobResult.getDataResultsList()){
                Message dataMsg = server.deserialzieMessage(wdr.getMsgType(), wdr.getResultData());
                System.out.println(String.format("Data '%s' processed: %s ", wdr.getDataId(), dataMsg.toString()));


            }
        }

    }

    @Test
    public void testWorkflowWithStereo() throws Exception {

        String job_name = "Multi Channel SAD Example";
        String file_prefix = "sad_multi_channel";

        // TASK 1: Request a SAD plugin:
        Workflow.AbstractWorkflowPluginTask.Builder goodSAD = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        // Create a WorkflowJob  that will handled each channel in an audio submission
        Workflow.DataHandlerProperty.Builder props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.SPLIT);


        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(goodSAD.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SAD")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        // Package the WorkflowJob in a WorkflowDefinition
        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        Path path = FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow");
        FileOutputStream fos = new FileOutputStream( path.toFile());
        wfd.build().writeTo(fos);

        System.out.println("Saved abstract workflow as: " + path.toString());


    }

    private Workflow.WorkflowAnalysisRequest createAnalysisMessage(Workflow.WorkflowDefinition workflowDef, String audioFilename) throws IOException, UnsupportedAudioFileException {
        // We assume our workflow only has one order, which has one job

        // Create the data message for our filename
        Olive.Audio.Builder audio  = ClientUtils.createAudioFromFile(audioFilename, 0, ClientUtils.AudioTransferType.SEND_AS_PATH, null);

        Workflow.WorkflowDataRequest.Builder dataRequest = Workflow.WorkflowDataRequest.newBuilder()
                .setDataId(Paths.get(audioFilename).getFileName().toString())
                .setDataType(Olive.InputDataType.AUDIO)
                .setWorkflowData(audio.build().toByteString());

        // Option example
//        Olive.OptionValue.Builder ov = Olive.OptionValue.newBuilder().setName("select_best").setValue("False");

        Workflow.WorkflowAnalysisRequest.Builder msg = Workflow.WorkflowAnalysisRequest.newBuilder()
                .setWorkflowDefinition(workflowDef)
//                .addOption(ov)
                .addWorkflowDataInput(dataRequest);

        // no options - yet

        return msg.build();

    }

    private Workflow.WorkflowAnalysisRequest createTextAnalysisMessage(Workflow.WorkflowDefinition workflowDef, String textInput, String id) throws IOException, UnsupportedAudioFileException {
        // We assume our workflow only has one order, which has one job
        // Create the data message for our filename
        Olive.Text.Builder text = Olive.Text.newBuilder().addText(textInput);
        Workflow.WorkflowDataRequest.Builder dataRequest = Workflow.WorkflowDataRequest.newBuilder()
                .setDataId(id)
                .setDataType(Olive.InputDataType.TEXT)
                .setWorkflowData(text.build().toByteString());

        Workflow.WorkflowAnalysisRequest.Builder msg = Workflow.WorkflowAnalysisRequest.newBuilder()
                .setWorkflowDefinition(workflowDef)
                .addWorkflowDataInput(dataRequest);

        return msg.build();
    }

    @Test
    /**
     */
    public void testCreatingWorkflowDef_SADONLY() throws Exception {

        String job_name = "SAD workflow";
        String file_prefix = "sad";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_NEWEST_VERSION));


        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
     /*   Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }*/

    }

    @Test
    /**
     */
    public void testCreatingBadAbstractWorkflow() throws Exception {

        String job_name = "Bad Abstract SAD and LID workflow";
        String file_prefix = "test_bad_abstract_sad";


        // This should be an invalid SAD request - NO domain with the name 'testing'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn"))
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_NEWEST_VERSION))
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_STARTS).setValue("testing"));

        // generic enough this should be okay
        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("lid-embed"))
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi").build());

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        /*Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }*/

    }

    @Test
    /**
     */
    public void test_SAD_PIM_FRAMES_TO_REGIONS() throws Exception {

        String job_name = "Mock SAD frames PIM Regions";
        String file_prefix = "sad_pim_regions";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_NEWEST_VERSION));


        Workflow.ScoreOutputTransformRequest.Builder transform_pit_task = Workflow.ScoreOutputTransformRequest.newBuilder()
                .setPlugin("pim-transformFrames").setDomain("test-sad")
                .setTraitInput(Olive.TraitType.FRAME_SCORER)
                .setTraitOutput(Olive.TraitType.REGION_SCORER);

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD REGIONS")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.SCORE_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(transform_pit_task.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("SAD REGIONS")
                        .setDescription("SAD Frame Conversion")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD").setPluginKeywordName("scores").build())
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }


    }

    @Test
    /**
     */
    public void testCreatingWorkflowDef_SAD_LID() throws Exception {

        String job_name = "Basic SAD and LID workflow";
        String file_prefix = "sad_lid";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_NEWEST_VERSION));


        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("lid-embedplda").build());
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }
        // Save the actualized workflow request
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_actualize_request.workflow").toFile());
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualize_request.workflow").toFile());
//        wr.build().writeTo(fos);
        // Save the actualized workflow
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_actualized_workflow").toFile());
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualized_workflow").toFile());
//        result.getRep().getWorkflow().writeTo(fos);

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
            Assert.fail("Workflow Analysis request message failed: " + result.getError());
        }

        // Save the actualize workflow request
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_analysis_request.workflow").toFile());
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_request.workflow").toFile());
//        analysisRequest.writeTo(fos);

        // And finally, save the result:
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_analysis_result.workflow").toFile());
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_result.workflow").toFile());
//        analysisResult.getRep().writeTo(fos);




    }

    @Test
    /**
     * Use this "test" to create protobufs for testing
     */
    public void testCreatingWorkflowDefActual() throws Exception {

        String job_name = "job_sad_lid_sid_real";
        String file_prefix = "sad-lid-sid_real";

//        String[] pluginNames = ['sad-dnn',];
//        String[] domainNames = ['multi']

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("lid-embedplda").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder sid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sid-dplda").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }
        // Save the actualized workflow request
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_actualize_request.workflow").toFile());
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualize_request.workflow").toFile());
        wr.build().writeTo(fos);
        // Save the actualized workflow
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_actualized_workflow").toFile());
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualized_workflow").toFile());
        result.getRep().getWorkflow().writeTo(fos);

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
            Assert.fail("Workflow Analysis request message failed: " + result.getError());
        }
        // Save the actualize workflow request
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_analysis_request.workflow").toFile());
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_request.workflow").toFile());
        analysisRequest.writeTo(fos);

        // And finally, save the result:
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_analysis_result.workflow").toFile());
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_result.workflow").toFile());
        analysisResult.getRep().writeTo(fos);




    }


    @Test
    /**
     * Use this "test" to create protobufs for testing
     */
    public void testCreatingWorkflowDef() throws Exception {

        String job_name = "job_sad_lid_sid";
        String file_prefix = "sad-lid-sid";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("lid-embedplda").build());
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder sid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sid-dplda").build());
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){

            String err = result.getError();
            if (null == err){
                err = result.getRep().getError();
            }
            Assert.fail("Workflow request message failed: " + err);
        }
        // Save the actualized workflow request
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_actualize_request.workflow").toFile());
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualize_request.workflow").toFile());
        wr.build().writeTo(fos);
        // Save the actualized workflow
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_actualized_workflow").toFile());
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualized_workflow").toFile());
        result.getRep().getWorkflow().writeTo(fos);

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
            Assert.fail("Workflow Analysis request message failed: " + result.getError());
        }
        // Save the actualize workflow request
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_analysis_request.workflow").toFile());
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_request.workflow").toFile());
        analysisRequest.writeTo(fos);

        // And finally, save the result:
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_analysis_result.workflow").toFile());
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_result.workflow").toFile());
        analysisResult.getRep().writeTo(fos);




    }

    @Test
    /**
     * Use this "test" to create protobufs for testing
     */
    public void testCreatingWorkflow_SAD_LID_SID_SDD_QBE_Workflow() throws Exception {

        String job_name = "Multi Job Types";
        String file_prefix = "sad-lid-sid-sdd-qbe";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("lid-embedplda").build());
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder sid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sid-dplda").build());
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

//        Olive.RegionScorerRequest.Builder qbe_task = Olive.RegionScorerRequest.newBuilder().setPlugin("qbe-tdnn").setDomain("multi-v1");
        Workflow.AbstractWorkflowPluginTask.Builder qbe_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("qbe-tdnn").build());

        Workflow.AbstractWorkflowPluginTask.Builder sdd_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sdd-sbcEmbed").build());
//        Olive.RegionScorerRequest.Builder sdd_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sdd-sbcEmbed").setDomain("telClosetalk-v1");

        // Case in-sensitive:
//        Workflow.AbstractWorkflowPluginTask.Builder sdd_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
//                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_STARTS).setValue("sdd-sbcembed").build())
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_STARTS).setValue("telClosetalk"));

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("Test Workflow Job with multiple types of scorers")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true)
                        .setDescription("SAD as a Region Scorer"))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setReturnResult(true)
                        .setDescription("LID as a Global Scorer"))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true)
                        .setDescription("SID as a Global Scorer"))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("QBE")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(qbe_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("QBE")
                        .setReturnResult(true)
                        .setDescription("QBE as a Region Scorer"))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SDD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
//                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sdd_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SDD")
                        .setReturnResult(true)
                        .setDescription("SDD as a Region Scorer"));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setDescription("Test Workflow Definition description")
                .setCreated( Olive.DateTime.newBuilder().setYear(2020).setMonth(12).setDay(1).setHour(9).setMin(0).setSec(0))
                .setUpdated( Olive.DateTime.newBuilder().setYear(2020).setMonth(12).setDay(1).setHour(9).setMin(0).setSec(0))
                .setVersion("0.1.0")
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }
        // Save the actualized workflow request
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_actualize_request.workflow").toFile());
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualize_request.workflow").toFile());
//        wr.build().writeTo(fos);
        // Save the actualized workflow
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_actualized_workflow").toFile());
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualized_workflow").toFile());
//        result.getRep().getWorkflow().writeTo(fos);

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        checkAnalysisError(analysisResult);

        // Save the actualize workflow request
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_analysis_request.workflow").toFile());
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_request.workflow").toFile());
//        analysisRequest.writeTo(fos);

        // And finally, save the result:
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/sad_lid_sid_analysis_result.workflow").toFile());
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_result.workflow").toFile());
//        analysisResult.getRep().writeTo(fos);


    }

    @Test
    public void test2JobsSADLIDthenSIDWorkflowDef() throws Exception {

        String analysisjob_name = "Mono SAD LID";

        String file_prefix = "test_mono_sad_lid_stereo_sid";

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build());
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("lid-embed").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder sid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sid-embed"))
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multicond").build());

        // For SAD and LID use mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder sad_data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);
        // For SID process each channel, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder sid_data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.SPLIT);

        // Create a single job, with 3 tasks (SAD and LID and SID), all results should be returned to the client


        Workflow.JobDefinition.Builder sadAnalysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(sad_data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
//                        .addClassId("eng")
//                        .addClassId("spa")
                        .setConsumerResultLabel("LIDCLG")
                        .setReturnResult(true));

        Workflow.JobDefinition.Builder sidAnalysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName("Stereo SID")
//                .setJobName(analysisjob_name)
                .setDataProperties(sid_data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true));

        // Now create the orders
        Workflow.WorkflowOrderDefinition.Builder analysisOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(sadAnalysisJob)
                .addJobDefinition(sidAnalysisJob)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            }else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }

        // Save the actualized workflow request
/*        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualize_request.workflow").toFile());
        wr.build().writeTo(fos);
        // Save the actualized workflow (response)
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualized_workflow").toFile());
        result.getRep().getWorkflow().writeTo(fos);

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
            Assert.fail("Workflow Analysis request message failed: " + result.getError());
        }

        // Save the actualize workflow request
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_request.workflow").toFile());
        analysisRequest.writeTo(fos);

        // And finally, save the result:
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_result.workflow").toFile());
        analysisResult.getRep().writeTo(fos);*/




    }

    @Test
    /**
     */
    public void testCreating_test_CONDITIONAL_ASR_Analysis() throws Exception {

        //String job_name = "Multi Conditional TEST Workflow";
        String file_prefix = "test_multi_conditional_job";

        // Create a workflow definition that does SAD (frames), LID, CONDITIONAL PIMENTO, then in a new job it does
        // ASR using plugin/domains from the pimento.

        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi-v1"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("lid").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi-v1"));

        // todo - create a new plugin (pimento) that will take in GS from LID and then pick ASR plugin and domain(s), returns
        // one or more task or jobs? -- for ASR prolly better as task since we may want to run those sequential due to memory and then our
        // new composer (?) task will
        Workflow.Plugin2PluginRequest.Builder pimento_task = Workflow.Plugin2PluginRequest.newBuilder()
                .setPlugin("chooser")
                .setDomain("test-lid-asr")
                .addOption(Olive.OptionValue.newBuilder().setName("foo").setValue("bar").build());

//        Workflow.DynamicPluginRequest.Builder dynamic_asr_task = Workflow.DynamicPluginRequest.newBuilder().setConditionalTaskName("CONDITIONAL_ASR");

        Workflow.OliveNodeWorkflow.Builder oliveConditionalNode = Workflow.OliveNodeWorkflow.newBuilder()
                .setLabel("Convert Frame Scores to Region Scores ")
                .setNodeHandler("workflow_sad_frames_to_regions");
//                .setNodeResultHandler("tbd");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName("Multi Conditional TEST Workflow")
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("Test conditional processing")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // IMPORTANT - THIS JOB MUST FINISH BEFORE THE DYNAMIC JOB CAN BE START
                .addTransferResultLabels("audio") // Let OLIVE know to transfer the /same/ audio to downstream jobs
                .addTransferResultLabels("CONDITIONAL_ASR")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD_FRAMES")
                        .setDescription("SAD Frame Scores")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD_FRAMES").setPluginKeywordName("speech_frames").build())
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("VTD").setPluginKeywordName("speech_regions").build())
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("DYNAMIC_ASR") // todo figure out task name and trait info..
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)  // TODO THIS IS THE WRONG TYPE...
                        .setMessageData(pimento_task.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes lid global scores, no data input
                        .setConsumerResultLabel("CONDITIONAL_ASR")
                        .setDescription("ASR Conditional Task Composer")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("LID").setPluginKeywordName("global_scores").build())
                        .setAllowFailure(false)
                        .setReturnResult(false))
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("VTD_REGIONS")
//                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
//                        .setMessageType(Olive.MessageType.OLIVE_NODE)
//                        .setMessageData(oliveNode.build().toByteString())
//                        .setConsumerDataLabel("VTD")
//                        .setConsumerResultLabel("VTD_Frames_2_Regions")
//                        .setReturnResult(true));
                ;


        // Create a dynamic job, that will be completed based on the results of the previous job
        Workflow.DynamicPluginRequest.Builder dynamic_asr_task = Workflow.DynamicPluginRequest.newBuilder().setConditionalTaskName("CONDITIONAL_ASR");
        Workflow.JobDefinition.Builder dynamic_job = Workflow.JobDefinition.newBuilder()
                .setJobName("Dynamic ASR")
                .addDynamicJobName("Multi Conditional TEST Workflow")  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_props)                         // these should be ignored... since we want to use upstream data but how to specify/allow that?
                .setDescription("Test dynamic selection of the ASR plugin/domain")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // may not matter in this case --
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_asr_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Dynamic ASR Task")
                        // Maybe it takes options...
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD_FRAMES").setPluginKeywordName("speech_frames").build())
                        .setReturnResult(true));

        // And finally, create a SAD region scoring job
        Workflow.JobDefinition.Builder sadOnlyJob = Workflow.JobDefinition.newBuilder()
                .setJobName("SAD Only Job")
                .setDataProperties(data_props)
                .setDescription("Test conditional processing")
                //.setProcessingType(Workflow.WorkflowJobType.)  // WE DON'T CARE ABOUT THE PROCESSING TYPE FOR THIS JOB
                .addTransferResultLabels("audio") ///  don't need to do this... it should get audio from the previous job
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD_FRAMES")
                        .setDescription("SAD Frame Scores")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(dynamic_job)
//                .addJobDefinition(sadOnlyJob)  // todo restore - but will have to update unit tests in Python!
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            if (result.hasError()) {
                Assert.fail("Workflow actualize request message failed: " + result.getError());
            }
            else {
                Assert.fail("Workflow actualize request message failed: " + result.getRep().getError());
            }
        }

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
            if (analysisResult.hasError()) {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getError());
            }
            else {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getRep().getError());
            }
        }
    }

    @Test
    public void test_ASR_WorkflowDef() throws Exception {

        String analysisjob_name = "ASR MT Workflow";
        String file_prefix = "asr_mt";
        // Use real plugin names
        Olive.RegionScorerRequest.Builder asr_task = Olive.RegionScorerRequest.newBuilder().setPlugin("asr-test").setDomain("english");
//        Workflow.Plugin2PluginRequest.Builder mt_pit_task = Workflow.Plugin2PluginRequest.newBuilder().setPlugin("pim-extractTextRegion").setDomain("test-# So 'SAD' from the workflow should match 'SAD' from our config-asr");
        // TODO transformer uses plugin output to create plugin data OR plugin scores...(convert FS to RS, or convert RS to  text data input )
        Workflow.DataOutputTransformRequest.Builder mt_pit_task = Workflow.DataOutputTransformRequest.newBuilder().setPlugin("pim-extractTextRegion").setDomain("test-ldd-asr")
                .setDataOutput(Olive.InputDataType.TEXT); // or data output?
        Olive.TextTransformationRequest.Builder mt_task = Olive.TextTransformationRequest.newBuilder().setPlugin("txt-text-v1").setDomain("eng-txt");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with 3 tasks (SAD and LID and SID), all results should be returned to the client
        Workflow.JobDefinition.Builder analysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(asr_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR TEXT")
                        .setTraitOutput(Olive.TraitType.DATA_OUTPUT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.DATA_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(mt_pit_task.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR TEXT")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.TEXT_TRANSFORM_REQUEST)
                        .setMessageData(mt_task.build().toByteString())
                        .setConsumerDataLabel("text")
                        .setConsumerResultLabel("MT")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR TEXT").setPluginKeywordName("text").build())
                        .setReturnResult(true));

        // Now create the orders
        Workflow.WorkflowOrderDefinition.Builder analysisOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("ASR Analysis");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ ".workflow").toFile());
        wfd.build().writeTo(fos);


    }

    @Test
    public void test_MOCK_Video_FRV_WorkflowDef() throws Exception {

        String analysisjob_name = "FRV Workflow";
        String file_prefix = "mock_frv";
        // Use real plugin names
        Olive.BoundingBoxScorerRequest.Builder video_task = Olive.BoundingBoxScorerRequest.newBuilder().setPlugin("video-mock").setDomain("face-v1");
        Olive.ClassModificationRequest.Builder video_enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setPlugin("video-mock")
                .setDomain("face-v1")
                .setClassId("none");


        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.VIDEO)
                .setPreprocessingRequired(true);

        Workflow.JobDefinition.Builder analysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRV")
                        .setTraitOutput(Olive.TraitType.BOUNDING_BOX_SCORER)
                        .setMessageType(Olive.MessageType.BOUNDING_BOX_REQUEST)
                        .setMessageData(video_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("video").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Workflow.DataHandlerProperty.Builder enroll_data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);
        Workflow.JobDefinition.Builder enrollJob = Workflow.JobDefinition.newBuilder()
                .setJobName("Video Enrollment")
                .setDataProperties(enroll_data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRV")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(video_enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV Enroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));


        // Now create the orders
        Workflow.WorkflowOrderDefinition.Builder analysisOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("FRV Analysis");

        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollJob)
                .setOrderName("Enrollment Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .addOrder(enrollOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ ".workflow").toFile());
        wfd.build().writeTo(fos);


    }

    @Test
    public void test_Video_FDV_WorkflowDef() throws Exception {

        String analysisjob_name = "FRV Workflow";
        String file_prefix = "fdv";
        // Use real plugin names
        Olive.BoundingBoxScorerRequest.Builder video_task = Olive.BoundingBoxScorerRequest.newBuilder().setPlugin("fdv-pyEmbed-v1.0.0").setDomain("multi-v1");
        Olive.ClassModificationRequest.Builder video_enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setPlugin("fdv-pyEmbed-v1.0.0")
                .setDomain("multi-v1")
                .setClassId("none");


        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.VIDEO)
                .setPreprocessingRequired(true);

        Workflow.JobDefinition.Builder analysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FDV")
                        .setTraitOutput(Olive.TraitType.BOUNDING_BOX_SCORER)
                        .setMessageType(Olive.MessageType.BOUNDING_BOX_REQUEST)
                        .setMessageData(video_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FDV")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("video").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Workflow.DataHandlerProperty.Builder enroll_data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.VIDEO)
                .setPreprocessingRequired(true);
        Workflow.JobDefinition.Builder enrollJob = Workflow.JobDefinition.newBuilder()
                .setJobName("Video Enrollment")
                .setDataProperties(enroll_data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRV")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(video_enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV Enroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("video").setPluginKeywordName("data").build())
                        .setReturnResult(true));


        // Now create the orders
        Workflow.WorkflowOrderDefinition.Builder analysisOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("FRV Analysis");

        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollJob)
                .setOrderName("Enrollment Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .addOrder(enrollOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ ".workflow").toFile());
        wfd.build().writeTo(fos);

    }

    @Test
    public void test_MOCK_Image_FRI_WorkflowDef() throws Exception {

        String analysisjob_name = "FRI Workflow";
        String file_prefix = "mock_fri";
        // Use real plugin names
        Olive.BoundingBoxScorerRequest.Builder img_task = Olive.BoundingBoxScorerRequest.newBuilder().setPlugin("img-mock").setDomain("face-v1");
        Olive.ClassModificationRequest.Builder image_enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setPlugin("img-mock")
                .setDomain("face-v1")
                .setClassId("none");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);

        Workflow.JobDefinition.Builder analysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRI")
                        .setTraitOutput(Olive.TraitType.BOUNDING_BOX_SCORER)
                        .setMessageType(Olive.MessageType.BOUNDING_BOX_REQUEST)
                        .setMessageData(img_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRI")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Workflow.JobDefinition.Builder enrollJob = Workflow.JobDefinition.newBuilder()
                .setJobName("Image Enrollment")
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRI")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(image_enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV Enroll")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Workflow.JobDefinition.Builder enrollImgJob = Workflow.JobDefinition.newBuilder()
                .setJobName("Image Enrollment")
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRI")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(image_enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRI Enroll")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));


        // Now create the orders
        Workflow.WorkflowOrderDefinition.Builder analysisOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("FRI Analysis");

        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollJob)
                .setOrderName("Enrollment Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .addOrder(enrollOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ ".workflow").toFile());
        wfd.build().writeTo(fos);


    }

    @Test
    /**
     */
    public void test_Mock_CONDITIONAL_ASR_MT() throws Exception {

        //String job_name = "Multi Conditional TEST Workflow";
        String file_prefix = "mock_asr_mt_conditional_job";



        // We don't care which SAD it picks
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi-v1"));

        // We need to know what LID is used so we handle the results
        String lid_plugin_name = "lid-embedplda-1.0.0"; // Returns LID and SID
        // String lid_plugin_name = "lid-embedplda-empty" //--> returns no results
        // String lid_plugin_name = "lid-embedplda-exception" //--> throws an exception
        // String lid_plugin_name = "lid-embedplda-v1b" //--> Just returns english
        // String lid_plugin_name = "lid-embedplda-v1b-py3" //--> spanish only
        // String lid_plugin_name = "lid-embedplda-v1b-vp3" //--> russian only
        Olive.GlobalScorerRequest.Builder lid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin(lid_plugin_name).setDomain("multi-v1");


        //Olive.RegionScorerRequest.Builder text_task = Olive.RegionScorerRequest.newBuilder().setPlugin("txt-text-v1").setDomain("eng-cts-v1");

        // Pimiento to choose the ASR plugin/domain from the LID results
        Workflow.Plugin2PluginRequest.Builder pimento_asr_task = Workflow.Plugin2PluginRequest.newBuilder()
                .setPlugin("chooser")
                .setDomain("test-lid-asr");

        Workflow.Plugin2PluginRequest.Builder pimento_text_task = Workflow.Plugin2PluginRequest.newBuilder()
                .setPlugin("chooser")
                .setDomain("test-lid-text");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName("Conditional ASR and MT Workflow")
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("Test conditional processing")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // IMPORTANT - THIS JOB MUST FINISH BEFORE THE DYNAMIC JOB CAN BE START
                .addTransferResultLabels("audio") // Let OLIVE know to transfer audio to downstream jobs
                .addTransferResultLabels("CONDITIONAL_ASR")
                .addTransferResultLabels("CONDITIONAL_MT")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD_FRAMES")
                        .setDescription("SAD Frame Scores")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("CONDITIONAL_ASR")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)
                        .setMessageData(pimento_asr_task.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes lid global scores, no data input
                        .setConsumerResultLabel("CONDITIONAL_ASR")
                        .setDescription("ASR Conditional Task Composer")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("LID").setPluginKeywordName("global_scores").build())
                        .setAllowFailure(false)
                        .setReturnResult(false))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("CONDITIONAL_MT")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)
                        .setMessageData(pimento_text_task.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes lid global scores, no data input
                        .setConsumerResultLabel("CONDITIONAL_MT")
                        .setDescription("MT Conditional Task Composer")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("LID").setPluginKeywordName("global_scores").build())
                        .setAllowFailure(false)
                        .setReturnResult(false));


        // Create a dynamic job, that will be completed based on the results of the previous job
        Workflow.DynamicPluginRequest.Builder dynamic_asr_task = Workflow.DynamicPluginRequest.newBuilder()
                .setConditionalTaskName("CONDITIONAL_ASR");
        Workflow.JobDefinition.Builder dynamic_job = Workflow.JobDefinition.newBuilder()
                .setJobName("Dynamic ASR")
                .addDynamicJobName("Conditional ASR Workflow")  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_props)                         // these should be ignored... since we want to use upstream data but how to specify/allow that?
                .setDescription("Test dynamic selection of the ASR plugin/domain")
                .addTransferResultLabels("ASR")     // The MT task will need ASR results
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_asr_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Dynamic ASR Task")
                        .setAllowFailure(false)
                        .setReturnResult(true));


        // And finally, create a dynamic job to handle the ASR output
        Workflow.DataHandlerProperty.Builder data_text_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.TEXT)
                .setPreprocessingRequired(false);
        Workflow.DynamicPluginRequest.Builder dynamic_mt_task = Workflow.DynamicPluginRequest.newBuilder()
                .setConditionalTaskName("CONDITIONAL_MT");
        // todo how do we transform ASR output to text input that the MT plugin can consume?
        Workflow.JobDefinition.Builder dynamic_text_job = Workflow.JobDefinition.newBuilder()
                .setJobName("Dynamic MT")
                .addDynamicJobName("Conditional MT Workflow")  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_text_props)
                .setDescription("Test dynamic selection of the MT plugin/domain using dynamic ASR input")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // has to run after the ASR task, since we need ASR result
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_mt_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("text")
                        .setConsumerResultLabel("MT")
                        .setDescription("Dynamic ASR Task")
                        .setReturnResult(true));


        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(dynamic_job)
                .addJobDefinition(dynamic_text_job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            if (result.hasError()) {
                Assert.fail("Workflow actualize request message failed: " + result.getError());
            }
            else {
                Assert.fail("Workflow actualize request message failed: " + result.getRep().getError());
            }
        }

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
            if (analysisResult.hasError()) {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getError());
            }
            else {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getRep().getError());
            }
        }




    }

    @Test
    /**
     * Use this "test" to create protobufs for testing
     */
    public void testWorkflow_Quality_Workflow() throws Exception {

        String job_name = "Quality Workflow";
        String file_prefix = "quality";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Olive.RegionScorerRequest.Builder quality_task = Olive.RegionScorerRequest.newBuilder().setPlugin("env-audioquality").setDomain("multi-v2");
        Olive.GlobalScorerRequest.Builder filter_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("qua-filter").setDomain("speaker-v1");
        Olive.GlobalScorerRequest.Builder validate_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("validateGlobal").setDomain("qua-filter-validate");
        // SID analysis and enrollment
        String sidPluginName = "sid-embed";
        String sidPDomainName = "multicond-v1";
        Olive.GlobalScorerRequest.Builder sid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin(sidPluginName).setDomain(sidPDomainName);
        Olive.ClassModificationRequest.Builder sid_enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setPlugin(sidPluginName)
                .setDomain(sidPDomainName)
                .setClassId("none");
        Olive.ClassRemovalRequest.Builder sid_unenroll_task = Olive.ClassRemovalRequest.newBuilder()
                .setClassId("none")
                .setPlugin(sidPluginName)
                .setDomain(sidPDomainName);

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder analysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("Test Workflow Job with multiple types of scorers")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("AudioQuality")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)

                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(quality_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("AudioQuality")
                        .setReturnResult(false)
                        .setDescription("Audio Quality"))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("QUA")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(filter_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("QUA")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("AudioQuality").setPluginKeywordName("QUALITY").build())
                        .setReturnResult(false)
                        .setDescription("Audio Quality Filter"))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("VAL")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(validate_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("VAL")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
                        .setReturnResult(false)
                        .setAllowFailure(false)
                        .setDescription("Audio Quality Validation"))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SID")
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
                        .setReturnResult(true)
                        .setDescription("Speaker Identification"))

//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("SID")
//                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
//                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
//                        .setMessageData(sid_task.build().toByteString())
//                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
//                        .setReturnResult(true)
//                        .setDescription("SID as a Global Scorer"))
                        ;

        Workflow.JobDefinition.Builder enrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("SID Enrollment with Audio Quality Check")
                .setDataProperties(data_props)
                .setDescription("Provides SID enrollment if audio quality is met")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("AudioQuality")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(quality_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("AudioQuality")
                        .setReturnResult(false)
                        .setDescription("Audio Quality"))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("QUA")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(filter_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("QUA")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("AudioQuality").setPluginKeywordName("QUALITY").build())
                        .setReturnResult(false)
                        .setDescription("Audio Quality Filter"))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("VAL")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(validate_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("VAL")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
                        .setReturnResult(false)
                        .setAllowFailure(false)
                        .setDescription("Audio Quality Validation"))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID_Enroll")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(sid_enroll_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SID_Enroll")
                        .setAllowFailure(false)
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
                        .setReturnResult(true)
                        .setDescription("Speaker Identification"))

//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("SID")
//                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
//                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
//                        .setMessageData(sid_task.build().toByteString())
//                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
//                        .setReturnResult(true)
//                        .setDescription("SID as a Global Scorer"))
                ;

        Workflow.JobDefinition.Builder unenrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("Unenrollment for SID")
                .setDataProperties(data_props)
                .setDescription("SID Unenrollment job ")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(sid_unenroll_task.build().toByteString())
                        .setConsumerDataLabel("")//  Does not use data...
                        .setConsumerResultLabel("SID_Unenroll")
                        .setAllowFailure(false)
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder analysisOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("Analysis Order");

        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollmentJob)
                .setOrderName("Enrollment Order");

        Workflow.WorkflowOrderDefinition.Builder unenrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(unenrollmentJob)
                .setOrderName("Unenrollment Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setDescription("Audio Quality Workflow Definition")
                .setVersion("1.0")
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }

    }

    /**
     * Use this "test" to create protobufs for testing
     */
//    public void testWorkflow_Enroll_Quality_Workflow() throws Exception {
//
//        String job_name = "Quality Workflow";
//        String file_prefix = "quality";
//
//        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
//        // either SAD/LID task
//
//        // This should be an invalid SAD request - NO domain with the name 'test'
//        Olive.RegionScorerRequest.Builder quality_task = Olive.RegionScorerRequest.newBuilder().setPlugin("env-audioquality").setDomain("multi-v2");
//        Olive.GlobalScorerRequest.Builder filter_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("qua-filter").setDomain("speaker-v1");
//        Olive.GlobalScorerRequest.Builder validate_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("validateGlobal").setDomain("qua-filter-validate");
//        Olive.GlobalScorerRequest.Builder sid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("sid-embed").setDomain("multicond-v1");
//
//        // Use one mono (if stereo) audio input, having an 8K sample rate
//        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
//                .setMinNumberInputs(1)
//                .setMaxNumberInputs(1)
//                .setType(Olive.InputDataType.AUDIO)
//                .setPreprocessingRequired(true)
//                .setResampleRate(8000)
//                .setMode(Olive.MultiChannelMode.MONO);
//
//        // Create a single job, with two task (SAD and LID), both results should be returned to the client
//        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
//                .setJobName(job_name)
//                .setDataProperties(data_props)
//                .setDescription("Test Workflow Job with multiple types of scorers")
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("AudioQuality")
//                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
//                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
//                        .setMessageData(quality_task.build().toByteString())
//                        .setConsumerDataLabel("audio")
//                        .setConsumerResultLabel("AudioQuality")
//                        .setReturnResult(true)
//                        .setDescription("Audio Quality"))
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("QUA")
//                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
//                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
//                        .setMessageData(filter_task.build().toByteString())
//                        .setConsumerDataLabel("audio")
//                        .setConsumerResultLabel("QUA")
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("AudioQuality").setPluginKeywordName("QUALITY").build())
//                        .setReturnResult(true)
//                        .setDescription("Audio Quality Filter"))
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("VAL")
//                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
//                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
//                        .setMessageData(validate_task.build().toByteString())
//                        .setConsumerDataLabel("audio")
//                        .setConsumerResultLabel("VAL")
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
//                        .setReturnResult(true)
//                        .setDescription("Audio Quality Validation"))
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("SID")
//                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
//                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
//                        .setMessageData(sid_task.build().toByteString())
//                        .setConsumerDataLabel("audio")
//                        .setConsumerResultLabel("SID")
////                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
//                        .setReturnResult(true)
//                        .setDescription("Speaker Identification"))
//
////                .addTasks(Workflow.WorkflowTask.newBuilder()
////                        .setTask("SID")
////                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
////                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
////                        .setMessageData(sid_task.build().toByteString())
////                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
////                        .setReturnResult(true)
////                        .setDescription("SID as a Global Scorer"))
//                ;
//
//        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
//                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
//                .addJobDefinition(job)
//                .setOrderName("Analysis Order");
//
//        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
//                .addOrder(order)
//                .setDescription("Audio Quality Workflow Definition")
//                .setVersion("1.0")
//                .setActualized(false);
//
//        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
//        wfd.build().writeTo(fos);
//
//        // Have the server actualize this workflow def
//        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
//        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
//        if(result.hasError() || result.getRep().hasError() ){
//            Assert.fail("Workflow request message failed: " + result.getError());
//        }
//
//    }


    @Test
    /**
     */
    public void testCreating_CONDITIONAL_ASR_Analysis() throws Exception {

        String job_name = "Conditional ASR Workflow";
        String file_prefix = "mock_conditional_asr";

        // Create a workflow definition that does SAD (frames), LID, CONDITIONAL PIMENTO, then in a new job it does
        // ASR using plugin/domains from the pimento.

        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi-v1"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("lid-embedplda").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi-v1"));

        // todo - create a new plugin (pimento) that will take in GS from LID and then pick ASR plugin and domain(s), returns
        // one or more task or jobs? -- for ASR prolly better as task since we may want to run those sequential due to memory and then our
        // new composer (?) task will
        Workflow.Plugin2PluginRequest.Builder pimento_task = Workflow.Plugin2PluginRequest.newBuilder().setPlugin("chooser").setDomain("test-lid-asr");


        Workflow.OliveNodeWorkflow.Builder oliveConditionalNode = Workflow.OliveNodeWorkflow.newBuilder()
                .setLabel("Convert Frame Scores to Region Scores ")
                .setNodeHandler("workflow_sad_frames_to_regions");
//                .setNodeResultHandler("tbd");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("SAD, LID, WITH Conditional ASR Processing")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // IMPORTANT - THIS JOB MUST FINISH FIRST BEFORE THE DYNAMIC JOB CAN BE START
                .addTransferResultLabels("audio")
                .addTransferResultLabels("CONDITIONAL_ASR")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setDescription("Speech Regions")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setAllowFailure(false)
                        .setReturnResult(true))

                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("CONDITIONAL_ASR") // todo figure out task name and trait info..
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)  // TODO THIS IS THE WRONG TYPE...
                        .setMessageData(pimento_task.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes lid global scores, no data input
                        .setConsumerResultLabel("CONDITIONAL_ASR")
                        .setDescription("ASR Conditional Task Composer")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("LID").setPluginKeywordName("global_scores").build())
                        .setAllowFailure(true)
                        .setReturnResult(false));


        // Create a dynamic job, that will be completed based on the results of the previous job
        Workflow.DynamicPluginRequest.Builder dynamic_asr_task = Workflow.DynamicPluginRequest.newBuilder().setConditionalTaskName("CONDITIONAL_ASR");
        Workflow.JobDefinition.Builder dynamic_job = Workflow.JobDefinition.newBuilder()
                .setJobName("Dynamic ASR")
                .addDynamicJobName(job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_props)
                .setDescription("Test dynamic selection of the ASR plugin/domain")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // may not matter in this case --
//                .setConditionalJobOutput(true) // must set to indicate that this is a conditional job
                // Assume we can use SAD from the frist job
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("SAD")
//                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
//                        .setMessageType(Olive.MessageType.FRAME_SCORER_REQUEST)
//                        .setMessageData(sad_task.build().toByteString())
//                        .setConsumerDataLabel("audio")
//                        .setConsumerResultLabel("SAD_FRAMES")
//                        .setDescription("SAD Frame Scores")
//                        .setReturnResult(true))
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("SAD")
//                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
//                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
//                        .setMessageData(sad_task.build().toByteString())
//                        .setConsumerDataLabel("audio")
//                        .setConsumerResultLabel("SAD_REGIONS")
//                        .setDescription("SAD Region Scores")
//                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_asr_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Dynamic ASR Task")
                        // Maybe it takes options...
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD_FRAMES").setPluginKeywordName("speech_frames").build())
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(dynamic_job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            }
            else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }


        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
            if (analysisResult.hasError()) {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getError());
            }
            else {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getRep().getError());
            }
        }




    }

    @Test
    /**
     */
    public void testCreating_STATIC_CONDITIONAL_ASR_Analysis() throws Exception {

        // We use static (not abstract) plugin names here to make this workflow easier to read/modify
        String job_name = "Conditional ASR Workflow";
        String file_prefix = "conditional_static_asr";

        // Create a workflow definition that does SAD (frames), LID, CONDITIONAL PIMENTO, then in a new job it does
        // ASR using plugin/domains from the pimento.

        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sad-dnn-v7.0.1").setDomain("multi-v1");
        Olive.GlobalScorerRequest.Builder lid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("lid-embedplda").setDomain("multi-v1");
        // Pimento/plugin that consumes global scores from LID and picks the best ASR plugin/domain
        // If uisng the latest LID plugin we use language names, not codes
        Workflow.Plugin2PluginRequest.Builder pimento_task = Workflow.Plugin2PluginRequest.newBuilder().setPlugin("chooser").setDomain("lid-names-asr");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two tasks (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("SAD, LID, WITH Conditional ASR Processing")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // IMPORTANT - THIS JOB MUST FINISH FIRST BEFORE THE DYNAMIC JOB CAN BE START
                .addTransferResultLabels("audio")
                .addTransferResultLabels("CONDITIONAL_ASR")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD_REGIONS")
                        .setDescription("Speech Activity Detection")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setAllowFailure(false)
//                        If you want to pass in SAD regions:
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD_FRAMES").setPluginKeywordName("speech_frames").build())
                        .setReturnResult(true))

                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("DYNAMIC_ASR")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)  // TODO THIS IS THE WRONG TYPE...
                        .setMessageData(pimento_task.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes lid global scores, no data input
                        .setConsumerResultLabel("CONDITIONAL_ASR")
                        .setDescription("ASR Conditional Task Composer")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("LID").setPluginKeywordName("global_scores").build())
                        .setAllowFailure(false)
                        .setReturnResult(false));


        // Create a dynamic job, that will be completed based on the results of the previous job
        Workflow.DynamicPluginRequest.Builder dynamic_asr_task = Workflow.DynamicPluginRequest.newBuilder().setConditionalTaskName("CONDITIONAL_ASR");
        Workflow.JobDefinition.Builder dynamic_job = Workflow.JobDefinition.newBuilder()
                .setJobName("Dynamic ASR")
                .addDynamicJobName(job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_props)
                .setDescription("Test dynamic selection of the ASR plugin/domain")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // should not matter in this case --
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_asr_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Dynamic ASR Task")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(dynamic_job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            }
            else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }


        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
            if (analysisResult.hasError()) {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getError());
            }
            else {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getRep().getError());
            }
        }



    }

    private void checkError(Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult>  result){
        String err = result.getError();
        if (null == err){
            err = result.getRep().getError();
        }
        if (null == err) {
            Assert.fail("Workflow request message failed: " + err);
        }
    }

    private void checkAnalysisError(Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult>  result){
        String err = result.getError();
        if (null == err){
            err = result.getRep().getError();
        }
        if (null == err) {
            Assert.fail("Workflow request message failed: " + err);
        }
    }

    @Test
    /**
     */
    public void testText() throws Exception {

        String job_name = "Text transformation workflow using a mock plugin";
        String file_prefix = "test_text";

        // This only runs the text transformation... at some point we will need a workflow that takes ASR results and transforms them
        Olive.RegionScorerRequest.Builder text_task = Olive.RegionScorerRequest.newBuilder().setPlugin("txt-text-v1").setDomain("eng-cts-v1");

        // We shouldn't need to pre-process MT text input
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.TEXT)
                .setPreprocessingRequired(false);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("Machine Translation Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.TEXT_TRANSFORM_REQUEST)
                        .setMessageData(text_task.build().toByteString())
                        .setConsumerDataLabel("text")
                        .setConsumerResultLabel("MT")
                        .setDescription("Mock test of translating text")
                        .setAllowFailure(false)
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Text Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "test.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }

    }



    @Test
    public void testMockSAD_LID_WorkflowDef() throws Exception {

        String analysisjob_name = "Mock SAD, LID for testing";
        String file_prefix = "mock-sad-lid";
        // Use real plugin names
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sad-dnn-v6-v5").setDomain("tel-v1");
        String lid_plugin_name = "lid-embedplda-1.0.0";
        Olive.GlobalScorerRequest.Builder lid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin(lid_plugin_name).setDomain("multi-v1");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with 3 tasks (SAD and LID and SID), all results should be returned to the client
        Workflow.JobDefinition.Builder analysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setReturnResult(true));

        // Now create the orders
        Workflow.WorkflowOrderDefinition.Builder analysisOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);


    }

    @Test
    public void testASRWorkflowDef() throws Exception {

        String analysisjob_name = "ASR Workflow";
        String file_prefix = "asr";
        // Use real plugin names
        Olive.RegionScorerRequest.Builder asr_task = Olive.RegionScorerRequest.newBuilder().setPlugin("asr-dynapy-v3.0.0").setDomain("english-tdnnChain-tel-v1");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with 3 tasks (SAD and LID and SID), all results should be returned to the client
        Workflow.JobDefinition.Builder analysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(asr_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setReturnResult(true));

        // Now create the orders
        Workflow.WorkflowOrderDefinition.Builder analysisOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("ASR Analysis");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ ".workflow").toFile());
        wfd.build().writeTo(fos);


    }


    @Test
    public void testCreatingSID_LID_EnrollmentWorkflowDef() throws Exception {

        String analysisjob_name = "SAD, LID, SID analysis with LID and SID enrollment";

        String file_prefix = "sad-lid-sid_enroll-lid-sid";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // Use real plugin names
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sad-dnn-v7.0.1").setDomain("multi-v1");
        String lid_plugin_name = "lid-embedplda-v2.0.1";
        Olive.GlobalScorerRequest.Builder lid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin(lid_plugin_name).setDomain("multi-v1");

        String sid_plugin_name = "sid-dplda-v2.0.1";
        Olive.GlobalScorerRequest.Builder sid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin(sid_plugin_name).setDomain("multi-v1");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with 3 tasks (SAD and LID and SID), all results should be returned to the client
        Workflow.JobDefinition.Builder analysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true));

        // Create an enrollment task for LID
        Olive.ClassModificationRequest.Builder lid_enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setClassId("none")
                .setPlugin(lid_plugin_name)
                .setDomain("multi-v1");
        Olive.ClassModificationRequest.Builder sid_enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setClassId("none")
                .setPlugin(sid_plugin_name)
                .setDomain("multi-v1");
        Workflow.JobDefinition.Builder sidEnrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("SID Enrollment")
                .setDataProperties(data_props)
                .setDescription("SID Enrollment")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(sid_enroll_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SID Enrollment")
                        .setAllowFailure(false)
                        .setReturnResult(true));
        Workflow.JobDefinition.Builder lidEnrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("LID Enrollment")
                .setDataProperties(data_props)
                .setDescription("LID Enrollment")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(lid_enroll_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID Enrollment")
                        .setAllowFailure(false)
                        .setReturnResult(true));

        Olive.ClassRemovalRequest.Builder lid_unenroll_task = Olive.ClassRemovalRequest.newBuilder()
                .setClassId("none")
                .setPlugin(lid_plugin_name)
                .setDomain("multi-v1");
        Olive.ClassRemovalRequest.Builder sid_unenroll_task = Olive.ClassRemovalRequest.newBuilder()
                .setClassId("none")
                .setPlugin(sid_plugin_name)
                .setDomain("multi-v1");
        Workflow.JobDefinition.Builder lidUnenrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("LID Unenrollment")
                .setDataProperties(data_props)
                .setDescription("LID UNenrollment job ")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(lid_unenroll_task.build().toByteString())
                        .setConsumerDataLabel("")  //  Does not use data...
                        .setConsumerResultLabel("LID Unenroll")
                        .setAllowFailure(false)
                        .setReturnResult(true));

        Workflow.JobDefinition.Builder sidUnenrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("SID Unenrollment")
                .setDataProperties(data_props)
                .setDescription("SID UNenrollment job ")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(sid_unenroll_task.build().toByteString())
                        .setConsumerDataLabel("")//  Does not use data...
                        .setConsumerResultLabel("SID Unenroll")
                        .setAllowFailure(false)
                        .setReturnResult(true));

        // Now create the orders
        Workflow.WorkflowOrderDefinition.Builder analysisOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("Analysis Order");

        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(sidEnrollmentJob)
                .addJobDefinition(lidEnrollmentJob)
                .setOrderName("Enrollment Order");

        Workflow.WorkflowOrderDefinition.Builder unenrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(sidUnenrollmentJob)
                .addJobDefinition(lidUnenrollmentJob)
                .setOrderName("Unenrollment Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setCreated(Olive.DateTime.newBuilder().setDay(29).setMonth(6).setYear(2021).setHour(8).setMin(0).setSec(0).build())
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
/*        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }*/

/*        // Save the actualized workflow request
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualize_request.workflow").toFile());
        wr.build().writeTo(fos);
        // Save the actualized workflow (response)
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualized_workflow").toFile());
        result.getRep().getWorkflow().writeTo(fos);*/

//        // Now create the analysis message
//        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
//        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
//        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
//            Assert.fail("Workflow Analysis request message failed: " + result.getError());
//        }

//        // Save the actualize workflow request
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_request.workflow").toFile());
//        analysisRequest.writeTo(fos);
//
//        // And finally, save the result:
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_result.workflow").toFile());
//        analysisResult.getRep().writeTo(fos);




    }

    @Test
    public void testCreatingEnrollmentWorkflowDef() throws Exception {

        String analysisjob_name = "SAD, LID, SID analysis using test plugins";

        String file_prefix = "sad-lid-sid-enroll_test";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build());
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("lid-embed").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder sid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sid-dplda").build());
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));
        String lid_plugin_name = "lid-embedplda-v1b-vp3";
        String sid_plugin_name = "sid-dplda-v1";

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with 3 tasks (SAD and LID and SID), all results should be returned to the client
        Workflow.JobDefinition.Builder analysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true));

        // Create an enrollment task and job for LID

        Olive.ClassModificationRequest.Builder lid_enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setClassId("none")
                .setPlugin(lid_plugin_name)
                .setDomain("multi-v1");
        Workflow.JobDefinition.Builder lidEnrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("LID Enrollment")
                .setDataProperties(data_props)
                .setDescription("LID enrollment job using mock plugin")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(lid_enroll_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID_Enroll")
                        .setReturnResult(true));

        // Create an enrollment task and job for SID
        Olive.ClassModificationRequest.Builder sid_enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setClassId("none")
                .setPlugin(sid_plugin_name)
                .setDomain("multi-v1");
        Workflow.JobDefinition.Builder sidEnrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("SID Enrollment")
                .setDataProperties(data_props)
                .setDescription("SID enrollment job using mock plugin")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(sid_enroll_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SID")
                        .setReturnResult(true));

        // Now create the orders
        Workflow.WorkflowOrderDefinition.Builder analysisOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("Analysis Order");

        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(lidEnrollmentJob)
                .addJobDefinition(sidEnrollmentJob)
                .setOrderName("Enrollment Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .addOrder(enrollOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }

        // Save the actualized workflow request
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualize_request.workflow").toFile());
        wr.build().writeTo(fos);
        // Save the actualized workflow (response)
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualized_workflow").toFile());
        result.getRep().getWorkflow().writeTo(fos);

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
            Assert.fail("Workflow Analysis request message failed: " + result.getError());
        }

        // Save the actualize workflow request
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_request.workflow").toFile());
        analysisRequest.writeTo(fos);

        // And finally, save the result:
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_result.workflow").toFile());
        analysisResult.getRep().writeTo(fos);




    }

    @Test
    public void testCreatingMultiJobEnrollmentWorkflowDef() throws Exception {

        String analysisjob_name = "SAD, LID, SID analysis with LID and SID Enrollment jobs";

        String file_prefix = "sad-lid-sid-multi-enrollment-jobs";

        String sad_plugin_name = "sad-dnn";
        String lid_plugin_name = "lid-embedplda-v2.0.1";
        String sid_plugin_name = "sid-dplda-v2.0.1";
        String domainName = "multi-v1";
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin(sad_plugin_name).setDomain(domainName);
        Olive.GlobalScorerRequest.Builder lid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin(lid_plugin_name).setDomain(domainName);
        Olive.GlobalScorerRequest.Builder sid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin(sid_plugin_name).setDomain(domainName);

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with 3 tasks (SAD and LID and SID), all results should be returned to the client
        Workflow.JobDefinition.Builder analysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true));

        // Create an enrollment task and job for LID
        Olive.ClassModificationRequest.Builder lid_enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setClassId("none")
                .setPlugin(lid_plugin_name)
                .setDomain("multi-v1");
        Workflow.JobDefinition.Builder lidEnrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("LID Enrollment")
                .setDataProperties(data_props)
                .setDescription("LID enrollment job using mock plugin")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(lid_enroll_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID_Enroll")
                        .setReturnResult(true));

        // Create an enrollment task and job for SID
        Olive.ClassModificationRequest.Builder sid_enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setClassId("none")
                .setPlugin(sid_plugin_name)
                .setDomain("multi-v1");
        Workflow.JobDefinition.Builder sidEnrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("SID Enrollment")
                .setDataProperties(data_props)
                .setDescription("SID enrollment job using mock plugin")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(sid_enroll_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SID")
                        .setReturnResult(true));

        // Now create the orders
        Workflow.WorkflowOrderDefinition.Builder analysisOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("Analysis Order");

        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(lidEnrollmentJob)
                .addJobDefinition(sidEnrollmentJob)
                .setOrderName("Enrollment Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .addOrder(enrollOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }

        // Save the actualized workflow request
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualize_request.workflow").toFile());
        wr.build().writeTo(fos);
        // Save the actualized workflow (response)
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualized_workflow").toFile());
        result.getRep().getWorkflow().writeTo(fos);

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
            Assert.fail("Workflow Analysis request message failed: " + result.getError());
        }

        // Save the actualize workflow request
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_request.workflow").toFile());
        analysisRequest.writeTo(fos);

        // And finally, save the result:
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_result.workflow").toFile());
        analysisResult.getRep().writeTo(fos);




    }
    @Test
    public void testCreatingSIDEnrollmentWorkflowDef() throws Exception {

        String analysisjob_name = "job_sad_lid_sid_analysis";
        String enroll_job_name = "job_sid_enroll";

        String file_prefix = "sid-enroll"; // used iwth mock plugins

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build());
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("lid-embed").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder sid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sid-embed"))
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multicond").build());

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with 3 tasks (SAD and LID and SID), all results should be returned to the client


        Workflow.JobDefinition.Builder analysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true));

        // Create an enrollment task for SID
        Olive.ClassModificationRequest.Builder sid_enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setClassId("none")
                .setPlugin("sid-embed-v6.0.1")
                .setDomain("multicond-v1");
        Workflow.JobDefinition.Builder enrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName(enroll_job_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(sid_enroll_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SID")
                        .setReturnResult(true));

        // Now create the orders
        Workflow.WorkflowOrderDefinition.Builder analysisOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("Analysis Order");

        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollmentJob)
                .setOrderName("Enrollment Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .addOrder(enrollOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }

        // Save the actualized workflow request
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualize_request.workflow").toFile());
        wr.build().writeTo(fos);
        // Save the actualized workflow (response)
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualized_workflow").toFile());
        result.getRep().getWorkflow().writeTo(fos);

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
            Assert.fail("Workflow Analysis request message failed: " + result.getError());
        }

        // Save the actualize workflow request
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_request.workflow").toFile());
        analysisRequest.writeTo(fos);

        // And finally, save the result:
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_result.workflow").toFile());
        analysisResult.getRep().writeTo(fos);




    }


    @Test
    /**
     * Use this "test" to create protobufs for testing
     */
    public void testCreatingSHLWorkflowDef() throws Exception {


        String job_name = "job-SAD_SHL";
        String file_prefix = "sad-shl";
        // Create a workflow definition that does SAD (frames) and SHL.

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder shl_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("shl").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("telClosetalk"));

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SHL")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(shl_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SHL")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            Assert.fail("Workflow request message failed: " + result.getError());
        }
        // Save the actualized workflow request
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualize_request.workflow").toFile());
        wr.build().writeTo(fos);
        // Save the actualized workflow
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_actualized_workflow").toFile());
        result.getRep().getWorkflow().writeTo(fos);

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
            Assert.fail("Workflow Analysis request message failed: " + result.getError());
        }
        // Save the actualize workflow request
        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_request.workflow").toFile());
        analysisRequest.writeTo(fos);

        // And finally, save the result:
//        fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_analysis_result.workflow").toFile());
//        analysisResult.getRep().writeTo(fos);




    }


    @Test
    public void testWorkflowActualizeRequest() throws Exception {
        // connect
        init();

        // Create an interpreted Workflow Definition with one or more abstract tasks, submit to the
        // the server and make sure a valid WorkflowActualizeResult with a Workflow sub message is returned

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder awpt = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("test"));
        // not yet testing options

        Workflow.DataHandlerProperty.Builder props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName("sad_only")
                .setDataProperties(props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(awpt.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SAD")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder()
                .setWorkflowDefinition(wfd);

        // Make a sync request:
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError()){
            Assert.fail("Workflow request message failed: " + result.getError());
        }

        // Validate result
        Workflow.WorkflowActualizeResult wkResult = result.getRep();
        if(wkResult.hasError()){
//            Assert.fail("Workflow could not be actualized:: " + result.getError());
            log.debug("Workflow could not be actualized: " + result.getError());
        }
        else {
            Assert.fail("Expected a workflow failure due to invalid plugin name");
        }

        // Send another request, but with a valid SAD plugin selection

        // This should be an invalid SAD request
        awpt = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("tel"));
        // not yet testing options

        job = Workflow.JobDefinition.newBuilder()
                .setJobName("sad_only")
                .setDataProperties(props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(awpt.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true));


        order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        wr = Workflow.WorkflowActualizeRequest.newBuilder()
                .setWorkflowDefinition(wfd);

        // Make a sync request:
        result = server.synchRequest(wr.build());
        if(result.hasError()){
            Assert.fail("Workflow request message failed: " + result.getError());
        }

        // Should be one workflow job
        wkResult = result.getRep();
        Assert.assertEquals(wkResult.getWorkflow().getOrderCount(), 1);
        Workflow.WorkflowOrderDefinition orderDefinition =  wkResult.getWorkflow().getOrder(0);
        // And the task should be SAD
        Workflow.JobDefinition sadJob =  orderDefinition.getJobDefinition(0);
        Assert.assertEquals(sadJob.getTasksCount(), 1);
        Workflow.WorkflowTask wkTask = sadJob.getTasks(0);
        Assert.assertEquals(wkTask.getMessageType(), Olive.MessageType.FRAME_SCORER_REQUEST);


    }

}
