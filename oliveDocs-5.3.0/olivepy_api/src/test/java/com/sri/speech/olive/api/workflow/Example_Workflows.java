package com.sri.speech.olive.api.workflow;

import com.google.protobuf.Message;
import com.sri.speech.olive.api.Olive;
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


    private Olive.WorkflowAnalysisRequest createAnalysisMessage(Olive.WorkflowDefinition workflowDef, String audioFilename) throws IOException, UnsupportedAudioFileException {
        // We assume our workflow only has one order, which has one job
//        Olive.WorkflowOrderDefinition orderDefinition = workflowDef.getOrder(0);

        // Create the data message for our filename
        Olive.Audio.Builder audio = ClientUtils.createAudioFromFile(audioFilename, 0, ClientUtils.AudioTransferType.SEND_AS_PATH, null);
        Olive.WorkflowDataRequest.Builder dataRequest = Olive.WorkflowDataRequest.newBuilder()
                .setDataId(Paths.get(audioFilename).getFileName().toString())
                .setDataType(Olive.InputDataType.AUDIO)
                .setWorkflowData(audio.build().toByteString());

        // Option example
//        Olive.OptionValue.Builder ov = Olive.OptionValue.newBuilder().setName("select_best").setValue("False");

        Olive.WorkflowAnalysisRequest.Builder msg = Olive.WorkflowAnalysisRequest.newBuilder()
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
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder job = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setReturnResult(true));

        Olive.WorkflowOrderDefinition.Builder order = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath("/tmp/" + file_prefix+ "example.workflow").toFile());
        wfd.build().writeTo(fos);

    }

    @Test
    public void testLDD() throws Exception {

        // For Hommin
        String job_name = "LDD Only Workflow";
        String file_prefix = "ldd";

        Olive.RegionScorerRequest.Builder ldd_task = Olive.RegionScorerRequest.newBuilder().setPlugin("ldd-embedplda-v1.0.1").setDomain("multi-v1");


        // Use one mono (if stereo) audio input, having an 8K sample rate
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder job = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("LDD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(ldd_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LDD")
                        .setReturnResult(true));

        Olive.WorkflowOrderDefinition.Builder order = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
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
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder job = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setReturnResult(true));

        Olive.WorkflowOrderDefinition.Builder order = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
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
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder job = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("SAD and LID Processing")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("Speech Regions")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setReturnResult(true));

        Olive.WorkflowOrderDefinition.Builder order = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "example.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Olive.WorkflowActualizeRequest.Builder wr = Olive.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Olive.WorkflowActualizeRequest, Olive.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            Assert.fail("Workflow request message failed: " + result.getError());
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
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);


        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder job = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("FDI Processing")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("FDI")
                        .setTraitOutput(Olive.TraitType.BOUNDING_BOX_SCORER)
                        .setMessageType(Olive.MessageType.BOUNDING_BOX_REQUEST)
                        .setMessageData(rec_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FDI")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setAllowFailure(false)
                        .setReturnResult(true));

        // We DO NOT support enrollment

        Olive.WorkflowOrderDefinition.Builder order = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Olive.WorkflowActualizeRequest.Builder wr = Olive.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Olive.WorkflowActualizeRequest, Olive.WorkflowActualizeResult> result = server.synchRequest(wr.build());
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
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);


        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder job = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("FRI Processing")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("FRI")
                        .setTraitOutput(Olive.TraitType.BOUNDING_BOX_SCORER)
                        .setMessageType(Olive.MessageType.BOUNDING_BOX_REQUEST)
                        .setMessageData(rec_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRI")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setAllowFailure(false)
                        .setReturnResult(true));

        // We only support image enrollment
        Olive.DataHandlerProperty.Builder enroll_data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);
        Olive.JobDefinition.Builder enrollJob = Olive.JobDefinition.newBuilder()
                .setJobName("FRI Enrollment")
                .setDataProperties(enroll_data_props)
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("FRI")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRI Enroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Olive.DataHandlerProperty.Builder unenroll_data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(0)
                .setMaxNumberInputs(0)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);
        Olive.JobDefinition.Builder unenrollJob = Olive.JobDefinition.newBuilder()
                .setJobName("FRI Unenrollment")
                .setDataProperties(unenroll_data_props)
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("FRI")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(enroll_task.build().toByteString())
                        .setConsumerDataLabel("")
                        .setConsumerResultLabel("FRI Unenroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Olive.WorkflowOrderDefinition.Builder order = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");
        Olive.WorkflowOrderDefinition.Builder enrollOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollJob)
                .setOrderName("Enrollment Order");
        Olive.WorkflowOrderDefinition.Builder unenrollOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(unenrollJob)
                .setOrderName("Unenrollment Order");


        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Olive.WorkflowActualizeRequest.Builder wr = Olive.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Olive.WorkflowActualizeRequest, Olive.WorkflowActualizeResult> result = server.synchRequest(wr.build());
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

        Olive.Plugin2PluginRequest.Builder pimento_task = Olive.Plugin2PluginRequest.newBuilder().setPlugin("data-chooser").setDomain("lid-names-asr");

        String plugin_name = "img-mock";
        String domain_name = "face-v1";
