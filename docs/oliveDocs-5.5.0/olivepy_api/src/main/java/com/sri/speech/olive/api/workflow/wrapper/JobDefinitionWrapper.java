package com.sri.speech.olive.api.workflow.wrapper;

import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class JobDefinitionWrapper {

    private static Logger log = LoggerFactory.getLogger(JobDefinitionWrapper.class);

    private String jobName;
    private String jobDescription;
    private Workflow.DataHandlerProperty dataHandlerProperty;
    List<WorkflowTaskWrapper> tasks = new ArrayList<>();
    private boolean dynamicJob = false;

    public JobDefinitionWrapper(/*Workflow.DataHandlerProperty data, */Workflow.JobDefinition jobDef){
        this.jobName = jobDef.getJobName();
        this.dataHandlerProperty = jobDef.getDataProperties();
        List<Workflow.WorkflowTask> workflowTasks = jobDef.getTasksList();

        if(jobDef.hasDescription()){
            jobDescription = jobDef.getDescription();
        }
        else {
            jobDescription = "";
        }

        for (Workflow.WorkflowTask wt : workflowTasks){
            WorkflowTaskWrapper wtw = new WorkflowTaskWrapper(wt);
            if(wtw.isResultOutput()){
                tasks.add(wtw);
            }
        }
        if(jobDef.getDynamicJobNameCount() > 0){
            dynamicJob = true;
        }
    }

    public String getJobName() {
        return jobName;
    }

    public Workflow.DataHandlerProperty getDataHandlerProperty() {
        return dataHandlerProperty;
    }

    public List<WorkflowTaskWrapper> getTasks() {
        return tasks;
    }

    /**
     * Return the (optional) description of this job.  If the underlying JobDefinition message does not have a description
     * then an empty string is returned.
     *
     * @return the description, if any, of this task
     */
    public String getDescription() {
        return jobDescription;
    }

    public boolean isDynamicJob(){
        return dynamicJob;
    }
}
