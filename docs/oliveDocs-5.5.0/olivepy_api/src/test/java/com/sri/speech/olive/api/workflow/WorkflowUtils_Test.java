package com.sri.speech.olive.api.workflow;

import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Workflow;
import com.sri.speech.olive.api.utils.CommonUtils;
import com.sri.speech.olive.api.workflow.wrapper.JobResult;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.Map;

public class WorkflowUtils_Test {

    public String  TEST_SAD_LID_WORKFLOW_ANALYSIS_RESULT = "${TEST_DATA_ROOT}/testSuite/workflows/sad-lid-sid_analysis_result.workflow";

    @BeforeTest
    public void init(){

        Map<String, String> envMap = System.getenv();
        if(!envMap.containsKey("TEST_DATA_ROOT")){
            throw new SkipException("Skipping test 'TEST_DATA_ROOT not set");
        }

        TEST_SAD_LID_WORKFLOW_ANALYSIS_RESULT = CommonUtils.expandEnvVars(TEST_SAD_LID_WORKFLOW_ANALYSIS_RESULT, envMap);
    }

    @Test
    public void testExtractAnalysis() throws Exception {

        // load a saved analysis workflow result:
        Workflow.WorkflowAnalysisResult result = Workflow.WorkflowAnalysisResult.parseFrom(new FileInputStream(Paths.get(TEST_SAD_LID_WORKFLOW_ANALYSIS_RESULT).toFile()));

        Map<String, JobResult> jobMap = WorkflowUtils.extractWorkflowAnalysis(result);

        Assert.assertEquals(jobMap.size(), 1);


    }

}