//        Olive.RegionScorerRequest.Builder rec_task = Olive.RegionScorerRequest.newBuilder().setPlugin(plugin_name).setDomain(domain_name);
        Olive.ClassModificationRequest.Builder enroll_task = Olive.ClassModificationRequest.newBuilder()
                .setPlugin(plugin_name)
                .setDomain(domain_name)
                .setClassId("none");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        // TODO HOW TO HANDLE THIS IF WE DONT KNOW THE DATA TYPE TO USE..
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setConsumerDataLabel("data")
                .setType(Olive.InputDataType.BINARY)
                .setPreprocessingRequired(true);


        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder job = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("Conditional Face Recognition")
                .setProcessingType(Olive.WorkflowJobType.SEQUENTIAL)
                // Should be one of these...
//                .addTransferResultLabels("video")
//                .addTransferResultLabels("image")
                .addTransferResultLabels("data")
                .addTransferResultLabels("DYNAMIC_FR")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("DYNAMIC_FR")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)
                        .setMessageData(pimento_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("data").setPluginKeywordName("global_scores").build())
                        .setAllowFailure(false)
                        .setReturnResult(true));


        // Create a dynamic job, that will be completed based on the results of the previous job
        Olive.DynamicPluginRequest.Builder dynamic_fr_task = Olive.DynamicPluginRequest.newBuilder().setConditionalTaskName("DYNAMIC_FR");
        Olive.JobDefinition.Builder dynamic_job = Olive.JobDefinition.newBuilder()
                .setJobName("Dynamic Face Recognition")
                .addDynamicJobName(job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_props)
                .setDescription("Test dynamic selection of the Face Recognition plugin/domain")
                .setProcessingType(Olive.WorkflowJobType.SEQUENTIAL)  // should not matter in this case --
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("FR")
                        .setTraitOutput(Olive.TraitType.BOUNDING_BOX_SCORER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_fr_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FR")
                        .setDescription("Dynamic FR Task")
                        .setReturnResult(true));

        // We only support image enrollment
        Olive.DataHandlerProperty.Builder enroll_data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);
        Olive.JobDefinition.Builder enrollJob = Olive.JobDefinition.newBuilder()
                .setJobName("FRV Enrollment")
                .setDataProperties(enroll_data_props)
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("FRV")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV Enroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Olive.DataHandlerProperty.Builder unenroll_data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(0)
                .setMaxNumberInputs(0)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);
        Olive.JobDefinition.Builder unenrollJob = Olive.JobDefinition.newBuilder()
                .setJobName("FRV Unenrollment")
                .setDataProperties(unenroll_data_props)
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("FRV")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV Unenroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Olive.WorkflowOrderDefinition.Builder order = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(dynamic_job)
                .setOrderName("Analysis Order");
        Olive.WorkflowOrderDefinition.Builder enrollOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollJob)
                .setOrderName("Enrollment Order");
        Olive.WorkflowOrderDefinition.Builder unenrollOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(unenrollJob)
                .setOrderName("Unenrollment Order");


        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Olive.WorkflowActualizeRequest.Builder wr = Olive.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Olive.WorkflowActualizeRequest, Olive.WorkflowActualizeResult> result = server.synchRequest(wr.build());
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
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.VIDEO)
                .setPreprocessingRequired(true);


        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder job = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("FRV Processing")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("FRV")
                        .setTraitOutput(Olive.TraitType.BOUNDING_BOX_SCORER)
                        .setMessageType(Olive.MessageType.BOUNDING_BOX_REQUEST)
                        .setMessageData(rec_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("video").setPluginKeywordName("data").build())
                        .setAllowFailure(false)
                        .setReturnResult(true));

        // We only support image enrollment
        Olive.DataHandlerProperty.Builder enroll_data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);
        Olive.JobDefinition.Builder enrollJob = Olive.JobDefinition.newBuilder()
                .setJobName("FRV Enrollment")
                .setDataProperties(enroll_data_props)
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("FRV")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV Enroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Olive.DataHandlerProperty.Builder unenroll_data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(0)
                .setMaxNumberInputs(0)
                .setType(Olive.InputDataType.IMAGE)
                .setPreprocessingRequired(true);
        Olive.JobDefinition.Builder unenrollJob = Olive.JobDefinition.newBuilder()
                .setJobName("FRV Unenrollment")
                .setDataProperties(unenroll_data_props)
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("FRV")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(enroll_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FRV Unenroll")
                        .setAllowFailure(false)
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("image").setPluginKeywordName("data").build())
                        .setReturnResult(true));

        Olive.WorkflowOrderDefinition.Builder order = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");
        Olive.WorkflowOrderDefinition.Builder enrollOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollJob)
                .setOrderName("Enrollment Order");
        Olive.WorkflowOrderDefinition.Builder unenrollOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(unenrollJob)
                .setOrderName("Unenrollment Order");


        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Olive.WorkflowActualizeRequest.Builder wr = Olive.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Olive.WorkflowActualizeRequest, Olive.WorkflowActualizeResult> result = server.synchRequest(wr.build());
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
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.VIDEO)
                .setPreprocessingRequired(true);


        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder job = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("FDV Processing")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("FDV")
                        .setTraitOutput(Olive.TraitType.BOUNDING_BOX_SCORER)
                        .setMessageType(Olive.MessageType.BOUNDING_BOX_REQUEST)
                        .setMessageData(rec_task.build().toByteString())
                        .setConsumerDataLabel("data")
                        .setConsumerResultLabel("FDV")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("video").setPluginKeywordName("data").build())
                        .setAllowFailure(false)
                        .setReturnResult(true));

        // We DO NOT support enrollment for FDV
        Olive.WorkflowOrderDefinition.Builder order = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Olive.WorkflowActualizeRequest.Builder wr = Olive.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Olive.WorkflowActualizeRequest, Olive.WorkflowActualizeResult> result = server.synchRequest(wr.build());
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
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder job = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("SAD and LID Processing")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setAllowFailure(false)
                        .setReturnResult(true))
                .setDescription("Speech Regions")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setReturnResult(true))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setReturnResult(true));

        Olive.WorkflowOrderDefinition.Builder order = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .setOrderName("Analysis Order");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_example.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Olive.WorkflowActualizeRequest.Builder wr = Olive.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Olive.WorkflowActualizeRequest, Olive.WorkflowActualizeResult> result = server.synchRequest(wr.build());
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
        Olive.DataHandlerProperty.Builder sad_data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);
        // For SID process each channel, having an 8K sample rate
        Olive.DataHandlerProperty.Builder sid_data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.SPLIT);

        // Create a single job, with 3 tasks (SAD and LID and SID), all results should be returned to the client


        Olive.JobDefinition.Builder sadAnalysisJob = Olive.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(sad_data_props)
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setDescription("Speech Regions")
                        .setReturnResult(true))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setReturnResult(true));

        Olive.JobDefinition.Builder sidAnalysisJob = Olive.JobDefinition.newBuilder()
                .setJobName("Stereo SID")
