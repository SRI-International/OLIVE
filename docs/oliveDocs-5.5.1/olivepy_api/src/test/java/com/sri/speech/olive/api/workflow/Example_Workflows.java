package com.sri.speech.olive.api.workflow;

import com.google.protobuf.Message;
import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.StreamingServer;
import com.sri.speech.olive.api.Workflow;
import com.sri.speech.olive.api.Server;
import com.sri.speech.olive.api.stream.Stream;
import com.sri.speech.olive.api.utils.AudioUtil;
import com.sri.speech.olive.api.utils.ClientUtils;
import com.sri.speech.olive.api.workflow.wrapper.DataResult;
import com.sri.speech.olive.api.workflow.wrapper.JobResult;
import com.sri.speech.olive.api.workflow.wrapper.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Example_Workflows {

    private static Logger log = LoggerFactory.getLogger(Example_Workflows.class);

    public static final int scenicPort = 5588;
    public static final String scenicHost = "localhost";
    private static final int TIMEOUT = 10000;  // 10 seconds is enough?

    private Server server;


    public Example_Workflows() throws Exception {

        // Create the server, skiping tests if we can't connect
        server = new Server();
        server.connect("Scenic_Test", scenicHost, scenicPort, scenicPort + 1, 100);

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

    private void init() {
        if (!server.getConnected().get()) {
            log.error("Unable to connect to the OLIVE server: {}", scenicHost);
            server.disconnect();
            throw new SkipException("Unable to connect to server");
        }

    }


    private Workflow.WorkflowAnalysisRequest createAnalysisMessage(Workflow.WorkflowDefinition workflowDef, String audioFilename) throws IOException, UnsupportedAudioFileException {
        // We assume our workflow only has one order, which has one job
//        Workflow.WorkflowOrderDefinition orderDefinition = workflowDef.getOrder(0);

        // Create the data message for our filename
        Olive.Audio.Builder audio = ClientUtils.createAudioFromFile(audioFilename, 0, ClientUtils.AudioTransferType.SEND_AS_PATH, null);
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


    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void testSAD() throws Exception {

        // For Hommin
        String job_name = "SAD Only Workflow";
        String file_prefix = "sad";

        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sad-dnn-v7.0.1").setDomain("multi-v1");
//        MOCK SAD
//        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sad-dnn-v6-v4").setDomain("multi-v1");
        /*Olive.AbstractWorkflowPluginTask.Builder sad_task = Olive.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Olive.SelectionCriteria.newBuilder().setType(Olive.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addPluginCriteria(Olive.SelectionCriteria.newBuilder().setType(Olive.SelectionType.SELECTION_NEWEST_VERSION));
*/

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
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
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

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "example.workflow").toFile());
        wfd.build().writeTo(fos);

    }

    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void testSDD() throws Exception {

        // For Hommin
        String job_name = "SDD Only Workflow";
        String file_prefix = "sdd_only";

        Olive.RegionScorerRequest.Builder sdd_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sdd-diarizeembed").setDomain("telClosetalk-v1");

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
                        .setTask("SDD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sdd_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SDD")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_example.workflow").toFile());
        wfd.build().writeTo(fos);

    }

    @Test
    public void testLDD() throws Exception {

        // For Hommin
        String job_name = "LDD Only Workflow";
        String file_prefix = "ldd";

        Olive.RegionScorerRequest.Builder ldd_task = Olive.RegionScorerRequest.newBuilder().setPlugin("ldd-embedplda-v1.0.1").setDomain("multi-v1");


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
                        .setTask("LDD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(ldd_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LDD")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "_example.workflow").toFile());
        wfd.build().writeTo(fos);

    }

    @Test
    public void testLID() throws Exception {

        // For Hommin
        String job_name = "LID Only Workflow";
        String file_prefix = "lid";

        Olive.GlobalScorerRequest.Builder sad_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("lid-embedplda-v2.0.1").setDomain("multi-v1");


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
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "example.workflow").toFile());
        wfd.build().writeTo(fos);

    }


    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void testSAD_LID() throws Exception {

        String job_name = "Basic SAD and LID workflow";
        String file_prefix = "sad_lid";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // Not doing this, but could define abstract tasks
        /*Olive.AbstractWorkflowPluginTask.Builder sad_task = Olive.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Olive.SelectionCriteria.newBuilder().setType(Olive.SelectionType.SELECTION_CONTAINS).setValue("sad-dnn").build())
                .addPluginCriteria(Olive.SelectionCriteria.newBuilder().setType(Olive.SelectionType.SELECTION_NEWEST_VERSION));

        Olive.AbstractWorkflowPluginTask.Builder lid_task = Olive.AbstractWorkflowPluginTask.newBuilder()
                .addPluginCriteria(Olive.SelectionCriteria.newBuilder().setType(Olive.SelectionType.SELECTION_CONTAINS).setValue("lid-embedplda").build());*/

        // To add plugin specific options:
        //.addOption(Olive.OptionValue.newBuilder().setName("foo").setValue("bar").build());
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sad-dnn-v7.0.1").setDomain("multi-v1");
        Olive.GlobalScorerRequest.Builder lid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("lid-embedplda").setDomain("multi-v1");

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
                .setDescription("SAD and LID Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("Speech Regions")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
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

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "example.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            Assert.fail("Workflow request message failed: " + result.getError());
        }

    }

    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void test_STREAM_SAD_SPEECH_ASR_16K() throws Exception {

        // FIXME - THIS IS ACTUALLY AN 8K DOMAIN
        String job_name = "Streaming SAD plus ASR on speech workflow";
        String job_text_name = "Streaming ASR on speech detection";
        String file_prefix = "stream_sad_speech_asr";

        // To add plugin specific options:
        //.addOption(Olive.OptionValue.newBuilder().setName("foo").setValue("bar").build());

        Stream.RegionScorerStreamingRequest.Builder sad_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("sad-dnn-stream").setDomain("multi-v1");
        Stream.RegionScorerStreamingRequest.Builder asr_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("asr-dynapy-streaming-v3.0.0").setDomain("english-tdnnChain-tel-v1");
        // The streaming pimiento:
        Stream.SyncStreamingRequest.Builder stream_mgr_task = Stream.SyncStreamingRequest.newBuilder().setPlugin("pim-speech-streaming").setDomain("default");

        // A pimiento that converts ASR partial result output (metadata) into text output
        Workflow.ScoreOutputTransformRequest.Builder pr_pie = Workflow.ScoreOutputTransformRequest.newBuilder()
                .setPlugin("pim-extractStreamingTextRegion").setDomain("asr-partial-result-sentence")
                .setTraitInput(Olive.TraitType.REGION_SCORER)
                .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER);

        // Use one mono (if stereo) audio input, having an 8K sample rate, should this be how we specify a stream?
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // ASR data needs to be 16K
        Workflow.DataHandlerProperty.Builder data_asr_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Text output properties (partial results are returned as text)
        Workflow.DataHandlerProperty.Builder data_text_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.TEXT)
                .setPreprocessingRequired(false)
                .setResampleRate(8000);

        // Create a single job, with two tasks (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("SAD Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MAIN")
                        .setTraitOutput(Olive.TraitType.SYNC_STREAMING_ANALYZER) // and what kind of trait output is this?
                        .setMessageType(Olive.MessageType.SYNC_STREAMING_REQUEST)
                        .setMessageData(stream_mgr_task.build().toByteString())
                        .setConsumerDataLabel("")
                        .setConsumerResultLabel("Streaming Manager")
                        .setDescription("Streaming Manager Task")
                        .setReturnResult(false))
                // STREAMING SAD
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.REGION_SCORER_STREAMING_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setDescription("Streaming SAD Regions")
                        .setAllowFailure(false)
                        .setReturnResult(true));

        // ASR has its own job, since it processes data at 16k
        Workflow.JobDefinition.Builder asr_job = Workflow.JobDefinition.newBuilder()
                .setJobName("Streaming ASR16K")
                .setDataProperties(data_asr_props)
                .setDescription("ASR 16K Audio Processing")
                // STREAMING ASR
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.REGION_SCORER_STREAMING_REQUEST)
                        .setMessageData(asr_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Streaming ASR")
                        .setReturnResult(true));


        // SINCE IT CREATES --> TEXT, AUDO, ETC, and we want
        Workflow.JobDefinition.Builder text_job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_text_name)
                .setDataProperties(data_text_props)
                .setDescription("ASR Partial Results to Text")
                // Takes ASR metadata and extracts the partial result returned as a sentence to the client
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR_PR")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.SCORE_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(pr_pie.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR_PR")
                        // somewhat awkward since we use streaming metadata not the typical 'asr' region score results
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setDescription("ASR Partial Result Text")
                        .setReturnResult(true));


        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(asr_job)
                .addJobDefinition(text_job)
                .setOrderName("Streaming Analysis");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            }
            else{
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }

    }

    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void test_STREAM_SAD_ASR() throws Exception {

        String job_name = "Streaming SAD and ASR workflow";
        String job_text_name = "Streaming ASR EP";
        String file_prefix = "stream_sad_asr";

        // To add plugin specific options:
        //.addOption(Olive.OptionValue.newBuilder().setName("foo").setValue("bar").build());

        Stream.RegionScorerStreamingRequest.Builder sad_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("sad-dnn-stream").setDomain("multi-v1");
        Stream.RegionScorerStreamingRequest.Builder asr_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("asr-dynapy-streaming-v3.0.0").setDomain("english-tdnnChain-tel-v1");
        Stream.RegionScorerStreamingRequest.Builder ep_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("EP-CNN-v1.0.0").setDomain("v1");
        // The streaming pimiento:
        Stream.SyncStreamingRequest.Builder stream_mgr_task = Stream.SyncStreamingRequest.newBuilder().setPlugin("streaming-pimiento").setDomain("default");

        // A pimiento that converts ASR partial result output (metadata) into text output
        Workflow.ScoreOutputTransformRequest.Builder ep_pie = Workflow.ScoreOutputTransformRequest.newBuilder()
                .setPlugin("pim-extractStreamingTextRegion").setDomain("asr-partial-result-ep")
                .setTraitInput(Olive.TraitType.REGION_SCORER)
                .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER);

        // Extracts ASR partial results (metadata) for the EP plugin
        Workflow.ScoreOutputTransformRequest.Builder pr_pie = Workflow.ScoreOutputTransformRequest.newBuilder()
                .setPlugin("pim-extractStreamingTextRegion").setDomain("asr-partial-result-sentence")
                .setTraitInput(Olive.TraitType.REGION_SCORER)
                .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER);

        // Use one mono (if stereo) audio input, having an 8K sample rate, should this be how we specify a stream?
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        Workflow.DataHandlerProperty.Builder data_text_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.TEXT)
                .setPreprocessingRequired(false)
                .setResampleRate(8000);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("SAD and LID Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MAIN")
                        .setTraitOutput(Olive.TraitType.SYNC_STREAMING_ANALYZER) // and what kind of trait output is this?
                        .setMessageType(Olive.MessageType.SYNC_STREAMING_REQUEST)
                        .setMessageData(stream_mgr_task.build().toByteString())
                        .setConsumerDataLabel("")
                        .setConsumerResultLabel("Streaming Manager")
                        .setDescription("Streaming Manager Task")
                        .setReturnResult(false))
                // STREAMING SAD
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.REGION_SCORER_STREAMING_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setDescription("Streaming SAD Regions")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                // STREAMING ASR
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.REGION_SCORER_STREAMING_REQUEST)
                        .setMessageData(asr_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Streaming ASR")
                        .setReturnResult(true));
        // fixme: ASR streaming should produce ASR region scores, EP Partial Results, 'Simple' Partial Results - how
        // do we identify which of those values it converts to REGION_SCORER_STREAMING???
        // fixme: ASR should also return a 'partial result', which is a string (I think)
        // Add EP (text) --- but this is werid cause the data type is text... so maybe it should be in a different job?

        // I may have this wrong... we need to turn ASR output into text, but DATA TRANSFORMER SUPPORTS TEXT, AUDIO, VIDEO CONVERSIONS WHICH SEEMS MORE COMPLICATED THAN WHAT WE NEED.
        // SINCE IT CREATES --> TEXT, AUDO, ETC, and we want
        Workflow.JobDefinition.Builder text_job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_text_name)
                .setDataProperties(data_text_props)
                .setDescription("ASR Partial Results to Text")
                // Takes ASR metadata and extracts the partial result returned as a sentence to the client
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("TXT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.SCORE_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(pr_pie.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR_PR")
                        // somewhat awkward since we use streaming metadata not the typical 'asr' region score results
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setDescription("ASR Partial Result Text")
                        .setReturnResult(true))
                // Takes ASR metadata and extractes the partial result for the EP plugin
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("TXT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.SCORE_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(ep_pie.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR_EP")
                        // somewhat awkward since we use streaming metadata not the typical 'asr' region score results
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setDescription("ASR Partial Result EP Text")
                        .setReturnResult(false))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("EP")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.TEXT_TRANSFORM_REQUEST)
                        .setMessageData(ep_task.build().toByteString())
                        .setConsumerDataLabel("text")
                        .setConsumerResultLabel("EP")
                        // somewhat awkward since we use streaming metadata not the typical 'asr' region score results
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR_EP").setPluginKeywordName("text").build())
                        .setDescription("End pointer plugin")
                        .setReturnResult(true));


        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(text_job)
                .setOrderName("Streaming Analysis");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_example.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            }
            else{
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }

    }

    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void test_STREAM_SAD_ASR_NO_EP() throws Exception {

        String job_name = "Streaming SAD and ASR workflow";
        String job_text_name = "Streaming SAD ASR";
        String file_prefix = "stream_sad_asr_no_ep";

        // To add plugin specific options:
        //.addOption(Olive.OptionValue.newBuilder().setName("foo").setValue("bar").build());

        Stream.RegionScorerStreamingRequest.Builder sad_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("sad-dnn-stream").setDomain("multi-v1");
        Stream.RegionScorerStreamingRequest.Builder asr_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("asr-dynapy-streaming-v3.0.0").setDomain("english-tdnnChain-tel-v1");
        // The streaming pimiento:
        Stream.SyncStreamingRequest.Builder stream_mgr_task = Stream.SyncStreamingRequest.newBuilder().setPlugin("streaming-pimiento").setDomain("default");

        // A pimiento that converts ASR partial result output (metadata) into text output
        Workflow.ScoreOutputTransformRequest.Builder ep_pie = Workflow.ScoreOutputTransformRequest.newBuilder()
                .setPlugin("pim-extractStreamingTextRegion").setDomain("asr-partial-result-ep")
                .setTraitInput(Olive.TraitType.REGION_SCORER)
                .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER);

        // Extracts ASR partial results (metadata) for the EP plugin
        Workflow.ScoreOutputTransformRequest.Builder pr_pie = Workflow.ScoreOutputTransformRequest.newBuilder()
                .setPlugin("pim-extractStreamingTextRegion").setDomain("asr-partial-result-sentence")
                .setTraitInput(Olive.TraitType.REGION_SCORER)
                .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER);

        // Use one mono (if stereo) audio input, having an 8K sample rate, should this be how we specify a stream?
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        Workflow.DataHandlerProperty.Builder data_text_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.TEXT)
                .setPreprocessingRequired(false)
                .setResampleRate(8000);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("SAD and LID Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MAIN")
                        .setTraitOutput(Olive.TraitType.SYNC_STREAMING_ANALYZER) // and what kind of trait output is this?
                        .setMessageType(Olive.MessageType.SYNC_STREAMING_REQUEST)
                        .setMessageData(stream_mgr_task.build().toByteString())
                        .setConsumerDataLabel("")
                        .setConsumerResultLabel("Streaming Manager")
                        .setDescription("Streaming Manager Task")
                        .setReturnResult(false))
                // STREAMING SAD
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.REGION_SCORER_STREAMING_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setDescription("Streaming SAD Regions")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                // STREAMING ASR
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.REGION_SCORER_STREAMING_REQUEST)
                        .setMessageData(asr_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Streaming ASR")
                        .setReturnResult(true));

        Workflow.JobDefinition.Builder text_job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_text_name)
                .setDataProperties(data_text_props)
                .setDescription("ASR Partial Results to Text")
                // Takes ASR metadata and extracts the partial result returned as a sentence to the client
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("TXT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.SCORE_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(pr_pie.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR_PR")
                        // somewhat awkward since we use streaming metadata not the typical 'asr' region score results
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setDescription("ASR Partial Result Text")
                        .setReturnResult(true));


        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(text_job)
                .setOrderName("Streaming Analysis");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            }
            else{
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }

    }

    @Test
    /**
     * A streaming SAD, ASR, EP workflow using 16k audio for ASR
     */
    public void test_STREAM_SAD_ASR16K() throws Exception {

        //String job_name = "Streaming SAD";
        String job_text_name = "Streaming SAD, ASR, EP";
        String file_prefix = "stream_sad_asr16k";


        Stream.RegionScorerStreamingRequest.Builder sad_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("sad-dnn-stream").setDomain("multi-v1");
        Stream.RegionScorerStreamingRequest.Builder asr_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("asr-dynapy-streaming-v3.0.0").setDomain("english-tdnnChain-tel-v1");
        Stream.RegionScorerStreamingRequest.Builder ep_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("EP-CNN-v1.0.0").setDomain("v1");
        // The streaming pimiento:
        Stream.SyncStreamingRequest.Builder stream_mgr_task = Stream.SyncStreamingRequest.newBuilder().setPlugin("streaming-pimiento").setDomain("default");

        // A pimiento that converts ASR partial result output (metadata) into text output
        Workflow.ScoreOutputTransformRequest.Builder ep_pie = Workflow.ScoreOutputTransformRequest.newBuilder()
                .setPlugin("pim-extractStreamingTextRegion").setDomain("asr-partial-result-ep")
                .setTraitInput(Olive.TraitType.REGION_SCORER)
                .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER);

        // Extracts ASR partial results (metadata) for the EP plugin
        Workflow.ScoreOutputTransformRequest.Builder pr_pie = Workflow.ScoreOutputTransformRequest.newBuilder()
                .setPlugin("pim-extractStreamingTextRegion").setDomain("asr-partial-result-sentence")
                .setTraitInput(Olive.TraitType.REGION_SCORER)
                .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER);

        // Use one mono (if stereo) audio input, having an 8K sample rate, should this be how we specify a stream?
        Workflow.DataHandlerProperty.Builder data_sad_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        Workflow.DataHandlerProperty.Builder data_asr_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(16000)
                .setMode(Olive.MultiChannelMode.MONO);

        Workflow.DataHandlerProperty.Builder data_text_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.TEXT)
                .setPreprocessingRequired(false)
                .setResampleRate(8000);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder sad_job = Workflow.JobDefinition.newBuilder()
                .setJobName("Streaming SAD")
                .setDataProperties(data_sad_props)
                .setDescription("SAD Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MAIN")
                        .setTraitOutput(Olive.TraitType.SYNC_STREAMING_ANALYZER) // and what kind of trait output is this?
                        .setMessageType(Olive.MessageType.SYNC_STREAMING_REQUEST)
                        .setMessageData(stream_mgr_task.build().toByteString())
                        .setConsumerDataLabel("")
                        .setConsumerResultLabel("Streaming Manager")
                        .setDescription("Streaming Manager Task")
                        .setReturnResult(false))
                // STREAMING SAD
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.REGION_SCORER_STREAMING_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setDescription("Streaming SAD Regions")
                        .setAllowFailure(false)
                        .setReturnResult(true));

        // Create a single job for ASR, using 16K audio
        Workflow.JobDefinition.Builder asr_job = Workflow.JobDefinition.newBuilder()
                .setJobName("Streaming ASR16K")
                .setDataProperties(data_asr_props)
                .setDescription("ASR 16K Audio Processing")
                // STREAMING ASR
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.REGION_SCORER_STREAMING_REQUEST)
                        .setMessageData(asr_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Streaming ASR")
                        .setReturnResult(true));



        // I may have this wrong... we need to turn ASR output into text, but DATA TRANSFORMER SUPPORTS TEXT, AUDIO, VIDEO CONVERSIONS WHICH SEEMS MORE COMPLICATED THAN WHAT WE NEED.
        // SINCE IT CREATES --> TEXT, AUDO, ETC, and we want
        Workflow.JobDefinition.Builder text_job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_text_name)
                .setDataProperties(data_text_props)
                .setDescription("ASR Partial Results to Text")
                // Takes ASR metadata and extracts the partial result returned as a sentence to the client
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("TXT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.SCORE_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(pr_pie.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR_PR")
                        // somewhat awkward since we use streaming metadata not the typical 'asr' region score results
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setDescription("ASR Partial Result Text")
                        .setReturnResult(true))
                // Takes ASR metadata and extractes the partial result for the EP plugin
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("TXT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.SCORE_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(ep_pie.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR_EP")
                        // somewhat awkward since we use streaming metadata not the typical 'asr' region score results
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setDescription("ASR Partial Result EP Text")
                        .setReturnResult(false))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("EP")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.TEXT_TRANSFORM_REQUEST)
                        .setMessageData(ep_task.build().toByteString())
                        .setConsumerDataLabel("text")
                        .setConsumerResultLabel("EP")
                        // somewhat awkward since we use streaming metadata not the typical 'asr' region score results
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR_EP").setPluginKeywordName("text").build())
                        .setDescription("End pointer pluging")
                        .setReturnResult(true));


        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(sad_job)
                .addJobDefinition(asr_job)
                .addJobDefinition(text_job)
                .setOrderName("Streaming Analysis");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_example.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            }
            else{
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }

    }

    @Test
    /**
     * Create a workflow that will extract
     */
    public void test_STREAM_SAD_ASR_EP_Global() throws Exception {

        String job_name = "Streaming SAD and EP Global Score workflow";
        String job_text_name = "Streaming SAD, EP";
        String file_prefix = "stream_sad_asr_ep_global";

        // To add plugin specific options:
        //.addOption(Olive.OptionValue.newBuilder().setName("foo").setValue("bar").build());

        Stream.RegionScorerStreamingRequest.Builder sad_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("sad-dnn-stream").setDomain("multi-v1");
        Stream.RegionScorerStreamingRequest.Builder asr_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("asr-dynapy-streaming-v3.0.0").setDomain("english-tdnnChain-tel-v1");
        Stream.RegionScorerStreamingRequest.Builder ep_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("EP-CNN-v1.0.0").setDomain("v1");
        // The streaming pimiento:
        Stream.SyncStreamingRequest.Builder stream_mgr_task = Stream.SyncStreamingRequest.newBuilder().setPlugin("streaming-pimiento").setDomain("default");

        // A pimiento that converts an EP json string output into a global score
        Workflow.ScoreOutputTransformRequest.Builder ep_global_pim_task = Workflow.ScoreOutputTransformRequest.newBuilder()
                .setPlugin("pim-extractEndpointGlobalScore").setDomain("default_ep")
                .setTraitInput(Olive.TraitType.TEXT_TRANSFORMER)
                .setTraitOutput(Olive.TraitType.GLOBAL_SCORER);

        // A pimiento that converts ASR partial result output (metadata) into text output
        Workflow.ScoreOutputTransformRequest.Builder ep_pie = Workflow.ScoreOutputTransformRequest.newBuilder()
                .setPlugin("pim-extractStreamingTextRegion").setDomain("asr-partial-result-ep")
                .setTraitInput(Olive.TraitType.REGION_SCORER)
                .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER);

        // Extracts ASR partial results (metadata) for the EP plugin
        Workflow.ScoreOutputTransformRequest.Builder pr_pie = Workflow.ScoreOutputTransformRequest.newBuilder()
                .setPlugin("pim-extractStreamingTextRegion").setDomain("asr-partial-result-sentence")
                .setTraitInput(Olive.TraitType.REGION_SCORER)
                .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER);

        // Use one mono (if stereo) audio input, having an 8K sample rate, should this be how we specify a stream?
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        Workflow.DataHandlerProperty.Builder data_text_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.TEXT)
                .setPreprocessingRequired(false)
                .setResampleRate(8000);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("SAD and LID Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MAIN")
                        .setTraitOutput(Olive.TraitType.SYNC_STREAMING_ANALYZER) // and what kind of trait output is this?
                        .setMessageType(Olive.MessageType.SYNC_STREAMING_REQUEST)
                        .setMessageData(stream_mgr_task.build().toByteString())
                        .setConsumerDataLabel("")
                        .setConsumerResultLabel("Streaming Manager")
                        .setDescription("Streaming Manager Task")
                        .setReturnResult(false))
                // STREAMING SAD
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.REGION_SCORER_STREAMING_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setDescription("Streaming SAD Regions")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                // STREAMING ASR
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.REGION_SCORER_STREAMING_REQUEST)
                        .setMessageData(asr_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Streaming ASR")
                        .setReturnResult(true));

        Workflow.JobDefinition.Builder text_job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_text_name)
                .setDataProperties(data_text_props)
                .setDescription("ASR Partial Results to Text")
                // Takes ASR metadata and extracts the partial result returned as a sentence to the client
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("TXT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.SCORE_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(pr_pie.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR_PR")
                        // somewhat awkward since we use streaming metadata not the typical 'asr' region score results
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setDescription("ASR Partial Result Text")
                        .setReturnResult(false))
                // Takes ASR metadata and extractes the partial result for the EP plugin
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("TXT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.SCORE_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(ep_pie.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR_EP")
                        // somewhat awkward since we use streaming metadata not the typical 'asr' region score results
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setDescription("ASR Partial Result EP Text")
                        .setReturnResult(false))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("EP")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.TEXT_TRANSFORM_REQUEST)
                        .setMessageData(ep_task.build().toByteString())
                        .setConsumerDataLabel("text")
                        .setConsumerResultLabel("EP")
                        // somewhat awkward since we use streaming metadata not the typical 'asr' region score results
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR_EP").setPluginKeywordName("text").build())
                        .setDescription("End pointer plugin")
                        .setReturnResult(false))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("EP_GLOBAL")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.SCORE_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(ep_global_pim_task.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("EP_GLOBAL")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("EP").setPluginKeywordName("scores").build())
                        .setDescription("End pointer text to Global Scorer adapter")
                        .setReturnResult(true));



        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(text_job)
                .setOrderName("Streaming Analysis");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_example.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            }
            else{
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }

    }

    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void test_STREAM_SAD_LID() throws Exception {

        String job_name = "Streaming SAD LID SID";
        String file_prefix = "stream_sad_lid_sid";

        // To add plugin specific options:
        //.addOption(Olive.OptionValue.newBuilder().setName("foo").setValue("bar").build());

        Stream.RegionScorerStreamingRequest.Builder sad_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("sad-dnn-stream").setDomain("multi-v1");
        Stream.RegionScorerStreamingRequest.Builder lid_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("lid-embedplda-stream").setDomain("multi-v1");
        Stream.RegionScorerStreamingRequest.Builder sid_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("sid-embed-stream").setDomain("multicond-v1-comm");

        // The streaming pimiento:
        Stream.SyncStreamingRequest.Builder stream_mgr_task = Stream.SyncStreamingRequest.newBuilder().setPlugin("pim-general-streaming").setDomain("default");

//        // A pimiento that converts ASR partial result output (metadata) into text output
//        Workflow.ScoreOutputTransformRequest.Builder ep_pie = Workflow.ScoreOutputTransformRequest.newBuilder()
//                .setPlugin("pim-extractStreamingTextRegion").setDomain("asr-partial-result-ep")
//                .setTraitInput(Olive.TraitType.REGION_SCORER)
//                .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER);

        // Extracts ASR partial results (metadata) for the EP plugin
//        Workflow.ScoreOutputTransformRequest.Builder pr_pie = Workflow.ScoreOutputTransformRequest.newBuilder()
//                .setPlugin("pim-extractStreamingTextRegion").setDomain("asr-partial-result-sentence")
//                .setTraitInput(Olive.TraitType.REGION_SCORER)
//                .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER);

        // Use one mono (if stereo) audio input, having an 8K sample rate, should this be how we specify a stream?
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
                .setDescription("SAD and LID Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MAIN")
                        .setTraitOutput(Olive.TraitType.SYNC_STREAMING_ANALYZER) // the task with the business logic
                        .setMessageType(Olive.MessageType.SYNC_STREAMING_REQUEST)
                        .setMessageData(stream_mgr_task.build().toByteString())
                        .setConsumerDataLabel("")
                        .setConsumerResultLabel("Streaming Manager")
                        .setDescription("Streaming Manager Task")
                        .setReturnResult(false))
                // STREAMING SAD
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.REGION_SCORER_STREAMING_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setDescription("Streaming SAD Regions")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                // STREAMING LID
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_STREAMING_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Streaming LID")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_STREAMING_REQUEST)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SID")
                        .setDescription("Streaming SID")
                        .setReturnResult(true));



        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Streaming Analysis");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_example.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            }
            else{
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }

    }

    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void test_STREAM_SAD_ONLY() throws Exception {

        String job_name = "Streaming SAD ";
        String file_prefix = "stream_sad";

        Stream.RegionScorerStreamingRequest.Builder sad_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("sad-dnn-stream").setDomain("multi-v1");

        // The streaming pimiento:
        Stream.SyncStreamingRequest.Builder stream_mgr_task = Stream.SyncStreamingRequest.newBuilder().setPlugin("pim-general-streaming").setDomain("default");


        // Use one mono (if stereo) audio input, having an 8K sample rate, should this be how we specify a stream?
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
                .setDescription("SAD and LID Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MAIN")
                        .setTraitOutput(Olive.TraitType.SYNC_STREAMING_ANALYZER) // the task with the business logic
                        .setMessageType(Olive.MessageType.SYNC_STREAMING_REQUEST)
                        .setMessageData(stream_mgr_task.build().toByteString())
                        .setConsumerDataLabel("")
                        .setConsumerResultLabel("Streaming Manager")
                        .setDescription("Streaming Manager Task")
                        .setReturnResult(false))
                // STREAMING SAD
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.REGION_SCORER_STREAMING_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setDescription("Streaming SAD Regions")
                        .setAllowFailure(false)
                        .setReturnResult(true));



        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Streaming Analysis");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_example.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            }
            else{
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }

    }

    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void test_STREAM_ASR_ONLY() throws Exception {

        String job_name = "Streaming ASR ";
        String file_prefix = "stream_asr";

//        Stream.RegionScorerStreamingRequest.Builder sad_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("sad-dnn-stream").setDomain("multi-v1");
        Stream.RegionScorerStreamingRequest.Builder asr_task = Stream.RegionScorerStreamingRequest.newBuilder().setPlugin("asr-dynapy-streaming-v3.0.0").setDomain("english-tdnnChain-tel-v1");

        // The streaming pimiento:
        Stream.SyncStreamingRequest.Builder stream_mgr_task = Stream.SyncStreamingRequest.newBuilder().setPlugin("pim-general-streaming").setDomain("default");


        // Use one mono (if stereo) audio input, having an 8K sample rate, should this be how we specify a stream?
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
                .setDescription("ASR Streaming")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MAIN")
                        .setTraitOutput(Olive.TraitType.SYNC_STREAMING_ANALYZER) // the task with the business logic
                        .setMessageType(Olive.MessageType.SYNC_STREAMING_REQUEST)
                        .setMessageData(stream_mgr_task.build().toByteString())
                        .setConsumerDataLabel("")
                        .setConsumerResultLabel("Streaming Manager")
                        .setDescription("Streaming Manager Task")
                        .setReturnResult(false))
                // STREAMING SAD
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER_STREAMING)
                        .setMessageType(Olive.MessageType.REGION_SCORER_STREAMING_REQUEST)
                        .setMessageData(asr_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Streaming ASR Regions")
                        .setAllowFailure(false)
                        .setReturnResult(true));



        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Streaming Analysis");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_example.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            }
            else{
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }

    }

    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void test_FDI() throws Exception {

        String job_name = "Face Detection Using an Image";
        String file_prefix = "fdi";

        String plugin_name = "fdi-pyEmbed-v1.0.0";
        String domain_name = "multi-v1";
        Olive.RegionScorerRequest.Builder rec_task = Olive.RegionScorerRequest.newBuilder().setPlugin(plugin_name).setDomain(domain_name);
        Olive.ClassModificationRequest.Builder enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setPlugin(plugin_name)
                .setDomain(domain_name)
                .setClassId("none");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);


        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("FDI Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FDI")
                        .setTraitOutput(Olive.TraitType.BOUNDING_BOX_SCORER)
                        .setMessageType(Olive.MessageType.BOUNDING_BOX_REQUEST)
                        .setMessageData(rec_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FDI")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setAllowFailure(false)
                        .setReturnResult(true));

        // We DO NOT support enrollment

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            Assert.fail("Workflow request message failed: " + result.getError());
        }

    }

    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void test_FRI() throws Exception {

        String job_name = "Face Recognition Using an Image";
        String file_prefix = "fri";

        String plugin_name = "fri-pyEmbed-v1.0.0";
        String domain_name = "multi-v1";
        Olive.RegionScorerRequest.Builder rec_task = Olive.RegionScorerRequest.newBuilder().setPlugin(plugin_name).setDomain(domain_name);
        Olive.ClassModificationRequest.Builder enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setPlugin(plugin_name)
                .setDomain(domain_name)
                .setClassId("none");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);


        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("FRI Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRI")
                        .setTraitOutput(Olive.TraitType.BOUNDING_BOX_SCORER)
                        .setMessageType(Olive.MessageType.BOUNDING_BOX_REQUEST)
                        .setMessageData(rec_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRI")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setAllowFailure(false)
                        .setReturnResult(true));

        // We only support image enrollment
        Workflow.DataHandlerProperty.Builder enroll_data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);
        Workflow.JobDefinition.Builder enrollJob = Workflow.JobDefinition.newBuilder()
                .setJobName("FRI Enrollment")
                .setDataProperties(enroll_data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRI")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRI Enroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Workflow.DataHandlerProperty.Builder unenroll_data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(0)
                .setMaxNumberInputs(0)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);
        Workflow.JobDefinition.Builder unenrollJob = Workflow.JobDefinition.newBuilder()
                .setJobName("FRI Unenrollment")
                .setDataProperties(unenroll_data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRI")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(enroll_task.build().toByteString())
                        .setConsumerDataLabel("")
                        .setConsumerResultLabel("FRI Unenroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");
        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollJob)
                .setOrderName("Enrollment Order");
        Workflow.WorkflowOrderDefinition.Builder unenrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(unenrollJob)
                .setOrderName("Unenrollment Order");


        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            Assert.fail("Workflow request message failed: " + result.getError());
        }

    }
    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void test_dynamic_FR() throws Exception {

        String job_name = "Face Recognition Using Video or Image";
        String file_prefix = "dynamic_fr";

        Workflow.Plugin2PluginRequest.Builder pimento_task = Workflow.Plugin2PluginRequest.newBuilder().setPlugin("data-chooser").setDomain("lid-names-asr");

        String plugin_name = "img-mock";
        String domain_name = "face-v1";
//        Olive.RegionScorerRequest.Builder rec_task = Olive.RegionScorerRequest.newBuilder().setPlugin(plugin_name).setDomain(domain_name);
        Olive.ClassModificationRequest.Builder enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setPlugin(plugin_name)
                .setDomain(domain_name)
                .setClassId("none");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        // TODO HOW TO HANDLE THIS IF WE DONT KNOW THE DATA TYPE TO USE..
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setConsumerDataLabel("data")
                .setType(Olive.InputDataType.BINARY)
                .setPreprocessingRequired(true);


        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("Conditional Face Recognition")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)
                // Should be one of these...
