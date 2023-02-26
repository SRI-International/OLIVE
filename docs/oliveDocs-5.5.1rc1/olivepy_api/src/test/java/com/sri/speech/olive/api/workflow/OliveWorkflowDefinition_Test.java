package com.sri.speech.olive.api.workflow;


import com.google.protobuf.TextFormat;
import com.google.protobuf.util.JsonFormat;
import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Server;
import com.sri.speech.olive.api.utils.CommonUtils;
import com.sri.speech.olive.api.workflow.wrapper.*;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * These tests need the test/mock server plugins
 */
public class OliveWorkflowDefinition_Test {

    private static Logger log = LoggerFactory.getLogger(OliveWorkflowDefinition_Test.class);

    public String AUDIO_TEST_FILE = "${TEST_DATA_ROOT}/testSuite/general/fsh_kws.wav";
    public String  TEST_SAD_LID_WORKFLOW_DEF = "${TEST_DATA_ROOT}/testSuite/workflows/orig/sad-lid-sid_abstract.workflow";
    public String TEST_AED_WORKFLOW_DEF_TXT = "${TEST_DATA_ROOT}/testSuite/workflows/aed/aed_abstract.txt";
    public String  TEST_AED_WORKFLOW_DEF_BINARY = "${TEST_DATA_ROOT}/testSuite/workflows/orig/aed_abstract.workflow";
//    public String  TEST_AED_WORKFLOW_DEF = "${TEST_DATA_ROOT}/testSuite/workflows/test_enroll.txt";
    public String  TEST_SAD_LID_SID_SDD_QBE_WORKFLOW_DEF = "${TEST_DATA_ROOT}/testSuite/workflows/sad-lid-sid-sdd-qbe_abstract.workflow";

    public static final int scenicPort      = 5588;
    public static final String scenicHost   = "localhost";
    private static final int TIMEOUT        = 10000;  // 10 seconds is enough?

    private Server server;

    public OliveWorkflowDefinition_Test(){
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
        TEST_AED_WORKFLOW_DEF_TXT = CommonUtils.expandEnvVars(TEST_AED_WORKFLOW_DEF_TXT, envMap);
        TEST_AED_WORKFLOW_DEF_BINARY = CommonUtils.expandEnvVars(TEST_AED_WORKFLOW_DEF_BINARY, envMap);
        TEST_SAD_LID_SID_SDD_QBE_WORKFLOW_DEF = CommonUtils.expandEnvVars(TEST_SAD_LID_SID_SDD_QBE_WORKFLOW_DEF, envMap);
    }

    @Test
    public void testJSONAnalysisJobs() throws Exception {

        //First load the workflow
        OliveWorkflowDefinition ow = new OliveWorkflowDefinition(TEST_SAD_LID_WORKFLOW_DEF);
        // Convert it to JSON
        String workflowJasonStr = JsonFormat.printer().print(ow.workflowDefinition);
        System.out.println(workflowJasonStr);


    }

    @Test
    public void testAnalysisJobs() throws Exception {

        // Needs a connection to the server to run these tests...

        //First load the workflow
        OliveWorkflowDefinition ow = new OliveWorkflowDefinition(TEST_SAD_LID_WORKFLOW_DEF);

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

    }


    @Test
    public void testAnalysisJobs_text_workflow() throws Exception {

        Olive.FrameScorerRequest.Builder fsrBuilder = Olive.FrameScorerRequest.newBuilder();
        fsrBuilder.setPlugin("pluginName").setDomain("default");

        Olive.FrameScorerRequest fsr = fsrBuilder.build();
        String fsrString = fsr.toString();

        Olive.FrameScorerRequest.Builder fsrStrBuilder = Olive.FrameScorerRequest.newBuilder();
        TextFormat.getParser().merge(fsrString, fsrStrBuilder);
        // Non json string
        Olive.FrameScorerRequest finalFsr = fsrStrBuilder.build();

        // JSON example
        String fsrJasonStr = JsonFormat.printer().print(fsrBuilder);
        Olive.FrameScorerRequest.Builder fsrJsonBuilder = Olive.FrameScorerRequest.newBuilder();
        JsonFormat.parser().merge(fsrJasonStr, fsrJsonBuilder);
        Olive.FrameScorerRequest finalJsonFsr = fsrJsonBuilder.build();

        // We will need to convert the JSON string into an actual JSON strucutre so we can handle the binary messageData
        JSONParser parser = new JSONParser();
        Object obj = parser.parse(fsrJasonStr);

        //First load the workflow (as text file)
        OliveWorkflowDefinition owFromTxt = new OliveWorkflowDefinition(TEST_AED_WORKFLOW_DEF_TXT);
        OliveWorkflowDefinition owFromBinary = new OliveWorkflowDefinition(TEST_AED_WORKFLOW_DEF_BINARY);


        // We verify that our workflows are the same when created from a text file or a (binary) protobuf
        //Assert.assertEquals(owFromTxt, owFromBinary);

        log.info("Workflow task names: {}", owFromTxt.getAnalysisTaskNames());

        for(JobDefinitionWrapper jdw : owFromTxt.getAnalysisTasks()){
            // There should be only one job...
            log.info("Job definition name: {}, data type: {}, description: {}",jdw.getJobName(), jdw.getDataHandlerProperty().getType(), jdw.getDescription());
            for(WorkflowTaskWrapper task : jdw.getTasks()){
                log.info("Task name: {}, analysis trait: {}, task: {}, plugin: {}, domain: {}, description: {}",
                        task.getTaskName(),
                        task.getTraitType(),
                        task.getTaskType(),
                        task.getPluginName(),
                        task.getDomainName(),
                        task.getDescription() );
            }
            boolean match = false;
            for (JobDefinitionWrapper binJW : owFromBinary.getAnalysisTasks()){
                if (binJW.getJobName().equals(jdw.getJobName())){
                    match = true;
                }
                // check tasks...
            }
            Assert.assertTrue(match);
        }
        log.info(String.format("Analysis tasks '%s' ", owFromTxt.getAnalysisTaskNames()));
        log.info(String.format("Workflow Description: '%s' ", owFromTxt.getWorkflowDescription()));

        Assert.assertEquals(4, owFromTxt.getAnalysisTasks().get(0).getTasks().size());
        Assert.assertEquals(4, owFromTxt.getAnalysisTaskNames().size());

    }


}