//                .setJobName(analysisjob_name)
                .setDataProperties(sid_data_props)
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
                        .setDescription("Stereo SID")
                        .setReturnResult(true));

        // Now create the orders
        Olive.WorkflowOrderDefinition.Builder analysisOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(sadAnalysisJob)
                .addJobDefinition(sidAnalysisJob)
                .setOrderName("Analysis Order");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_example.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
/*        Olive.WorkflowActualizeRequest.Builder wr = Olive.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Olive.WorkflowActualizeRequest, Olive.WorkflowActualizeResult> result = server.synchRequest(wr.build());
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
        Olive.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Olive.WorkflowAnalysisRequest, Olive.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
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
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two task (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder analysisJob = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setDescription("Test Workflow Job with multiple types of scorers")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("AudioQuality")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)

                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(quality_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("AudioQuality")
                        .setReturnResult(false)
                        .setDescription("Audio Quality"))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("QUA")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(filter_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("QUA")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("AudioQuality").setPluginKeywordName("QUALITY").build())
                        .setReturnResult(false)
                        .setDescription("Audio Quality Filter"))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("VAL")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(validate_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("VAL")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
                        .setReturnResult(false)
                        .setAllowFailure(false)
                        .setDescription("Audio Quality Validation"))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(sid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SID")
//                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
                        .setReturnResult(true)
                        .setDescription("Speaker Identification"))

//                .addTasks(Olive.WorkflowTask.newBuilder()
//                        .setTask("SID")
//                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
//                        .setMessageType(Olive.MessageType.ABSTRACT_WORKFLOW_TASK)
//                        .setMessageData(sid_task.build().toByteString())
//                        .setConsumerDataLabel("audio").setConsumerResultLabel("SID")
//                        .setReturnResult(true)
//                        .setDescription("SID as a Global Scorer"))
                ;

        Olive.JobDefinition.Builder enrollmentJob = Olive.JobDefinition.newBuilder()
                .setJobName("SID Enrollment with Audio Quality Check")
                .setDataProperties(data_props)
                .setDescription("Provides SID enrollment if audio quality is met")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("AudioQuality")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(quality_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("AudioQuality")
                        .setReturnResult(false)
                        .setDescription("Audio Quality"))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("QUA")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(filter_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("QUA")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("AudioQuality").setPluginKeywordName("QUALITY").build())
                        .setReturnResult(false)
                        .setDescription("Audio Quality Filter"))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("VAL")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(validate_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("VAL")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
                        .setReturnResult(false)
                        .setAllowFailure(false)
                        .setDescription("Audio Quality Validation"))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SID_Enroll")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(sid_enroll_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SID_Enroll")
                        .setAllowFailure(false)
//                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("QUA").setPluginKeywordName("QUALITY_FILTER").build())
                        .setReturnResult(true)
                        .setDescription("Speaker Identification"));

        Olive.JobDefinition.Builder unenrollmentJob = Olive.JobDefinition.newBuilder()
                .setJobName("Unenrollment for SID")
                .setDataProperties(data_props)
                .setDescription("SID Unenrollment job ")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(sid_unenroll_task.build().toByteString())
                        .setConsumerDataLabel("")//  Does not use data...
                        .setConsumerResultLabel("SID_Unenroll")
                        .setAllowFailure(false)
                        .setReturnResult(true));

        Olive.WorkflowOrderDefinition.Builder analysisOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("Analysis Order");

        Olive.WorkflowOrderDefinition.Builder enrollOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(enrollmentJob)
                .setOrderName("Enrollment Order");

        Olive.WorkflowOrderDefinition.Builder unenrollOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(unenrollmentJob)
                .setOrderName("Unenrollment Order");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setDescription("Audio Quality Workflow Definition")
                .setVersion("1.0")
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_abstract.workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def
        Olive.WorkflowActualizeRequest.Builder wr = Olive.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Olive.WorkflowActualizeRequest, Olive.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            Assert.fail("Workflow request message failed: " + result.getError());
        }

    }


    private void checkError(Server.Result<Olive.WorkflowActualizeRequest, Olive.WorkflowActualizeResult> result) {
        String err = result.getError();
        if (null == err) {
            err = result.getRep().getError();
        }
        if (null == err) {
            Assert.fail("Workflow request message failed: " + err);
        }
    }

    private void checkAnalysisError(Server.Result<Olive.WorkflowAnalysisRequest, Olive.WorkflowAnalysisResult> result) {
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
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with 3 tasks (SAD and LID and SID), all results should be returned to the client
        Olive.JobDefinition.Builder analysisJob = Olive.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(data_props)
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(asr_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setReturnResult(true));

        // Now create the orders
        Olive.WorkflowOrderDefinition.Builder analysisOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("ASR Analysis");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
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
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with 3 tasks (SAD and LID and SID), all results should be returned to the client
        Olive.JobDefinition.Builder analysisJob = Olive.JobDefinition.newBuilder()
                .setJobName(analysisjob_name)
                .setDataProperties(data_props)
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD")
                        .setDescription("Speech Regions")
                        .setReturnResult(true))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setReturnResult(true))
                .addTasks(Olive.WorkflowTask.newBuilder()
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
        Olive.JobDefinition.Builder sidEnrollmentJob = Olive.JobDefinition.newBuilder()
                .setJobName("SID Enrollment")
                .setDataProperties(data_props)
                .setDescription("SID Enrollment")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_MODIFICATION_REQUEST)
                        .setMessageData(sid_enroll_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SID Enrollment")
                        .setAllowFailure(false)
                        .setReturnResult(true));
        Olive.JobDefinition.Builder lidEnrollmentJob = Olive.JobDefinition.newBuilder()
                .setJobName("LID Enrollment")
                .setDataProperties(data_props)
                .setDescription("LID Enrollment")
                .addTasks(Olive.WorkflowTask.newBuilder()
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
        Olive.JobDefinition.Builder lidUnenrollmentJob = Olive.JobDefinition.newBuilder()
                .setJobName("LID Unenrollment")
                .setDataProperties(data_props)
                .setDescription("LID UNenrollment job ")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(lid_unenroll_task.build().toByteString())
                        .setConsumerDataLabel("")  //  Does not use data...
                        .setConsumerResultLabel("LID Unenroll")
                        .setAllowFailure(false)
                        .setReturnResult(true));

        Olive.JobDefinition.Builder sidUnenrollmentJob = Olive.JobDefinition.newBuilder()
                .setJobName("SID Unenrollment")
                .setDataProperties(data_props)
                .setDescription("SID UNenrollment job ")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SID")
                        .setTraitOutput(Olive.TraitType.CLASS_MODIFIER)
                        .setMessageType(Olive.MessageType.CLASS_REMOVAL_REQUEST)
                        .setMessageData(sid_unenroll_task.build().toByteString())
                        .setConsumerDataLabel("")//  Does not use data...
                        .setConsumerResultLabel("SID Unenroll")
                        .setAllowFailure(false)
                        .setReturnResult(true));

        // Now create the orders
        Olive.WorkflowOrderDefinition.Builder analysisOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(analysisJob)
                .setOrderName("Analysis Order");

        Olive.WorkflowOrderDefinition.Builder enrollOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ENROLLMENT_TYPE)
                .addJobDefinition(sidEnrollmentJob)
                .addJobDefinition(lidEnrollmentJob)
                .setOrderName("Enrollment Order");

        Olive.WorkflowOrderDefinition.Builder unenrollOrder = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_UNENROLLMENT_TYPE)
                .addJobDefinition(sidUnenrollmentJob)
                .addJobDefinition(lidUnenrollmentJob)
                .setOrderName("Unenrollment Order");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(analysisOrder)
                .addOrder(enrollOrder)
                .addOrder(unenrollOrder)
                .setCreated(Olive.DateTime.newBuilder().setDay(29).setMonth(6).setYear(2021).setHour(8).setMin(0).setSec(0).build())
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + "_example.workflow").toFile());
        wfd.build().writeTo(fos);


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
            Olive.Plugin2PluginRequest.Builder pimento_task = Olive.Plugin2PluginRequest.newBuilder().setPlugin("map-routerGlobal").setDomain("lid_to_asr-dynapy-v3.0.0");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two tasks (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder job = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("SAD, LID, WITH Conditional ASR Processing")
                .setProcessingType(Olive.WorkflowJobType.SEQUENTIAL)  // IMPORTANT - THIS JOB MUST FINISH FIRST BEFORE THE DYNAMIC JOB CAN BE START
                .addTransferResultLabels("audio")
                .addTransferResultLabels("CONDITIONAL_ASR")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD_REGIONS")
                        .setDescription("Speech Activity Detection")
                        .setReturnResult(true))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setAllowFailure(false)
//                        If you want to pass in SAD regions:
//                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("SAD_FRAMES").setPluginKeywordName("speech_frames").build())
                        .setReturnResult(true))

                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("DYNAMIC_ASR")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)  // TODO THIS IS THE WRONG TYPE...
                        .setMessageData(pimento_task.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes lid global scores, no data input
                        .setConsumerResultLabel("CONDITIONAL_ASR")
                        .setDescription("ASR Conditional Task Composer")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("LID").setPluginKeywordName("global_scores").build())
                        .setAllowFailure(false)
                        .setReturnResult(false));


        // Create a dynamic job, that will be completed based on the results of the previous job
        Olive.DynamicPluginRequest.Builder dynamic_asr_task = Olive.DynamicPluginRequest.newBuilder().setConditionalTaskName("CONDITIONAL_ASR");
        Olive.JobDefinition.Builder dynamic_job = Olive.JobDefinition.newBuilder()
                .setJobName("Dynamic ASR")
                .addDynamicJobName(job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_props)
                .setDescription("Test dynamic selection of the ASR plugin/domain")
                .setProcessingType(Olive.WorkflowJobType.SEQUENTIAL)  // should not matter in this case --
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_asr_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Dynamic ASR Task")
                        .setReturnResult(true));

        Olive.WorkflowOrderDefinition.Builder order = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(dynamic_job)
                .setOrderName("Analysis Order");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Olive.WorkflowActualizeRequest.Builder wr = Olive.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Olive.WorkflowActualizeRequest, Olive.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            } else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }


        // Now create the analysis message
        Olive.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Olive.WorkflowAnalysisRequest, Olive.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
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
        Olive.Plugin2PluginRequest.Builder pimento_task = Olive.Plugin2PluginRequest.newBuilder().setPlugin("chooser").setDomain("lid-names-asr");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        Olive.DataHandlerProperty.Builder asr_data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.SPLIT);

        // Create a single job, with two tasks (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder job = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("SAD, LID, WITH Conditional ASR Processing")
                .setProcessingType(Olive.WorkflowJobType.SEQUENTIAL)  // IMPORTANT - THIS JOB MUST FINISH FIRST BEFORE THE DYNAMIC JOB CAN BE START
//                .addTransferResultLabels("audio")                    // We don't want to transfer audio from this job to the next since we want ASR to have the option of using stereo data
                .addTransferResultLabels("CONDITIONAL_ASR")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD_REGIONS")
                        .setDescription("Speech Activity Detection")
                        .setReturnResult(true))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("LID")
                        .setTraitOutput(Olive.TraitType.GLOBAL_SCORER)
                        .setMessageType(Olive.MessageType.GLOBAL_SCORER_REQUEST)
                        .setMessageData(lid_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LID")
                        .setDescription("Language ID")
                        .setAllowFailure(false)
//                        If you want to pass in SAD regions:
//                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("SAD_FRAMES").setPluginKeywordName("speech_frames").build())
                        .setReturnResult(true))

                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("DYNAMIC_ASR")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)  // TODO THIS IS THE WRONG TYPE...
                        .setMessageData(pimento_task.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes lid global scores, no data input
                        .setConsumerResultLabel("CONDITIONAL_ASR")
                        .setDescription("ASR Conditional Task Composer")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("LID").setPluginKeywordName("global_scores").build())
                        .setAllowFailure(false)
                        .setReturnResult(false));


        // Create a dynamic job, that will be completed based on the results of the previous job
        Olive.DynamicPluginRequest.Builder dynamic_asr_task = Olive.DynamicPluginRequest.newBuilder().setConditionalTaskName("CONDITIONAL_ASR");
        Olive.JobDefinition.Builder dynamic_job = Olive.JobDefinition.newBuilder()
                .setJobName("Dynamic ASR")
                .addDynamicJobName(job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(asr_data_props)
                .setDescription("Test dynamic selection of the ASR plugin/domain")
                .setProcessingType(Olive.WorkflowJobType.SEQUENTIAL)  // should not matter in this case --
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_asr_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Dynamic ASR Task")
                        .setReturnResult(true));

        Olive.WorkflowOrderDefinition.Builder order = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(dynamic_job)
                .setOrderName("Analysis Order");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);

        // Have the server actualize this workflow def - to make sure it worked
        Olive.WorkflowActualizeRequest.Builder wr = Olive.WorkflowActualizeRequest.newBuilder().setWorkflowDefinition(wfd);
        Server.Result<Olive.WorkflowActualizeRequest, Olive.WorkflowActualizeResult> result = server.synchRequest(wr.build());
        if (result.hasError() || result.getRep().hasError()) {
            if (result.hasError()) {
                Assert.fail("Workflow request message failed: " + result.getError());
            } else {
                Assert.fail("Workflow request message failed: " + result.getRep().getError());
            }
        }


        // Now create the analysis message
        Olive.WorkflowAnalysisRequest analysisRequest = createAnalysisMessage(result.getRep().getWorkflow(), "/Users/e24652/audio/sad_smoke.wav");
        Server.Result<Olive.WorkflowAnalysisRequest, Olive.WorkflowAnalysisResult> analysisResult = server.synchRequest(analysisRequest);
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
        Olive.Plugin2PluginRequest.Builder pimento_task = Olive.Plugin2PluginRequest.newBuilder().setPlugin("map-routerRegion-v1.0.0").setDomain("ldd_to_asr-dynapy-v3.0.0");

        // Use one mono (if stereo) audio input, having an 8K sample rate
        Olive.DataHandlerProperty.Builder data_props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        // Create a single job, with two tasks (SAD and LID), both results should be returned to the client
        Olive.JobDefinition.Builder job = Olive.JobDefinition.newBuilder()
                .setJobName(job_name)
                .setDataProperties(data_props)
                .setConditionalJobOutput(true)
                .setDescription("SAD, LDD, DIA, WITH Conditional ASR Processing")
                .setProcessingType(Olive.WorkflowJobType.SEQUENTIAL)  // IMPORTANT - THIS JOB MUST FINISH FIRST BEFORE THE DYNAMIC JOB CAN BE START
                .addTransferResultLabels("audio")
                .addTransferResultLabels("CONDITIONAL_ASR")
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("SAD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(sad_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("SAD_REGIONS")
                        .setDescription("Speech Activity Detection")
                        .setReturnResult(true))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("LDD")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(ldd_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("LDD")
                        .setDescription("Language Detection")
                        .setAllowFailure(false)
//                        If you want to pass in SAD regions:
//                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("SAD_FRAMES").setPluginKeywordName("speech_frames").build())
                        .setReturnResult(true))
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("DIA")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.REGION_SCORER_REQUEST)
                        .setMessageData(dia_task.build().toByteString())
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("DIA")
                        .setDescription("Speaker Diarization")
                        .setAllowFailure(false)
//                        If you want to pass in SAD regions:
//                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("SAD_FRAMES").setPluginKeywordName("speech_frames").build())
                        .setReturnResult(true))

                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("DYNAMIC_ASR")
                        .setTraitOutput(Olive.TraitType.PLUGIN_2_PLUGIN)
                        .setMessageType(Olive.MessageType.PLUGIN_2_PLUGIN_REQUEST)
                        .setMessageData(pimento_task.build().toByteString())
                        .setConsumerDataLabel("global_scores")  //  consumes ldd scores, no data (audio) input
                        .setConsumerResultLabel("CONDITIONAL_ASR")
                        .setDescription("ASR Conditional Task Composer")
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("LDD").setPluginKeywordName("global_scores").build())
                        .addOptionMappings(Olive.OptionMap.newBuilder().setWorkflowKeywordName("DIA").setPluginKeywordName("dia_scores").build())
                        .setAllowFailure(false)
                        .setReturnResult(false));


        // Create a dynamic job, that will be completed based on the results of the previous job
        Olive.DynamicPluginRequest.Builder dynamic_asr_task = Olive.DynamicPluginRequest.newBuilder().setConditionalTaskName("CONDITIONAL_ASR");
        Olive.JobDefinition.Builder dynamic_job = Olive.JobDefinition.newBuilder()
                .setJobName("Dynamic ASR")
                .addDynamicJobName(job_name)  // This maps this job to the conditional job that determined the ASR plugin/domain
                .setDataProperties(data_props)
                .setDescription("Test dynamic selection of the ASR plugin/domain using LDD and DIA results")
                .setProcessingType(Olive.WorkflowJobType.SEQUENTIAL)  // should not matter in this case --
                .addTasks(Olive.WorkflowTask.newBuilder()
                        .setTask("ASR")
                        .setTraitOutput(Olive.TraitType.REGION_SCORER)
                        .setMessageType(Olive.MessageType.DYNAMIC_PLUGIN_REQUEST)
                        .setMessageData(dynamic_asr_task.build().toByteString())  // We need this from the previous job
                        .setConsumerDataLabel("audio")
                        .setConsumerResultLabel("ASR")
                        .setDescription("Dynamic ASR Task")
                        .setReturnResult(true));

        Olive.WorkflowOrderDefinition.Builder order = Olive.WorkflowOrderDefinition.newBuilder()
                .setWorkflowType(Olive.WorkflowType.WORKFLOW_ANALYSIS_TYPE)
                .addJobDefinition(job)
                .addJobDefinition(dynamic_job)
                .setOrderName("Analysis Order");

        Olive.WorkflowDefinition.Builder wfd = Olive.WorkflowDefinition.newBuilder()
                .addOrder(order)
                .setActualized(false);

        FileOutputStream fos = new FileOutputStream(FileSystems.getDefault().getPath("/tmp/" + file_prefix + ".workflow").toFile());
        wfd.build().writeTo(fos);


    }
}