//                .addTransferResultLabels("video")
//                .addTransferResultLabels("image")
                .addTransferResultLabels("data")
                .addTransferResultLabels("DYNAMIC_FR")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("DYNAMIC_FR")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)
                        .setMessageData(pimento_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("data").setPluginKeywordName("global_scores").build())
                        .setAllowFailure(false)
                        .setReturnResult(true));


        // Create a dynamic job, that will be completed based on the results of the previous job
        Workflow.DynamicPluginRequest.Builder dynamic_fr_task = Workflow.DynamicPluginRequest.newBuilder().setConditionalTaskName("DYNAMIC_FR");
        Workflow.JobDefinition.Builder dynamic_job = Workflow.JobDefinition.newBuilder()
                .setJobName("Dynamic Face Recognition")
                .addDynamicJobName(job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_props)
                .setDescription("Test dynamic selection of the Face Recognition plugin/domain")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // should not matter in this case --
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FR")
                        .setTraitOutput(Olive.TraitType.BOUNDING_BOX_SCORER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_fr_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FR")
                        .setDescription("Dynamic FR Task")
                        .setReturnResult(true));

        // We only support image enrollment
        Workflow.DataHandlerProperty.Builder enroll_data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);
        Workflow.JobDefinition.Builder enrollJob = Workflow.JobDefinition.newBuilder()
                .setJobName("FRV Enrollment")
                .setDataProperties(enroll_data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRV")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV Enroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Workflow.DataHandlerProperty.Builder unenroll_data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(0)
                .setMaxNumberInputs(0)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);
        Workflow.JobDefinition.Builder unenrollJob = Workflow.JobDefinition.newBuilder()
                .setJobName("FRV Unenrollment")
                .setDataProperties(unenroll_data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRV")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV Unenroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(dynamic_job)
                .setOrderName("Analysis Order");
        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollJob)
                .setOrderName("Enrollment Order");
        Workflow.WorkflowOrderDefinition.Builder unenrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(unenrollJob)
                .setOrderName("Unenrollment Order");


        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            Assert.fail("Workflow request message failed: " + result.getError());
        }

    }

    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void test_FRV() throws Exception {

        String job_name = "Face Recognition Using Video";
        String file_prefix = "frv";

        String plugin_name = "frv-pyEmbed-v1.0.0";
        String domain_name = "multi-v1";
        Olive.RegionScorerRequest.Builder rec_task = Olive.RegionScorerRequest.newBuilder().setPlugin(plugin_name).setDomain(domain_name);
        Olive.ClassModificationRequest.Builder enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setPlugin(plugin_name)
                .setDomain(domain_name)
                .setClassId("none");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.VIDEO)
                .setPreprocessingRequired(true);


        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("FRV Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRV")
                        .setTraitOutput(Olive.TraitType.BOUNDING_BOX_SCORER)
                        .setMessageType(Olive.MessageType.BOUNDING_BOX_REQUEST)
                        .setMessageData(rec_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("video").setPluginKeywordName("data").build())
                        .setAllowFailure(false)
                        .setReturnResult(true));

        // We only support image enrollment
        Workflow.DataHandlerProperty.Builder enroll_data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);
        Workflow.JobDefinition.Builder enrollJob = Workflow.JobDefinition.newBuilder()
                .setJobName("FRV Enrollment")
                .setDataProperties(enroll_data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRV")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV Enroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Workflow.DataHandlerProperty.Builder unenroll_data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(0)
                .setMaxNumberInputs(0)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);
        Workflow.JobDefinition.Builder unenrollJob = Workflow.JobDefinition.newBuilder()
                .setJobName("FRV Unenrollment")
                .setDataProperties(unenroll_data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FRV")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV Unenroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");
        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollJob)
                .setOrderName("Enrollment Order");
        Workflow.WorkflowOrderDefinition.Builder unenrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(unenrollJob)
                .setOrderName("Unenrollment Order");


        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            Assert.fail("Workflow request message failed: " + result.getError());
        }

    }

    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void test_FDV() throws Exception {

        String job_name = "Face Detection Using Video";
        String file_prefix = "fdv";

        String plugin_name = "fdv-pyEmbed-v1.0.0";
        String domain_name = "multi-v1";
        Olive.RegionScorerRequest.Builder rec_task = Olive.RegionScorerRequest.newBuilder().setPlugin(plugin_name).setDomain(domain_name);
        Olive.ClassModificationRequest.Builder enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setPlugin(plugin_name)
                .setDomain(domain_name)
                .setClassId("none");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.VIDEO)
                .setPreprocessingRequired(true);


        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("FDV Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("FDV")
                        .setTraitOutput(Olive.TraitType.BOUNDING_BOX_SCORER)
                        .setMessageType(Olive.MessageType.BOUNDING_BOX_REQUEST)
                        .setMessageData(rec_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FDV")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("video").setPluginKeywordName("data").build())
                        .setAllowFailure(false)
                        .setReturnResult(true));

        // We DO NOT support enrollment for FDV
        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            Assert.fail("Workflow request message failed: " + result.getError());
        }

    }


    @Test
    /**
     * Use this "test" to create protobufs for testing ASDEC plugin(s)
     */
    public void testSAD_LID_SID() throws Exception {

        String job_name = "SAD, LID, and SID workflow";
        String file_prefix = "sad_lid_sid";

        // To add plugin specific options:
        //.addOption(Olive.OptionValue.newBuilder().setName("foo").setValue("bar").build());
        String sad_plugin_name = "sad-dnn-v7.0.1";
        String lid_plugin_name = "lid-embedplda-v2.0.1";
        String sid_plugin_name = "sid-dplda-v2.0.1";
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin(sad_plugin_name).setDomain("multi-v1");
        Olive.GlobalScorerRequest.Builder lid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin(lid_plugin_name).setDomain("multi-v1");
        Olive.GlobalScorerRequest.Builder sid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin(sid_plugin_name).setDomain("multi-v1");

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
                .setDescription("SAD and LID Processing")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                .setDescription("Speech Regions")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
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

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_example.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            Assert.fail("Workflow request message failed: " + result.getError());
        }

    }

    @Test
    public void testSAD_LID_stereo_SID() throws Exception {

        String analysisjob_name = "Mono SAD LID, Stereo SID";

        String file_prefix = "mono_sad_lid_stereo_sid";

        String sad_plugin_name = "sad-dnn-v7.0.1";
        String lid_plugin_name = "lid-embedplda-v2.0.1";
        String sid_plugin_name = "sid-dplda-v2.0.1";
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin(sad_plugin_name).setDomain("multi-v1");
        Olive.GlobalScorerRequest.Builder lid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin(lid_plugin_name).setDomain("multi-v1");
        Olive.GlobalScorerRequest.Builder sid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin(sid_plugin_name).setDomain("multi-v1");


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
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setDescription("Speech Regions")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setReturnResult(true));

        Workflow.JobDefinition.Builder sidAnalysisJob = Workflow.JobDefinition.newBuilder()
                .setJobName("Stereo SID")
