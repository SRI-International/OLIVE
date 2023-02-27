package com.sri.speech.olive.api.workflow.wrapper;

import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * A wrapper around a WorkflowJobResult message from OLIVE, execpt the all tasks are fully deserialized into
 * protofbuf messages (so TaskResult would contain a FrameScorerResult and not a byte stream as in the original
 * WorkflowTaskResult in the .
 */
public class JobResult {

    private static Logger log = LoggerFactory.getLogger(JobResult.class);

    // If this job failed, then it will have an error message:
    boolean isError;
    String errMsg;

    String jobName;

    Map<String, List<TaskResult>> tasks;
    List<DataResult> dataResults;

    // trait (global scorer), task type (SAD), error (if any), analysis (deserialized)
    public JobResult(Workflow.WorkflowJobResult jobResult, Map<String, List<TaskResult>> jobTasks, List<DataResult> jobDataResults ){

        this.jobName = jobResult.getJobName();
        this.dataResults = jobDataResults;
        this.tasks = jobTasks;

        if (jobResult.hasError()){
            this.isError = true;
            this.errMsg = jobResult.getError();
            return;
        }
        else {
            this.isError = false;
        }


    }

    public String getErrMsg() {
        return isError ? errMsg : "";
    }

    public boolean isError() {return isError;}

    public String getJobName() {
        return jobName;
    }

    public Map<String, List<TaskResult>> getTasks() {
        return tasks;
    }

    public List<DataResult> getDataResults() {
        return dataResults;
    }


}
