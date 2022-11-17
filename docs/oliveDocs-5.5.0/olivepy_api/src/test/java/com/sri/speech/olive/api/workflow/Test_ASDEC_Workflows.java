package com.sri.speech.olive.api.workflow;

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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Test_ASDEC_Workflows {

    private static Logger log = LoggerFactory.getLogger(Test_ASDEC_Workflows.class);

    public static final int scenicPort      = 5588;
    public static final String scenicHost   = "localhost";
    private static final int TIMEOUT        = 10000;  // 10 seconds is enough?

    private Server server;


    public Test_ASDEC_Workflows() throws  Exception{

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
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void testCreatingASDECWorkflowDef_AED() throws Exception {

        // For Hommin
        String job_name = "Acoustic Event Detection Workflow";
        String enrollment_job_name = "Acoustic Event Detection Enrollment Workflow";
        String file_prefix = "aed_enroll_test";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        /*Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sed-rmsEnergy-v1.0.0").build())
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_NEWEST_VERSION))
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("default"));*/
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sed-rmsEnergy-v1.0.0").setDomain("default-v1");


        Olive.RegionScorerRequest.Builder aed_task = Olive.RegionScorerRequest.newBuilder().setPlugin("aed-enrollable-v1.0.0").setDomain("default-v1");
        Olive.GlobalScorerRequest.Builder inout_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("env-indoorOutdoor-v1.0.0").setDomain("default-v1");
        // not a workflow consumer
        Olive.GlobalScorerRequest.Builder power_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("env-powerSupplyHum-v1.0.0").setDomain("default-v1");


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
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SED")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SED")
                        .setDescription("Energy")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("__aed__")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(aed_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("Event Detection")
                        .setDescription("Event Detection")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SED").setPluginKeywordName("SED").build())
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("__env__")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(inout_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("IndoorOutdoor")
                        .setDescription("Indoor/Outdoor")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SED").setPluginKeywordName("SED").build())
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("__env__")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(power_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("Power Hum")
                        .setDescription("Power Hum")
                        .setReturnResult(true));

        // The AED plugin is enrollable
        //Olive.ClassModificationRequest.Builder aed_enroll_task = Olive.ClassModificationRequest.newBuilder().setPlugin("aed-enrollable-v1.0.0").setDomain("default-v1").setClassId("none");
        //Olive.ClassModificationRequest.Builder aed_enroll_task = Olive.ClassModificationRequest.newBuilder().setPlugin("aed-enrollable-v1.0.0").setDomain("default-v1").setClassId("none");

        Workflow.JobDefinition.Builder enrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName(enrollment_job_name)
                .setDataProperties(data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("__aed__")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(aed_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("AED-Enroll")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder analysiOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("Analysis Order");
        Workflow.WorkflowOrderDefinition.Builder enrollmentOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollmentJob)
                .setOrderName("Enrollment Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(analysiOrder)
                .addOrder(enrollmentOrder)
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
    }

    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void testCreatingASDECWorkflowDef_Speech_Analysis() throws Exception {

        // For Hommin
        String job_name = "Speech Analysis Workflow";
        String file_prefix = "speech_analysis";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // Don't use abstract tasks...  too confusing with all the name changes
        /*Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_NEWEST_VERSION))
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi-v1"));*/
        Olive.FrameScorerRequest.Builder sad_task = Olive.FrameScorerRequest.newBuilder().setPlugin("sad-dnn-v7.0.1").setDomain("multi-v1");

        // VTD
       /* Workflow.AbstractWorkflowPluginTask.Builder vtd_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("vtd-dnn").build())
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_NEWEST_VERSION))
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("vtd"));*/
        Olive.RegionScorerRequest.Builder vtd_task = Olive.RegionScorerRequest.newBuilder().setPlugin("vtd-dnn-v7.0.1").setDomain("vtd-v1");

//        Olive.RegionScorerRequest.Builder aed_task = Olive.RegionScorerRequest.newBuilder().setPlugin("aed-enrollable-v1.0.0").setDomain("default-v1");
//        Olive.GlobalScorerRequest.Builder inout_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("mex-indoorOutdoor-v1.0.0").setDomain("default-v1");
//        // not a workflow consumer
//        Olive.GlobalScorerRequest.Builder power_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("mex-powerSupplyHum-v1.0").setDomain("default-v1");

        /*Workflow.AbstractWorkflowPluginTask.Builder vocal_effort = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("voi-voiceCharacterization").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("vocalEffort"));*/
        Olive.RegionScorerRequest.Builder vocal_effort = Olive.RegionScorerRequest.newBuilder().setPlugin("voi-vocalEffort-v1.0.0").setDomain("default-v1");


        /*Workflow.AbstractWorkflowPluginTask.Builder speakings_style_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("voiceCharacterization").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("speakingStyle"));*/
        Olive.GlobalScorerRequest.Builder speakings_style_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("voi-speakingStyle-v1.0.0").setDomain("default-v1");

        /*Workflow.AbstractWorkflowPluginTask.Builder speaker_count_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("mex-speakercount").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("default"));*/
        Olive.GlobalScorerRequest.Builder speaker_count_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("env-speakerCount-v1.0.0").setDomain("default-v1");


        /*Workflow.AbstractWorkflowPluginTask.Builder reverb_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("mex-multi").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));*/
        Olive.GlobalScorerRequest.Builder reverb_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("env-multiClass-v2.0.0").setDomain("multi-v1");

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
                        .setMessageType(Olive.MessageType.FRAME_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setDescription("Speech")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("VTD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(vtd_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("VTD")
                        .setDescription("Live Speech")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("VOI")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(vocal_effort.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("Vocal Effort")
                        .setDescription("Vocal Effort")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD").setPluginKeywordName("SAD").build())
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("__voi__")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(speakings_style_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("Speaking Style")
                        .setDescription("Speaking Style")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD").setPluginKeywordName("SAD").build())
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("__env__")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(speaker_count_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("Speaking Count")
                        .setDescription("Speaking Count")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD").setPluginKeywordName("SAD").build())
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ENV")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(reverb_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("Reverb")
                        .setDescription("Reverb")
                        .addClassId("Reverb-nml")
                        .addClassId("Reverb-rev")
                        .setReturnResult(true))
                ;

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

//        // Have the server actualize this workflow def
//        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
//        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
//        if(result.hasError() || result.getRep().hasError() ){
//            Assert.fail("Workflow request message failed: " + result.getError());
//        }
//
//        // Now create the analysis message
//        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
//        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
//        if(analysisResult.hasError() || analysisResult.getRep().hasError() ){
//            Assert.fail("Workflow Analysis request message failed: " + result.getError());
//        }





    }

    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void testCreatingASDECWorkflowDef_SpeakerCount() throws Exception {

        // For Hommin
        String job_name = "Speaker Count Test Workflow";
        String file_prefix = "sad_speaker_count";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));


//        Olive.GlobalScorerRequest.Builder count_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("mex-speakercount").setDomain("multi-v1");

        Workflow.AbstractWorkflowPluginTask.Builder count_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("mex-speakercount").build());
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
                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MEX")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(count_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("MEX")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD").setPluginKeywordName("SAD").build())
                        .setReturnResult(true))
                /*.addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask(Olive.TaskType.SID)
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true))*/;

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
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void testCreatingASDECWorkflowDef_Background_Noise() throws Exception {

        // For Hommin
        String job_name = "Background Noise Workflow";
        String file_prefix = "background_noise";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        /*Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));*/
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sad-dnn-v7.0.1").setDomain("multi-v1");


        Olive.RegionScorerRequest.Builder count_task = Olive.RegionScorerRequest.newBuilder().setPlugin("nsd-sadInverter-v1.0.0").setDomain("default-v1");
        /*Workflow.AbstractWorkflowPluginTask.Builder count_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("nsd-sadInverter").build());*/

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
                        .setMessageType(Olive.MessageType.FRAME_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setDescription("Speech")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("__nsd__")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(count_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("NSD")
                        .setDescription("Nonspeech")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD").setPluginKeywordName("SAD").build())
                        .setReturnResult(true))
                /*.addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask(Olive.TaskType.SID)
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true))*/;

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
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void testCreatingASDECWorkflowDef_SpeakingStyle() throws Exception {

        // For Hommin
        String job_name = "Vocal Speaking Style Test Workflow";
        String file_prefix = "sad-speaking_style";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("voi-voiceCharacterization").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("speakingStyle"));

//        Workflow.AbstractWorkflowPluginTask.Builder sid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
//                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sid-dplda").build())
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
                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("VOI")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("VOI")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD").setPluginKeywordName("SAD").build())
                        .setReturnResult(true))
                /*.addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask(Olive.TaskType.SID)
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true))*/;

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
    public void testCreatingASDECWorkflowDef_Reverb() throws Exception {

        // For Homin
        String job_name = "SAD and Audio Metadata Extractor";
        String file_prefix = "mex-multi";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build());
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("mex-multi").build());
//                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("vocaleffort").build());
//                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_NEWEST_VERSION)
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
                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MEX")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("MEX Multi")
                        .addClassId("Reverb-nml")
                        .addClassId("Reverb-rev")
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD").setPluginKeywordName("SAD").build())
                        .setReturnResult(true))
                /*.addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask(Olive.TaskType.SID)
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true))*/;

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
    public void testCreatingASDECWorkflowDef_VocalEffor() throws Exception {

        // For Homin
        String job_name = "SAD and Vocal Effort Workflow";
        String file_prefix = "sad-vocal_style";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("voi-voiceCharacterization").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("vocalEffort").build());
//                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_NEWEST_VERSION)
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
                        .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("VOI")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("VOI")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD").setPluginKeywordName("SAD").build())
                        .setReturnResult(true))
                /*.addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask(Olive.TaskType.SID)
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true))*/;

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
    public void testCreatingASDECWorkflowDef_SED_AED() throws Exception {
        // Plugins for Mahi..

        // but I'm not sure about the plugin name and domain
        String job_name = "SED AED Enrollable Test Workflow";
        String file_prefix = "sed_aed";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sed").build());
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("tel-v1"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("aed-enrollable").build());
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
                        .setTask("SED")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SED")
                        .setReturnResult(true))
                // todo not task defined for this yet!
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("AED")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("AED")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SED").setPluginKeywordName("SED").build())
                        .setReturnResult(true))
                /*.addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask(Olive.TaskType.SID)
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true))*/;

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
    public void testCreatingASDECWorkflowDef_SED_MEX_INDOOR() throws Exception {
        // Plugins for Mahi..

        // but I'm not sure about the plugin name and domain
        String job_name = "SED Indoor-Outdoor (BSD) Test Workflow";
        String file_prefix = "sed_indoorOutdoor";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sed").build());
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("tel-v1"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("mex-indoorOutdoor").build());
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
                        .setTask("SED")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SED")
                        .setReturnResult(true))
                // todo not task defined for this yet!
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MEX")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("Indoor-Outdoor")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SED").setPluginKeywordName("SED").build())
                        .setReturnResult(true))
                /*.addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask(Olive.TaskType.SID)
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true))*/;

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
    public void testCreatingASDECWorkflowDef_NSD() throws Exception {
        // Plugins for Mahi..

        // but I'm not sure about the plugin name and domain
        String job_name = "Non Speech Workflow";
        String file_prefix = "non_speech";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_STARTS).setValue("multi"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("nsd-sadInverter").build())
                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("default"));

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
                        .setReturnResult(true)
                        .setAllowFailure(false))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("NSD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD_NSD")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD").setPluginKeywordName("SAD").build())
                        .setReturnResult(true))
                /*.addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask(Olive.TaskType.SID)
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true))*/;

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
    public void testCreatingASDECWorkflowDef_SED_INDOOR_OUTDOOR() throws Exception {
        // Plugins for Mahi..

        // but I'm not sure about the plugin name and domain
        String job_name = "sed_indoor_outdoor";
        String file_prefix = "sed_indoor_outdoor";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // This should be an invalid SAD request - NO domain with the name 'test'
        Workflow.AbstractWorkflowPluginTask.Builder sad_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("sed").build());
//                .addDomainCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("default"));

        Workflow.AbstractWorkflowPluginTask.Builder lid_task = Workflow.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Workflow.SelectionCriteria.newBuilder().setType(Workflow.SelectionType.SELECTION_CONTAINS).setValue("ind-outd").build());
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
                        .setTask("SED")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SED")
                        .setReturnResult(true))
                // TODO NO TYPE DEFINED FOR THIS YET
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SDD")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SDD")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SED").setPluginKeywordName("SED").build())
                        .setReturnResult(true))
                /*.addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask(Olive.TaskType.SID)
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true))*/;

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

}