//                .setJobName(analysisjob_name)
                .setDataProperties(sid_data_props)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setDescription("Stereo SID")
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

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_example.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
/*        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if(result.hasError() || result.getRep().hasError() ){
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            }else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }*/

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
     * Use this "test" to create protobufs for testing
     */
    public void testQuality_Workflow() throws Exception {

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
                        .setDescription("Speaker Identification"));

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

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            Assert.fail("Workflow request message failed: " + result.getError());
        }

    }


    private void checkError(Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result) {
        String err = result.getError();
        if (null == err) {
            err = result.getRep().getError();
        }
        if (null == err) {
            Assert.fail("Workflow request message failed: " + err);
        }
    }

    private void checkAnalysisError(Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> result) {
        String err = result.getError();
        if (null == err) {
            err = result.getRep().getError();
        }
        if (null == err) {
            Assert.fail("Workflow request message failed: " + err);
        }
    }


    @Test
    public void testASR() throws Exception {
        // Workflow that does ASR only

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

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);


    }


    @Test
    public void testSAD_LID_SID_Enrollment() throws Exception {

        String analysisjob_name = "SAD, LID, SID analysis with LID and/or SID enrollment";

        String file_prefix = "sad-lid-sid_enroll-lid-sid";

        // Create a workflow definition that does SAD (frames) and LID.  A specific plugin/domain is not defined for
        // either SAD/LID task

        // Use real plugin names
        String sad_plugin_name = "sad-dnn-v7.0.1";
        String lid_plugin_name = "lid-embedplda-v2.0.1";
        String sid_plugin_name = "sid-dplda-v2.0.1";
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin(sad_plugin_name).setDomain("multi-v1");
        Olive.GlobalScorerRequest.Builder lid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin(lid_plugin_name).setDomain("multi-v1");
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
                        .setDescription("Speech Regions")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setDescription("Speaker ID")
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

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_example.workflow").toFile());
        wfd.build().writeTo(fos);


    }

    @Test
    public void testConnection() throws Exception {

        String clientID = "connection test";
        String oliveSeverName = "172.16.107.149";
//       bad
//        String oliveSeverName = "172.16.107.101";
        int streaming_port_number = 5591;
        int timeout = 10000;

        StreamingServer stream = new StreamingServer();
        stream.connect(clientID, oliveSeverName, streaming_port_number, timeout/1000);

        System.out.println("Connected!");
        String fileName = "/Users/e24652/dev/olive-data/Spanish.wav";
        AudioInputStream ais = AudioUtil.convertWave2Stream(Paths.get(fileName).toFile());
        byte[] samples = AudioUtil.convertWav2ByteArray(ais);
        int sampleRate = (int)ais.getFormat().getSampleRate();
        Olive.AudioBitDepth bitDepth = ClientUtils.getAudioDepth(ais.getFormat());
        int channelNumber = 0;
        int numberChannels = ais.getFormat().getChannels();
        Olive.Audio.Builder audio = null;
        // Do we need to resend the sample rate, bit depth, etc if we include it in the streaming request?
        audio = ClientUtils.createAudioFromDecodedBytes(fileName, samples, numberChannels, sampleRate, channelNumber, bitDepth, new ArrayList<>());
        stream.enqueueAudio(audio.build());
        Thread.sleep(5000);

    }
    @Test
    /**
     */
    public void testCreating_CONDITIONAL_ASR_Analysis() throws Exception {

        // We use static (not abstract) plugin names here to make this workflow easier to read/modify
        String job_name = "Conditional ASR Workflow";
        String file_prefix = "conditional_static_asr";

        // Create a workflow definition that does SAD (frames), LID, CONDITIONAL PIMENTO, then in a new job it does
        // ASR using plugin/domains from the pimento.

        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sad-dnn-v7.0.1").setDomain("multi-v1");
        Olive.GlobalScorerRequest.Builder lid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("lid-embedplda-v3.0.0").setDomain("multi-v1");
        // Pimento/plugin that consumes global scores from LID and picks the best ASR plugin/domain
        // If uisng the latest LID plugin we use language names, not codes
            Workflow.Plugin2PluginRequest.Builder pimento_task = Workflow.Plugin2PluginRequest.newBuilder().setPlugin("map-routerGlobal").setDomain("lid_to_asr-dynapy-v3.0.0");

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

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            } else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }


        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if (analysisResult.hasError() || analysisResult.getRep().hasError()) {
            if (analysisResult.hasError()) {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getError());
            } else {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getRep().getError());
            }
        }

    }

    public void test_CONDITIONAL_ASR_MT_Analysis_orig() throws Exception {

        // We use static (not abstract) plugin names here to make this workflow easier to read/modify
        String job_name = "Conditional ASR and MT Workflow";
        String file_prefix = "conditional_asr_and_mt";

        // We do SAD, LID, then the router pimientos to choose the ASR and MT plugins to choose the correct ASR and MT domains
        // based on the output from the LID plugin
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sad-dnn-v7.0.1").setDomain("multi-v1");
        Olive.GlobalScorerRequest.Builder lid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("lid-embedplda-v3.0.0").setDomain("multi-v1");

        // Pimientos
        Workflow.Plugin2PluginRequest.Builder pimento_asr = Workflow.Plugin2PluginRequest.newBuilder().setPlugin("map-routerGlobal").setDomain("lid_to_asr-dynapy-v3.0.0");
        Workflow.Plugin2PluginRequest.Builder pimento_txt = Workflow.Plugin2PluginRequest.newBuilder().setPlugin("map-routerGlobal").setDomain("lid_to_txt-neural-v3.0.0");
        // Pimiento to convert ASR region scores to a text string for ingest by MT
        Workflow.DataOutputTransformRequest.Builder mt_pit_task = Workflow.DataOutputTransformRequest.newBuilder().setPlugin("pim-extractTextRegion").setDomain("test-ldd-asr")
                .setDataOutput(Olive.InputDataType.TEXT);

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
                .setDescription("SAD, LID, WITH Conditional ASR and MT Processing")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // IMPORTANT - THIS JOB MUST FINISH FIRST BEFORE THE DYNAMIC JOB CAN BE START
                .addTransferResultLabels("audio")
                .addTransferResultLabels("CONDITIONAL_ASR")
                .addTransferResultLabels("CONDITIONAL_MT")
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
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("CONDITIONAL_ASR")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)
                        .setMessageData(pimento_asr.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes lid global scores, no data input
                        .setConsumerResultLabel("CONDITIONAL_ASR")
                        .setDescription("ASR Conditional Task Composer")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("LID").setPluginKeywordName("global_scores").build())
                        .setAllowFailure(true)
                        .setReturnResult(false))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("CONDITIONAL_MT")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)
                        .setMessageData(pimento_txt.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes lid global scores, no data input
                        .setConsumerResultLabel("CONDITIONAL_MT")
                        .setDescription("MT Conditional Task Composer")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("LID").setPluginKeywordName("global_scores").build())
                        .setAllowFailure(true)
                        .setReturnResult(false));


        // Create a dynamic job, that will be completed based on the results of the previous job
        Workflow.DynamicPluginRequest.Builder dynamic_asr_task = Workflow.DynamicPluginRequest.newBuilder().setConditionalTaskName("CONDITIONAL_ASR");
        Workflow.DynamicPluginRequest.Builder dynamic_mt_task = Workflow.DynamicPluginRequest.newBuilder()
                .setConditionalTaskName("CONDITIONAL_MT");
        Workflow.JobDefinition.Builder dynamic_asr_job = Workflow.JobDefinition.newBuilder()
                .setJobName("Dynamic ASR")
                .addDynamicJobName(job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_props)
                .setDescription("Dynamic selection of the ASR plugin/domain")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // should not matter in this case --
                .addTransferResultLabels("ASR")  // The MT pimiento  will need ASR results
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_asr_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Dynamic ASR Task")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR TEXT")
                        .setTraitOutput(Olive.TraitType.DATA_OUTPUT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.DATA_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(mt_pit_task.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR TEXT")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setReturnResult(false))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_mt_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("text")
                        .setConsumerResultLabel("MT")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR TEXT").setPluginKeywordName("text").build())
                        .setDescription("Dynamic MT Task")
                        .setReturnResult(true));

        // And finally, create a dynamic MT job to handle the ASR output (needs the MT plugin/domain and ASR output)
/*        Olive.DataHandlerProperty.Builder data_text_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.TEXT)
                .setPreprocessingRequired(false);
        Olive.DynamicPluginRequest.Builder dynamic_mt_task = Olive.DynamicPluginRequest.newBuilder()
                .setConditionalTaskName("CONDITIONAL_MT");
        Olive.JobDefinition.Builder dynamic_text_job = Olive.JobDefinition.newBuilder()
                .setJobName("Dynamic MT")
                .addDynamicJobName(job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_text_props)
                .setDescription("Dynamic selection of the MT plugin/domain using dynamic ASR input")
                .setProcessingType(Olive.WorkflowJobType.SEQUENTIAL)  // has to run after the ASR task, since we need ASR result
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("ASR TEXT")
                        .setTraitOutput(Olive.TraitType.DATA_OUTPUT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.DATA_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(mt_pit_task.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR TEXT")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setReturnResult(false))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("MT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_mt_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("text")
                        .setConsumerResultLabel("MT")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("ASR TEXT").setPluginKeywordName("text").build())
                        .setDescription("Dynamic MT Task")
                        .setReturnResult(true));*/

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(dynamic_asr_job)
//                .addJobDefinition(dynamic_text_job)
                .setOrderName("ASR and MT Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        Path path = FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow");
        FileOutputStream fos = new FileOutputStream(path.toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            } else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }


        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/dev/olive-data/Spanish.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if (analysisResult.hasError() || analysisResult.getRep().hasError()) {
            if (analysisResult.hasError()) {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getError());
            } else {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getRep().getError());
            }
        }
        else {

//            Olive.WorkflowDefinition wdTest = Olive.WorkflowDefinition.parseFrom(new FileInputStream(path.toFile()));
            //Olive.WorkflowDefinition wdTest2 = Olive.WorkflowDefinition.parseFrom(new FileInputStream("/Users/e24652/workflows/conditional_asr_and_mt.workflow.json"));
//            OliveWorkflowDefinition owd = new OliveWorkflowDefinition("/Users/e24652/workflows/conditional_asr_and_mt.workflow.json");
            System.out.println("Workflow results: "  );

            try {
                Map<String, JobResult>  jobResults = WorkflowUtils.extractWorkflowAnalysis(analysisResult.getRep());
                log.info("Workflow results: {}", jobResults);

                // do something with the results:
                if (!analysisResult.hasError()) {

                    for (String jobName : jobResults.keySet()) {
                        // print results
                        JobResult jr = jobResults.get(jobName);

                        System.out.println("Job: " + jobName);
                        for(String taskName:  jr.getTasks().keySet()){
                            List<TaskResult> trs = jr.getTasks().get(taskName);
                            for (TaskResult tr : trs) {
                                if (!tr.isError()) {
                                    System.out.println(String.format("%s = %s", taskName, tr.getTaskMessage().toString()));
                                } else {
                                    System.out.println(String.format("Task '%s' FAILED: %s ", taskName, tr.getErrMsg()));
                                }
                            }
                        }

                        System.out.println("");
                        System.out.println("");
                        System.out.println("Job Data Info ------------");
                        //                        System.out.println("");
                        for(DataResult dr : jr.getDataResults()){
                            System.out.println(String.format("Data ID: %s", dr.getDataName()));
                            System.out.println(String.format("%s", dr.getDataMessage().toString()));
                        }
                    }

                } else {
                    log.error("Workflow request failed: {}", analysisResult.getError());
                }
            } catch (Exception e) {
                log.error("Workflow request failed with error: ", e);
            }
        }

    }

    @Test
    /**
     */
    public void test_Smart_Transcription() throws Exception {

        // We use static (not abstract) plugin names here to make this workflow easier to read/modify
        //String job_name = "Conditional ASR and MT Workflow";
        String file_prefix = "smart_transcription";

        // Plugins used

        String sddPluginName = "sdd-diarizeEmbedSmolive-v1.0.0";
        String sddDomainName = "telClosetalk-int8-v1";

        String sadPluginName = "sad-dnn-v7.0.1";
        String sadDomainName = "multi-v1";

        String lddPluginName = "ldd-embedpldaSmolive-v1.0.0";
        String lddDomainName = "multi-int8-v1";

        String gddPluginName = "gdd-embedplda-v1.0.0";
        String gddDomainName = "multi-v1";

//        String sddPluginName = "";
//        String sddDomainName = "";

        String pimRouterPluginName = "map-routerRegion-v1.0.0";
        String pimRouterASRDomainName = "ldd_to_asr-dynapy-v3.0.0";
        String pimRouterMTDomainName = "lid_to_txt-neural-v3.0.0";

        // We do SAD, LID, then the router pimientos to choose the ASR and MT plugins to choose the correct ASR and MT domains
        // based on the output from the LID plugin
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin(sadPluginName).setDomain(sadDomainName);
        Olive.RegionScorerRequest.Builder gdd_task = Olive.RegionScorerRequest.newBuilder().setPlugin(gddPluginName).setDomain(gddDomainName);
        Olive.RegionScorerRequest.Builder ldd_task = Olive.RegionScorerRequest.newBuilder().setPlugin(lddPluginName).setDomain(lddDomainName);
        Olive.RegionScorerRequest.Builder sdd_task = Olive.RegionScorerRequest.newBuilder().setPlugin(sddPluginName).setDomain(sddDomainName);

        // Pimientos
        Workflow.Plugin2PluginRequest.Builder pimento_asr = Workflow.Plugin2PluginRequest.newBuilder().setPlugin(pimRouterPluginName).setDomain(pimRouterASRDomainName);
        Workflow.Plugin2PluginRequest.Builder pimento_txt = Workflow.Plugin2PluginRequest.newBuilder().setPlugin(pimRouterPluginName).setDomain(pimRouterMTDomainName);

        // Enrollment
        Olive.ClassModificationRequest.Builder sddEnrollTask = Olive.ClassModificationRequest.newBuilder()
                .setPlugin(sddPluginName)
                .setDomain(sddDomainName)
                .setClassId("none");

        // Un-enrollment
        Olive.ClassRemovalRequest.Builder sddUnenrollTask = Olive.ClassRemovalRequest.newBuilder()
                .setClassId("none")
                .setPlugin(sddPluginName)
                .setDomain(sddDomainName);


        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two tasks (SAD and LID), both results should be returned to the client
        String base_job_name = "Smart Transcription Workflow";
        Workflow.JobDefinition.Builder base_job = Workflow.JobDefinition.newBuilder()
                .setJobName(base_job_name)
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("Smart Transcription Workflow")
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
                        .setTask("GDD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(gdd_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("GDD")
                        .setDescription("Gender Detection")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LDD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(ldd_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LDD")
                        .setDescription("Language Detection")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("CONDITIONAL_ASR")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)
                        .setMessageData(pimento_asr.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes lid global scores, no data input
                        .setConsumerResultLabel("CONDITIONAL_ASR")
                        .setDescription("ASR Conditional Task Composer")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("LDD").setPluginKeywordName("global_scores").build())
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SDD").setPluginKeywordName("sdd_scores").build())
                        .setAllowFailure(true)
                        .setReturnResult(false));


        String dynamic_asr_job_name = "Dynamic ASR";
        // Create a dynamic job, that will be completed based on the results of the previous job
        Workflow.DynamicPluginRequest.Builder dynamic_asr_task = Workflow.DynamicPluginRequest.newBuilder().setConditionalTaskName("CONDITIONAL_ASR");
//        Workflow.DynamicPluginRequest.Builder dynamic_mt_task = Workflow.DynamicPluginRequest.newBuilder()
//                .setConditionalTaskName("CONDITIONAL_MT");
        Workflow.JobDefinition.Builder dynamic_asr_job = Workflow.JobDefinition.newBuilder()
                .setJobName(dynamic_asr_job_name)
                .addDynamicJobName(base_job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_props)
                .setDescription("Dynamic selection of the ASR plugin/domain")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // needs to run before MT, since it creates the text data consumed by the MT task
                .addTransferResultLabels("ASR TEXT")  // The MT pimiento  will need ASR results
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_asr_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Dynamic ASR Task")
                        .setReturnResult(true));

        Workflow.JobDefinition.Builder enrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("SDD Enrollment")
                .setDataProperties(data_props)
                .setDescription("Provides SDD enrollment")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SDD_Enroll")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(sddEnrollTask.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SDD_Enroll")
                        .setAllowFailure(false)
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
                        .setReturnResult(true)
                        .setDescription("Speaker Detection"));

        Workflow.JobDefinition.Builder unenrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("Unenrollment for SDD")
                .setDataProperties(data_props)
                .setDescription("SDD Unenrollment job ")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SDD")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(sddUnenrollTask.build().toByteString())
                        .setConsumerDataLabel("")//  Does not use data...
                        .setConsumerResultLabel("SDD_Unenroll")
                        .setAllowFailure(false)
                        .setReturnResult(true));
        // ORDERS

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(base_job)
                .addJobDefinition(dynamic_asr_job)
                .setOrderName("SAD, GDD, LDD, ASR Analysis");

        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollmentJob)
                .setOrderName("SDD Enrollment");

        Workflow.WorkflowOrderDefinition.Builder unenrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(unenrollmentJob)
                .setOrderName("SDD Unenrollment");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setActualized(false);

        Path path = FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow");
        FileOutputStream fos = new FileOutputStream(path.toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            } else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }


        if (true) {
            return;
        }

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/dev/olive-data/Spanish.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if (analysisResult.hasError() || analysisResult.getRep().hasError()) {
            if (analysisResult.hasError()) {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getError());
            } else {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getRep().getError());
            }
        }
        else {

//            Olive.WorkflowDefinition wdTest = Olive.WorkflowDefinition.parseFrom(new FileInputStream(path.toFile()));
            //Olive.WorkflowDefinition wdTest2 = Olive.WorkflowDefinition.parseFrom(new FileInputStream("/Users/e24652/workflows/conditional_asr_and_mt.workflow.json"));
//            OliveWorkflowDefinition owd = new OliveWorkflowDefinition("/Users/e24652/workflows/conditional_asr_and_mt.workflow.json");
            System.out.println("Workflow results: "  );

            try {
                Map<String, JobResult>  jobResults = WorkflowUtils.extractWorkflowAnalysis(analysisResult.getRep());
                log.info("Workflow results: {}", jobResults);

                // do something with the results:
                if (!analysisResult.hasError()) {

                    for (String jobName : jobResults.keySet()) {
                        // print results
                        JobResult jr = jobResults.get(jobName);

                        System.out.println("Job: " + jobName);
                        for(String taskName:  jr.getTasks().keySet()){
                            List<TaskResult> trs = jr.getTasks().get(taskName);
                            for (TaskResult tr : trs) {
                                if (!tr.isError()) {
                                    System.out.println(String.format("%s = %s", taskName, tr.getTaskMessage().toString()));
                                } else {
                                    System.out.println(String.format("Task '%s' FAILED: %s ", taskName, tr.getErrMsg()));
                                }
                            }
                        }

                        System.out.println("");
                        System.out.println("");
                        System.out.println("Job Data Info ------------");
                        //                        System.out.println("");
                        for(DataResult dr : jr.getDataResults()){
                            System.out.println(String.format("Data ID: %s", dr.getDataName()));
                            System.out.println(String.format("%s", dr.getDataMessage().toString()));
                        }
                    }

                } else {
                    log.error("Workflow request failed: {}", analysisResult.getError());
                }
            } catch (Exception e) {
                log.error("Workflow request failed with error: ", e);
            }
        }

    }

    @Test
    /**
     */
    public void test_CCU_SAD_SDD_ASR_MT() throws Exception {

        // We use static (not abstract) plugin names here to make this workflow easier to read/modify
        //String job_name = "Conditional ASR and MT Workflow";
        String file_prefix = "ccu_mandarin";

        // Plugins used

        String sddPluginName = "sdd-diarizeEmbedSmolive-v1.0.0";
        String sddDomainName = "telClosetalk-smart-v1";

        String sadPluginName = "sad-dnn-v7.0.1";
        String sadDomainName = "multi-v1";

//        String lddPluginName = "ldd-embedpldaSmolive-v1.0.0";
//        String lddDomainName = "multi-int8-v1";

        String asrPluginName = "asr-dynapy-v3.0.0";
        String asrDomainName = "mandarin-tdnnChain-tel-v1";

        String mtPluginName = "tmt-neural-v1.0.0";
        String mtDomainName = "cmn-eng-nmt-v1";

        String pimMTPluginName = "pim-extractTextRegion-v1.0.0";
        String pimMTDomainnName = "asr-mt-v1";

        // We do SAD, LID, then the router pimientos to choose the ASR and MT plugins to choose the correct ASR and MT domains
        // based on the output from the LID plugin
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin(sadPluginName).setDomain(sadDomainName);
        Olive.RegionScorerRequest.Builder asr_task = Olive.RegionScorerRequest.newBuilder().setPlugin(asrPluginName).setDomain(asrDomainName);
//        Olive.RegionScorerRequest.Builder ldd_task = Olive.RegionScorerRequest.newBuilder().setPlugin(lddPluginName).setDomain(lddDomainName);
        Olive.RegionScorerRequest.Builder sdd_task = Olive.RegionScorerRequest.newBuilder().setPlugin(sddPluginName).setDomain(sddDomainName);
        Olive.TextTransformationRequest.Builder mt_task = Olive.TextTransformationRequest.newBuilder().setPlugin(mtPluginName).setDomain(mtDomainName);

        // Pimientos
        Workflow.DataOutputTransformRequest.Builder mt_pit_task = Workflow.DataOutputTransformRequest.newBuilder().setPlugin(pimMTPluginName).setDomain(pimMTDomainnName)
                .setDataOutput(Olive.InputDataType.TEXT);

        // Enrollment
        Olive.ClassModificationRequest.Builder sddEnrollTask = Olive.ClassModificationRequest.newBuilder()
                .setPlugin(sddPluginName)
                .setDomain(sddDomainName)
                .setClassId("none");

        // Un-enrollment
        Olive.ClassRemovalRequest.Builder sddUnenrollTask = Olive.ClassRemovalRequest.newBuilder()
                .setClassId("none")
                .setPlugin(sddPluginName)
                .setDomain(sddDomainName);


        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two tasks (SAD and LID), both results should be returned to the client
        String base_job_name = "SAD, SDD, and ASR Mandarin Workflow";
        Workflow.JobDefinition.Builder base_job = Workflow.JobDefinition.newBuilder()
                .setJobName(base_job_name)
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("SAD, SDD, ASR Workflow Job")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)
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
                        .setTask("SDD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sdd_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SDD")
                        .setDescription("Speaker Detection")
                        .setAllowFailure(false)
                        .setReturnResult(true))
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("LDD")
//                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
//                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
//                        .setMessageData(ldd_task.build().toByteString())
//                        .setConsumerDataLabel("audio")
//                        .setConsumerResultLabel("LDD")
//                        .setDescription("Language Detection")
//                        .setAllowFailure(false)
//                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(asr_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Mandarin Recognition")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR TEXT")
                        .setTraitOutput(Olive.TraitType.DATA_OUTPUT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.DATA_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(mt_pit_task.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR TEXT")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setReturnResult(false))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.TEXT_TRANSFORM_REQUEST)
                        .setMessageData(mt_task.build().toByteString())
                        .setConsumerDataLabel("text")
                        .setConsumerResultLabel("MT")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR TEXT").setPluginKeywordName("text").build())
                        .setAllowFailure(true)
                        .setReturnResult(true));

        Workflow.JobDefinition.Builder enrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("SDD Enrollment")
                .setDataProperties(data_props)
                .setDescription("Provides SDD enrollment")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SDD_Enroll")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(sddEnrollTask.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SDD_Enroll")
                        .setAllowFailure(false)
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
                        .setReturnResult(true)
                        .setDescription("Speaker Detection"));

        Workflow.JobDefinition.Builder unenrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("Unenrollment for SDD")
                .setDataProperties(data_props)
                .setDescription("SDD Unenrollment job ")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SDD")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(sddUnenrollTask.build().toByteString())
                        .setConsumerDataLabel("")//  Does not use data...
                        .setConsumerResultLabel("SDD_Unenroll")
                        .setAllowFailure(false)
                        .setReturnResult(true));
        // ORDERS

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(base_job)
                .setOrderName("SAD, SDD, ASR Analysis");

        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollmentJob)
                .setOrderName("SDD Enrollment");

        Workflow.WorkflowOrderDefinition.Builder unenrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(unenrollmentJob)
                .setOrderName("SDD Unenrollment");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setActualized(false);

        Path path = FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow");
        FileOutputStream fos = new FileOutputStream(path.toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            } else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }



    }

    @Test
    public void test_CCU_SAD_SDD_ASR_MT_ENGLISH() throws Exception {

        // We use static (not abstract) plugin names here to make this workflow easier to read/modify
        //String job_name = "Conditional ASR and MT Workflow";
        String file_prefix = "ccu_english";

        // Plugins used

        String sddPluginName = "sdd-diarizeEmbedSmolive-v1.0.0";
        String sddDomainName = "telClosetalk-smart-v1";

        String sadPluginName = "sad-dnn-v7.0.1";
        String sadDomainName = "multi-v1";

//        String lddPluginName = "ldd-embedpldaSmolive-v1.0.0";
//        String lddDomainName = "multi-int8-v1";

        String asrPluginName = "asr-dynapy-v3.0.0";
        String asrDomainName = "english-tdnnChain-tel-v1";

//        String mtPluginName = "asr-dynapy-v3.0.0";
//        String mtDomainName = "mandarin-tdnnChain-tel-v1";

        String pimMTPluginName = "pim-extractTextRegion-v1.0.0";
        String pimMTDomainnName = "asr-mt-v1";

        // We do SAD, LID, then the router pimientos to choose the ASR and MT plugins to choose the correct ASR and MT domains
        // based on the output from the LID plugin
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin(sadPluginName).setDomain(sadDomainName);
        Olive.RegionScorerRequest.Builder asr_task = Olive.RegionScorerRequest.newBuilder().setPlugin(asrPluginName).setDomain(asrDomainName);
//        Olive.RegionScorerRequest.Builder ldd_task = Olive.RegionScorerRequest.newBuilder().setPlugin(lddPluginName).setDomain(lddDomainName);
        Olive.RegionScorerRequest.Builder sdd_task = Olive.RegionScorerRequest.newBuilder().setPlugin(sddPluginName).setDomain(sddDomainName);
//        Olive.TextTransformationRequest.Builder mt_task = Olive.TextTransformationRequest.newBuilder().setPlugin(mtPluginName).setDomain(mtDomainName);

        // Pimientos
        Workflow.DataOutputTransformRequest.Builder mt_pit_task = Workflow.DataOutputTransformRequest.newBuilder().setPlugin(pimMTPluginName).setDomain(pimMTDomainnName)
                .setDataOutput(Olive.InputDataType.TEXT);

        // Enrollment
        Olive.ClassModificationRequest.Builder sddEnrollTask = Olive.ClassModificationRequest.newBuilder()
                .setPlugin(sddPluginName)
                .setDomain(sddDomainName)
                .setClassId("none");

        // Un-enrollment
        Olive.ClassRemovalRequest.Builder sddUnenrollTask = Olive.ClassRemovalRequest.newBuilder()
                .setClassId("none")
                .setPlugin(sddPluginName)
                .setDomain(sddDomainName);


        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two tasks (SAD and LID), both results should be returned to the client
        String base_job_name = "SAD, SDD, and ASR English Workflow";
        Workflow.JobDefinition.Builder base_job = Workflow.JobDefinition.newBuilder()
                .setJobName(base_job_name)
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("SAD, SDD, adn ASR Workflow")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)
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
                        .setTask("SDD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sdd_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SDD")
                        .setDescription("Speaker Detection")
                        .setAllowFailure(false)
                        .setReturnResult(true))
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("LDD")
//                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
//                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
//                        .setMessageData(ldd_task.build().toByteString())
//                        .setConsumerDataLabel("audio")
//                        .setConsumerResultLabel("LDD")
//                        .setDescription("Language Detection")
//                        .setAllowFailure(false)
//                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(asr_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("English Recognition")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR TEXT")
                        .setTraitOutput(Olive.TraitType.DATA_OUTPUT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.DATA_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(mt_pit_task.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR TEXT")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setReturnResult(true));
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("MT")
//                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
//                        .setMessageType(Olive.MessageType.TEXT_TRANSFORM_REQUEST)
//                        .setMessageData(mt_task.build().toByteString())
//                        .setConsumerDataLabel("text")
//                        .setConsumerResultLabel("MT")
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR TEXT").setPluginKeywordName("text").build())
//                        .setAllowFailure(true)
//                        .setReturnResult(true));

        Workflow.JobDefinition.Builder enrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("SDD Enrollment")
                .setDataProperties(data_props)
                .setDescription("Provides SDD enrollment")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SDD_Enroll")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(sddEnrollTask.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SDD_Enroll")
                        .setAllowFailure(false)
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
                        .setReturnResult(true)
                        .setDescription("Speaker Detection"));

        Workflow.JobDefinition.Builder unenrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("Unenrollment for SDD")
                .setDataProperties(data_props)
                .setDescription("SDD Unenrollment job ")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SDD")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(sddUnenrollTask.build().toByteString())
                        .setConsumerDataLabel("")//  Does not use data...
                        .setConsumerResultLabel("SDD_Unenroll")
                        .setAllowFailure(false)
                        .setReturnResult(true));
        // ORDERS

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(base_job)
                .setOrderName("SAD, SDD, ASR Analysis");

        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollmentJob)
                .setOrderName("SDD Enrollment");

        Workflow.WorkflowOrderDefinition.Builder unenrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(unenrollmentJob)
                .setOrderName("SDD Unenrollment");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setActualized(false);

        Path path = FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow");
        FileOutputStream fos = new FileOutputStream(path.toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            } else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }



    }
    @Test
    /**
     */
    public void test_Smart_Transcription_maybe_faster() throws Exception {

        // We use static (not abstract) plugin names here to make this workflow easier to read/modify
        //String job_name = "Conditional ASR and MT Workflow";
        String file_prefix = "smart_transcription_multi_job";

        // Plugins used

        String sddPluginName = "sdd-diarizeEmbedSmolive-v1.0.0";
        String sddDomainName = "telClosetalk-int8-v1";

        String sadPluginName = "sad-dnn-v7.0.1";
        String sadDomainName = "multi-v1";

        String lddPluginName = "ldd-embedpldaSmolive-v1.0.0";
        String lddDomainName = "multi-int8-v1";

        String gddPluginName = "gdd-embedplda-v1.0.0";
        String gddDomainName = "multi-v1";

//        String sddPluginName = "";
//        String sddDomainName = "";

        String pimRouterPluginName = "map-routerRegion-v1.0.0";
        String pimRouterASRDomainName = "ldd_to_asr-dynapy-v3.0.0";
        String pimRouterMTDomainName = "lid_to_txt-neural-v3.0.0";

        // We do SAD, LID, then the router pimientos to choose the ASR and MT plugins to choose the correct ASR and MT domains
        // based on the output from the LID plugin
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin(sadPluginName).setDomain(sadDomainName);
        Olive.RegionScorerRequest.Builder gdd_task = Olive.RegionScorerRequest.newBuilder().setPlugin(gddPluginName).setDomain(gddDomainName);
        Olive.RegionScorerRequest.Builder ldd_task = Olive.RegionScorerRequest.newBuilder().setPlugin(lddPluginName).setDomain(lddDomainName);
        Olive.RegionScorerRequest.Builder sdd_task = Olive.RegionScorerRequest.newBuilder().setPlugin(sddPluginName).setDomain(sddDomainName);

        // Pimientos
        Workflow.Plugin2PluginRequest.Builder pimento_asr = Workflow.Plugin2PluginRequest.newBuilder().setPlugin(pimRouterPluginName).setDomain(pimRouterASRDomainName);
        Workflow.Plugin2PluginRequest.Builder pimento_txt = Workflow.Plugin2PluginRequest.newBuilder().setPlugin(pimRouterPluginName).setDomain(pimRouterMTDomainName);

        // Enrollment
        Olive.ClassModificationRequest.Builder sddEnrollTask = Olive.ClassModificationRequest.newBuilder()
                .setPlugin(sddPluginName)
                .setDomain(sddDomainName)
                .setClassId("none");

        // Un-enrollment
        Olive.ClassRemovalRequest.Builder sddUnenrollTask = Olive.ClassRemovalRequest.newBuilder()
                .setClassId("none")
                .setPlugin(sddPluginName)
                .setDomain(sddDomainName);


        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);


        Workflow.JobDefinition.Builder sad_job = Workflow.JobDefinition.newBuilder()
                .setJobName("SAD job")
                .setDataProperties(data_props)
                .setConditionalJobOutput(false)
                .setDescription("Smart Transcription Workflow")
                .setProcessingType(Workflow.WorkflowJobType.PARALLEL)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD_REGIONS")
                        .setDescription("Speech Activity Detection")
                        .setReturnResult(true));

        Workflow.JobDefinition.Builder gdd_job = Workflow.JobDefinition.newBuilder()
                .setJobName("GDD job")
                .setDataProperties(data_props)
                .setConditionalJobOutput(false)
                .setDescription("Smart Transcription Workflow")
                .setProcessingType(Workflow.WorkflowJobType.PARALLEL)
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("GDD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(gdd_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("GDD")
                        .setDescription("Gender Detection")
                        .setAllowFailure(false)
                        .setReturnResult(true));

        String base_job_name = "Smart Transcription Workflow";
        Workflow.JobDefinition.Builder base_job = Workflow.JobDefinition.newBuilder()
                .setJobName(base_job_name)
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("Smart Transcription Workflow")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // IMPORTANT - THIS JOB MUST FINISH FIRST BEFORE THE DYNAMIC JOB CAN BE START
                .addTransferResultLabels("audio")
                .addTransferResultLabels("CONDITIONAL_ASR")
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("SAD")
//                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
//                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
//                        .setMessageData(sad_task.build().toByteString())
//                        .setConsumerDataLabel("audio")
//                        .setConsumerResultLabel("SAD_REGIONS")
//                        .setDescription("Speech Activity Detection")
//                        .setReturnResult(true))
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("GDD")
//                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
//                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
//                        .setMessageData(gdd_task.build().toByteString())
//                        .setConsumerDataLabel("audio")
//                        .setConsumerResultLabel("GDD")
//                        .setDescription("Gender Detection")
//                        .setAllowFailure(false)
//                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("LDD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(ldd_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LDD")
                        .setDescription("Language Detection")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("CONDITIONAL_ASR")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)
                        .setMessageData(pimento_asr.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes lid global scores, no data input
                        .setConsumerResultLabel("CONDITIONAL_ASR")
                        .setDescription("ASR Conditional Task Composer")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("LDD").setPluginKeywordName("global_scores").build())
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SDD").setPluginKeywordName("sdd_scores").build())
                        .setAllowFailure(true)
                        .setReturnResult(false));


        String dynamic_asr_job_name = "Dynamic ASR";
        // Create a dynamic job, that will be completed based on the results of the previous job
        Workflow.DynamicPluginRequest.Builder dynamic_asr_task = Workflow.DynamicPluginRequest.newBuilder().setConditionalTaskName("CONDITIONAL_ASR");
