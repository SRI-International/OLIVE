package com.sri.speech.olive.api.workflow;

import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Workflow;
import com.sri.speech.olive.api.workflow.wrapper.DataResult;
import com.sri.speech.olive.api.workflow.wrapper.JobResult;
import com.sri.speech.olive.api.workflow.wrapper.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class WorkflowUtils {

    private static Logger log = LoggerFactory.getLogger(WorkflowUtils.class);

    /**
     * This method parses a WorkflowAnalysisResult message from the server, deserializning any internal task messages
     *
     * @param result a collection of Jobs, indexed by job name.  Typically only one job is returned.  Some complex
     *               workflows may produce multiple jobs and workflows that process each channel in a multi-channel
     *               data (audio) input will return a job for each channel analyzed.
     */
    public static Map<String, JobResult>  extractWorkflowAnalysis(Workflow.WorkflowAnalysisResult result){
        // todo use the workflow definition to add plugin info?

        // Usually an analysis only has one Job, but when handling stereo audio this could result in a job for
        // each channel
        Map<String, JobResult> jobMap = new HashMap<>();

        for (Workflow.WorkflowJobResult job : result.getJobResultList()){

            // Build up a list of task and data used for thoese tasks
            List<DataResult> dataResults = new ArrayList<>();

//            Map<String, TaskResult> taskMap = new HashMap<>();
            Map<String, List<TaskResult>> taskMap = new HashMap<>();

            // First get the data (usually audio) info used for this job
            for(Workflow.WorkflowDataResult dResult : job.getDataResultsList()){
                dataResults.add(new DataResult(dResult, job.getJobName()));
            }

            // Now process the tasks
//            List<TaskResult> tResults = new ArrayList<>();
            for(Workflow.WorkflowTaskResult tResult : job.getTaskResultsList()){

                // Make sure we have a unique name for this task
                String taskName = tResult.getTaskName();

                if(!taskMap.containsKey(taskName)){
                    taskMap.put(taskName, new ArrayList<>());
                }
                List<TaskResult> tResults = taskMap.get(taskName);
                // The protobuf message contains a serialized task result (such as a global scorer result ) which we
                // want to extract now to make down stream processing easier
                String jobName = job.getJobName();
                TaskResult tr = new TaskResult(tResult, jobName, dataResults);
                tResults.add(tr);
            }
            // We assume the job names are unique
            JobResult jr = new JobResult(job, taskMap, dataResults);
            jobMap.put(jr.getJobName(), jr);
        }

        return jobMap;
    }

    public static void extractWorkflowAnalysisByJob(Workflow.WorkflowAnalysisResult result){
        // todo group by job?
        // I'm not sure if this is helpful???
    }



}
