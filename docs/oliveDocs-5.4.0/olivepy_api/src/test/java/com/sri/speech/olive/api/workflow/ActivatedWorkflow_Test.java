package com.sri.speech.olive.api.workflow;


import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Server;
import com.sri.speech.olive.api.utils.ClientUtils;
import com.sri.speech.olive.api.utils.CommonUtils;
import com.sri.speech.olive.api.workflow.wrapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * These tests need the test/mock server plugins
 */
public class ActivatedWorkflow_Test {

    private static Logger log = LoggerFactory.getLogger(ActivatedWorkflow_Test.class);

    public String AUDIO_TEST_FILE = "${TEST_DATA_ROOT}/testSuite/general/fsh_kws.wav";
    public String  TEST_SAD_LID_WORKFLOW_DEF = "${TEST_DATA_ROOT}/testSuite/workflows/sad-lid-sid_abstract.workflow.txt";
    public String  TEST_SPEECH_ANALYSIS_WORKFLOW_DEF = "${TEST_DATA_ROOT}/testSuite/workflows/speech_analysis_abstract.workflow";

    public static final int scenicPort      = 5588;
    public static final String scenicHost   = "localhost";
    private static final int TIMEOUT        = 10000;  // 10 seconds is enough?

    private Server server;

    public ActivatedWorkflow_Test(){
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


    @BeforeTest
    public void init(){

        Map<String, String> envMap = System.getenv();
        if(!envMap.containsKey("TEST_DATA_ROOT")){
            throw new SkipException("Skipping test 'TEST_DATA_ROOT not set");
        }

        AUDIO_TEST_FILE = CommonUtils.expandEnvVars(AUDIO_TEST_FILE, envMap);
        TEST_SAD_LID_WORKFLOW_DEF = CommonUtils.expandEnvVars(TEST_SAD_LID_WORKFLOW_DEF, envMap);
        TEST_SPEECH_ANALYSIS_WORKFLOW_DEF = CommonUtils.expandEnvVars(TEST_SPEECH_ANALYSIS_WORKFLOW_DEF.trim(), envMap);
    }


    @Test
    public void testRunBasicWorkflow() throws Exception {

        // Needs a connection to the server to run these tests...

        //First load the workflow
        OliveWorkflowDefinition owd = new OliveWorkflowDefinition(TEST_SAD_LID_WORKFLOW_DEF);
        System.out.println(owd.toString());
        ActivatedWorkflow ow = owd.createWorkflow(server);

        log.info("Workflow task names: {}", ow.getAnalysisTaskNames());

        for(JobDefinitionWrapper jdw : ow.getAnalysisTasks()){
            // There should be only one job...
            log.info("Job definition name: {}, data type: {}",jdw.getJobName(), jdw.getDataHandlerProperty().getType());
            for(WorkflowTaskWrapper task : jdw.getTasks()){
                log.info("Task name: {}, analysis trait: {}, task: {}, plugin: {}, domain: {}",
                        task.getTaskName(),
                        task.getTraitType(),
                        task.getTaskType(),
                        task.getPluginName(),
                        task.getDomainName()  );
            }
        }
        // Prepare a callback:

        Server.ResultCallback<Olive.WorkflowAnalysisRequest, Olive.WorkflowAnalysisResult> rc = new Server.ResultCallback<Olive.WorkflowAnalysisRequest, Olive.WorkflowAnalysisResult>() {

            @Override
            public void call(Server.Result<Olive.WorkflowAnalysisRequest, Olive.WorkflowAnalysisResult> r) {

                try {
                    Map<String, JobResult> jobResults = WorkflowUtils.extractWorkflowAnalysis(r.getRep());

                    // do something with the results:
                    if (!r.hasError()) {

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
                        log.error("Workflow request failed: {}", r.getError());
                    }
                } catch (Exception e) {
                    log.error("Workflow request failed with error: ", e);
                }


            }

        };

        // Send a file
        Olive.Audio.Builder audio = ClientUtils.createAudioFromFile(AUDIO_TEST_FILE,
                -1,
                ClientUtils.AudioTransferType.SEND_SERIALIZED_BUFFER,
                new ArrayList<>());
        Olive.WorkflowDataRequest wdr = ow.packageAudio(audio.build(), AUDIO_TEST_FILE);

        // OR
        Olive.BinaryMedia.Builder media = ClientUtils.createBinaryMediaFromFile(AUDIO_TEST_FILE,
                ClientUtils.AudioTransferType.SEND_SERIALIZED_BUFFER,
                new ArrayList<>());
        Olive.WorkflowDataRequest mdr = ow.packageBinaryMedia(media.build(), AUDIO_TEST_FILE);

        List<Olive.WorkflowDataRequest> dataList = new ArrayList<>();
        dataList.add(wdr);
        ow.analyze(dataList, rc, new ArrayList<>());

        // Now we need to wait for the result...
        while(server.hasPendingRequests()){

            Thread.sleep(200);
        }
        Thread.sleep(100);
        System.out.println("Request done");
    }

    @Test
    public void testWorkflowClasses() throws Exception {

        // Needs a connection to the server to run these tests...

        //First load the workflow
        OliveWorkflowDefinition owd = new OliveWorkflowDefinition(TEST_SAD_LID_WORKFLOW_DEF);
        ActivatedWorkflow ow = owd.createWorkflow(server);

        // Prepare a callback:
        Server.ResultCallback<Olive.WorkflowClassStatusRequest, Olive.WorkflowClassStatusResult> rc = new Server.ResultCallback<Olive.WorkflowClassStatusRequest, Olive.WorkflowClassStatusResult>() {

            @Override
            public void call(Server.Result<Olive.WorkflowClassStatusRequest, Olive.WorkflowClassStatusResult> r) {

//                Map<String, JobResult> jobResults = WorkflowUtils.extractWorkflowAnalysis(r.getRep());

                // do something with the results:
                if (!r.hasError()) {


                    for (Olive.JobClass jobs : r.getRep().getJobClassList()) {
                        // print results
                        System.out.println(String.format("Job %s has %d tasks:",jobs.getJobName(), jobs.getTaskCount()));
                        for (Olive.TaskClass tc : jobs.getTaskList()){
                            System.out.println(String.format("Task: '%s' has Class IDs:",tc.getTaskName()));
                            for(String id : tc.getClassIdList()){
                                System.out.println(String.format("\t%s",id));
                            }
                        }
                    }

                } else {
                    log.error("Workflow request failed: {}", r.getError());
                }
            }
        };

        ow.currentClasses(rc);
        // OR
//        ow.currentClasses(rc, Olive.WorkflowType.WORKFLOW_ENROLLMENT_TYPE);


        // Now we need to wait for the result...
        while(server.hasPendingRequests()){

            Thread.sleep(200);
        }
        Thread.sleep(100);
        System.out.println("Request done");
    }


    @Test
    public void testWorkflowEqual() throws Exception {

        // Load the same WFD in two different objects, they should be the same:

        OliveWorkflowDefinition orig = new OliveWorkflowDefinition(TEST_SAD_LID_WORKFLOW_DEF);
        OliveWorkflowDefinition duplicate = new OliveWorkflowDefinition(TEST_SAD_LID_WORKFLOW_DEF);
        OliveWorkflowDefinition odd = new OliveWorkflowDefinition(TEST_SPEECH_ANALYSIS_WORKFLOW_DEF);

        Assert.assertEquals(duplicate, orig);
        Assert.assertNotEquals(duplicate, odd);
        Assert.assertNotEquals(orig, odd);

        // Now activate these workflows and compare again
        ActivatedWorkflow activatedOrig = orig.createWorkflow(server);
        ActivatedWorkflow activatedDup = duplicate.createWorkflow(server);
        ActivatedWorkflow activatedOdd = odd.createWorkflow(server);


        // These should be the same
        Assert.assertEquals(activatedOrig, activatedDup);
        // And of course these should not
        Assert.assertNotEquals(activatedOrig, activatedOdd);
        Assert.assertNotEquals(activatedOrig, orig);



    }
}