//        Workflow.DynamicPluginRequest.Builder dynamic_mt_task = Workflow.DynamicPluginRequest.newBuilder()
//                .setConditionalTaskName("CONDITIONAL_MT");
        Workflow.JobDefinition.Builder dynamic_asr_job = Workflow.JobDefinition.newBuilder()
                .setJobName(dynamic_asr_job_name)
                .addDynamicJobName(base_job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_props)
                .setDescription("Dynamic selection of the ASR plugin/domain")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // needs to run before MT, since it creates the text data consumed by the MT task
                .addTransferResultLabels("ASR TEXT")  // The MT pimiento  will need ASR results
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_asr_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Dynamic ASR Task")
                        .setReturnResult(true));

        Workflow.JobDefinition.Builder enrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("SDD Enrollment")
                .setDataProperties(data_props)
                .setDescription("Provides SDD enrollment")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SDD_Enroll")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(sddEnrollTask.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SDD_Enroll")
                        .setAllowFailure(false)
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
                        .setReturnResult(true)
                        .setDescription("Speaker Detection"));

        Workflow.JobDefinition.Builder unenrollmentJob = Workflow.JobDefinition.newBuilder()
                .setJobName("Unenrollment for SDD")
                .setDataProperties(data_props)
                .setDescription("SDD Unenrollment job ")
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("SDD")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(sddUnenrollTask.build().toByteString())
                        .setConsumerDataLabel("")//  Does not use data...
                        .setConsumerResultLabel("SDD_Unenroll")
                        .setAllowFailure(false)
                        .setReturnResult(true));
        // ORDERS

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(sad_job)
                .addJobDefinition(gdd_job)
                .addJobDefinition(base_job)
                .addJobDefinition(dynamic_asr_job)
                .setOrderName("SAD, GDD, LDD, ASR Analysis");

        Workflow.WorkflowOrderDefinition.Builder enrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollmentJob)
                .setOrderName("SDD Enrollment");

        Workflow.WorkflowOrderDefinition.Builder unenrollOrder = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(unenrollmentJob)
                .setOrderName("SDD Unenrollment");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setActualized(false);

        Path path = FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow");
        FileOutputStream fos = new FileOutputStream(path.toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            } else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }


        if (true) {
            return;
        }

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/dev/olive-data/Spanish.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if (analysisResult.hasError() || analysisResult.getRep().hasError()) {
            if (analysisResult.hasError()) {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getError());
            } else {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getRep().getError());
            }
        }
        else {

//            Olive.WorkflowDefinition wdTest = Olive.WorkflowDefinition.parseFrom(new FileInputStream(path.toFile()));
            //Olive.WorkflowDefinition wdTest2 = Olive.WorkflowDefinition.parseFrom(new FileInputStream("/Users/e24652/workflows/conditional_asr_and_mt.workflow.json"));
//            OliveWorkflowDefinition owd = new OliveWorkflowDefinition("/Users/e24652/workflows/conditional_asr_and_mt.workflow.json");
            System.out.println("Workflow results: "  );

            try {
                Map<String, JobResult>  jobResults = WorkflowUtils.extractWorkflowAnalysis(analysisResult.getRep());
                log.info("Workflow results: {}", jobResults);

                // do something with the results:
                if (!analysisResult.hasError()) {

                    for (String jobName : jobResults.keySet()) {
                        // print results
                        JobResult jr = jobResults.get(jobName);

                        System.out.println("Job: " + jobName);
                        for(String taskName:  jr.getTasks().keySet()){
                            List<TaskResult> trs = jr.getTasks().get(taskName);
                            for (TaskResult tr : trs) {
                                if (!tr.isError()) {
                                    System.out.println(String.format("%s = %s", taskName, tr.getTaskMessage().toString()));
                                } else {
                                    System.out.println(String.format("Task '%s' FAILED: %s ", taskName, tr.getErrMsg()));
                                }
                            }
                        }

                        System.out.println("");
                        System.out.println("");
                        System.out.println("Job Data Info ------------");
                        //                        System.out.println("");
                        for(DataResult dr : jr.getDataResults()){
                            System.out.println(String.format("Data ID: %s", dr.getDataName()));
                            System.out.println(String.format("%s", dr.getDataMessage().toString()));
                        }
                    }

                } else {
                    log.error("Workflow request failed: {}", analysisResult.getError());
                }
            } catch (Exception e) {
                log.error("Workflow request failed with error: ", e);
            }
        }

    }

    @Test
    /**
     */
    public void test_CONDITIONAL_ASR_MT_Analysis() throws Exception {

        // We use static (not abstract) plugin names here to make this workflow easier to read/modify
        String job_name = "Conditional ASR and MT Workflow";
        String file_prefix = "conditional_asr_plus_mt";

        // We do SAD, LID, then the router pimientos to choose the ASR and MT plugins to choose the correct ASR and MT domains
        // based on the output from the LID plugin
        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sad-dnn-v7.0.1").setDomain("multi-v1");
        Olive.GlobalScorerRequest.Builder lid_task = Olive.GlobalScorerRequest.newBuilder().setPlugin("lid-embedplda-v3.0.0").setDomain("multi-v1");

        // Pimientos
        Workflow.Plugin2PluginRequest.Builder pimento_asr = Workflow.Plugin2PluginRequest.newBuilder().setPlugin("map-routerGlobal").setDomain("lid_to_asr-dynapy-v3.0.0");
        Workflow.Plugin2PluginRequest.Builder pimento_txt = Workflow.Plugin2PluginRequest.newBuilder().setPlugin("map-routerGlobal").setDomain("lid_to_txt-neural-v3.0.0");
        // Pimiento to convert ASR region scores to a text string for ingest by MT
        Workflow.DataOutputTransformRequest.Builder mt_pit_task = Workflow.DataOutputTransformRequest.newBuilder().setPlugin("pim-extractTextRegion").setDomain("test-ldd-asr")
                .setDataOutput(Olive.InputDataType.TEXT);

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Workflow.DataHandlerProperty.Builder data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two tasks (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder base_job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("SAD, LID, WITH Conditional ASR and MT Processing")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // IMPORTANT - THIS JOB MUST FINISH FIRST BEFORE THE DYNAMIC JOB CAN BE START
                .addTransferResultLabels("audio")
                .addTransferResultLabels("CONDITIONAL_ASR")
                .addTransferResultLabels("CONDITIONAL_MT")
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
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("CONDITIONAL_ASR")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)
                        .setMessageData(pimento_asr.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes lid global scores, no data input
                        .setConsumerResultLabel("CONDITIONAL_ASR")
                        .setDescription("ASR Conditional Task Composer")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("LID").setPluginKeywordName("global_scores").build())
                        .setAllowFailure(true)
                        .setReturnResult(false))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("CONDITIONAL_MT")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)
                        .setMessageData(pimento_txt.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes lid global scores, no data input
                        .setConsumerResultLabel("CONDITIONAL_MT")
                        .setDescription("MT Conditional Task Composer")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("LID").setPluginKeywordName("global_scores").build())
                        .setAllowFailure(true)
                        .setReturnResult(false));


        String dynamic_asr_job_name = "Dynamic ASR";
        // Create a dynamic job, that will be completed based on the results of the previous job
        Workflow.DynamicPluginRequest.Builder dynamic_asr_task = Workflow.DynamicPluginRequest.newBuilder().setConditionalTaskName("CONDITIONAL_ASR");
//        Workflow.DynamicPluginRequest.Builder dynamic_mt_task = Workflow.DynamicPluginRequest.newBuilder()
//                .setConditionalTaskName("CONDITIONAL_MT");
        Workflow.JobDefinition.Builder dynamic_asr_job = Workflow.JobDefinition.newBuilder()
                .setJobName(dynamic_asr_job_name)
                .addDynamicJobName(job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("Dynamic selection of the ASR plugin/domain")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // needs to run before MT, since it creates the text data consumed by the MT task
                .addTransferResultLabels("ASR TEXT")  // The MT pimiento  will need ASR results
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_asr_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Dynamic ASR Task")
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("ASR TEXT")
                        .setTraitOutput(Olive.TraitType.DATA_OUTPUT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.DATA_OUTPUT_TRANSFORMER_REQUEST)
                        .setMessageData(mt_pit_task.build().toByteString())
                        .setConsumerDataLabel("scores")
                        .setConsumerResultLabel("ASR TEXT")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
                        .setReturnResult(false));
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("MT")
//                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
//                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
//                        .setMessageData(dynamic_mt_task.build().toByteString())  // We need this from the previous job
//                        .setConsumerDataLabel("text")
//                        .setConsumerResultLabel("MT")
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR TEXT").setPluginKeywordName("text").build())
//                        .setDescription("Dynamic MT Task")
//                        .setReturnResult(true));

        // And finally, create a dynamic MT job to handle the ASR output (needs the MT plugin/domain and ASR output)
        Workflow.DataHandlerProperty.Builder data_text_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.TEXT)
                .setPreprocessingRequired(false);
        Workflow.DynamicPluginRequest.Builder dynamic_mt_task = Workflow.DynamicPluginRequest.newBuilder()
                .setConditionalTaskName("CONDITIONAL_MT");

        Workflow.JobDefinition.Builder dynamic_text_job = Workflow.JobDefinition.newBuilder()
                .setJobName("Dynamic MT")
                .addDynamicJobName(job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .addDynamicJobName(dynamic_asr_job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_text_props)
                .setDescription("Dynamic selection of the MT plugin/domain using dynamic ASR input")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // has to run after the ASR task, since we need ASR result
//                .addTasks(Workflow.WorkflowTask.newBuilder()
//                        .setTask("ASR TEXT")
//                        .setTraitOutput(Olive.TraitType.DATA_OUTPUT_TRANSFORMER)
//                        .setMessageType(Olive.MessageType.DATA_OUTPUT_TRANSFORMER_REQUEST)
//                        .setMessageData(mt_pit_task.build().toByteString())
//                        .setConsumerDataLabel("scores")
//                        .setConsumerResultLabel("ASR TEXT")
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR").setPluginKeywordName("scores").build())
//                        .setReturnResult(false))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("MT")
                        .setTraitOutput(Olive.TraitType.TEXT_TRANSFORMER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_mt_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("text")
                        .setConsumerResultLabel("MT")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("ASR TEXT").setPluginKeywordName("text").build())
                        .setDescription("Dynamic MT Task")
                        .setReturnResult(true));

        Workflow.WorkflowOrderDefinition.Builder order = Workflow.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(base_job)
                .addJobDefinition(dynamic_asr_job)
                .addJobDefinition(dynamic_text_job)
                .setOrderName("ASR and MT Analysis Order");

        Workflow.WorkflowDefinition.Builder wfd = Workflow.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        Path path = FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow");
        FileOutputStream fos = new FileOutputStream(path.toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            } else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }


        if (true) {
            return;
        }

        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/dev/olive-data/Spanish.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if (analysisResult.hasError() || analysisResult.getRep().hasError()) {
            if (analysisResult.hasError()) {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getError());
            } else {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getRep().getError());
            }
        }
        else {

//            Olive.WorkflowDefinition wdTest = Olive.WorkflowDefinition.parseFrom(new FileInputStream(path.toFile()));
            //Olive.WorkflowDefinition wdTest2 = Olive.WorkflowDefinition.parseFrom(new FileInputStream("/Users/e24652/workflows/conditional_asr_and_mt.workflow.json"));
//            OliveWorkflowDefinition owd = new OliveWorkflowDefinition("/Users/e24652/workflows/conditional_asr_and_mt.workflow.json");
            System.out.println("Workflow results: "  );

            try {
                Map<String, JobResult>  jobResults = WorkflowUtils.extractWorkflowAnalysis(analysisResult.getRep());
                log.info("Workflow results: {}", jobResults);

                // do something with the results:
                if (!analysisResult.hasError()) {

                    for (String jobName : jobResults.keySet()) {
                        // print results
                        JobResult jr = jobResults.get(jobName);

                        System.out.println("Job: " + jobName);
                        for(String taskName:  jr.getTasks().keySet()){
                            List<TaskResult> trs = jr.getTasks().get(taskName);
                            for (TaskResult tr : trs) {
                                if (!tr.isError()) {
                                    System.out.println(String.format("%s = %s", taskName, tr.getTaskMessage().toString()));
                                } else {
                                    System.out.println(String.format("Task '%s' FAILED: %s ", taskName, tr.getErrMsg()));
                                }
                            }
                        }

                        System.out.println("");
                        System.out.println("");
                        System.out.println("Job Data Info ------------");
                        //                        System.out.println("");
                        for(DataResult dr : jr.getDataResults()){
                            System.out.println(String.format("Data ID: %s", dr.getDataName()));
                            System.out.println(String.format("%s", dr.getDataMessage().toString()));
                        }
                    }

                } else {
                    log.error("Workflow request failed: {}", analysisResult.getError());
                }
            } catch (Exception e) {
                log.error("Workflow request failed with error: ", e);
            }
        }

    }

    @Test
    /**
     */
    public void testCreating_CONDITIONAL_Stereo_ASR_Analysis() throws Exception {

        // Same as testCreating_CONDITIONAL_Stere_ASR_Analysis but we try doing the dynamic job with stereo data

        String job_name = "Conditional STEREO ASR Workflow";
        String file_prefix = "conditional_stereo_static_asr";

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

        Workflow.DataHandlerProperty.Builder asr_data_props = Workflow.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.SPLIT);

        // Create a single job, with two tasks (SAD and LID), both results should be returned to the client
        Workflow.JobDefinition.Builder job = Workflow.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("SAD, LID, WITH Conditional ASR Processing")
                .setProcessingType(Workflow.WorkflowJobType.SEQUENTIAL)  // IMPORTANT - THIS JOB MUST FINISH FIRST BEFORE THE DYNAMIC JOB CAN BE START
//                .addTransferResultLabels("audio")                    // We don't want to transfer audio from this job to the next since we want ASR to have the option of using stereo data
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
                .setDataProperties(asr_data_props)
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

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Workflow.WorkflowActualizeRequest.Builder wr = Workflow.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Workflow.WorkflowActualizeRequest, Workflow.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            } else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }


        // Now create the analysis message
        Workflow.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Workflow.WorkflowAnalysisRequest, Workflow.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
        if (analysisResult.hasError() || analysisResult.getRep().hasError()) {
            if (analysisResult.hasError()) {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getError());
            } else {
                Assert.fail("Workflow Analysis request message failed: " + analysisResult.getRep().getError());
            }
        }

    }

    @Test
    /**
     */
    public void testCreating_CONDITIONAL_LDD_DIA_ASR_Analysis() throws Exception {

        // We use static (not abstract) plugin names here to make this workflow easier to read/modify
        String job_name = "Conditional ASR Workflow";
        String file_prefix = "conditional_ldd_dia_asr";

        // Create a workflow definition that does SAD (frames), LID, CONDITIONAL PIMENTO, then in a new job it does
        // ASR using plugin/domains from the pimento.

        Olive.RegionScorerRequest.Builder sad_task = Olive.RegionScorerRequest.newBuilder().setPlugin("sad-dnn-v7.0.1").setDomain("multi-v1");
//        Olive.RegionScorerRequest.Builder ldd_task = Olive.RegionScorerRequest.newBuilder().setPlugin("ldd-sbcEmbed-v1.0.1").setDomain("multi-v1");
        Olive.RegionScorerRequest.Builder ldd_task = Olive.RegionScorerRequest.newBuilder().setPlugin("ldd-embedplda-v1.0.1").setDomain("multi-v1");
        Olive.RegionScorerRequest.Builder dia_task = Olive.RegionScorerRequest.newBuilder().setPlugin("dia-hybrid-v2.0.1").setDomain("multi-v1");

        // Pimento/plugin that consumes global scores from LID and picks the best ASR plugin/domain
        // If using the latest LID plugin we use language names, not codes
        Workflow.Plugin2PluginRequest.Builder pimento_task = Workflow.Plugin2PluginRequest.newBuilder().setPlugin("map-routerRegion-v1.0.0").setDomain("ldd_to_asr-dynapy-v3.0.0");

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
                .setDescription("SAD, LDD, DIA, WITH Conditional ASR Processing")
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
                        .setTask("LDD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(ldd_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LDD")
                        .setDescription("Language Detection")
                        .setAllowFailure(false)
//                        If you want to pass in SAD regions:
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD_FRAMES").setPluginKeywordName("speech_frames").build())
                        .setReturnResult(true))
                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("DIA")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(dia_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("DIA")
                        .setDescription("Speaker Diarization")
                        .setAllowFailure(false)
//                        If you want to pass in SAD regions:
//                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("SAD_FRAMES").setPluginKeywordName("speech_frames").build())
                        .setReturnResult(true))

                .addTasks(Workflow.WorkflowTask.newBuilder()
                        .setTask("DYNAMIC_ASR")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)
                        .setMessageData(pimento_task.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes ldd scores, no data (audio) input
                        .setConsumerResultLabel("CONDITIONAL_ASR")
                        .setDescription("ASR Conditional Task Composer")
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("LDD").setPluginKeywordName("global_scores").build())
                        .addOptionMappings(Workflow.OptionMap.newBuilder().setWorkflowKeywordName("DIA").setPluginKeywordName("dia_scores").build())
                        .setAllowFailure(false)
                        .setReturnResult(false));


        // Create a dynamic job, that will be completed based on the results of the previous job
        Workflow.DynamicPluginRequest.Builder dynamic_asr_task = Workflow.DynamicPluginRequest.newBuilder().setConditionalTaskName("CONDITIONAL_ASR");
        Workflow.JobDefinition.Builder dynamic_job = Workflow.JobDefinition.newBuilder()
                .setJobName("Dynamic ASR")
                .addDynamicJobName(job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_props)
                .setDescription("Test dynamic selection of the ASR plugin/domain using LDD and DIA results")
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

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);


    }
}
