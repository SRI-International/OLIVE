package com.sri.speech.olive.api.workflow;

import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Workflow;
import com.sri.speech.olive.api.utils.Pair;
import com.sri.speech.olive.api.workflow.wrapper.JobDefinitionWrapper;
import com.sri.speech.olive.api.workflow.wrapper.WorkflowTaskWrapper;
import org.apache.commons.lang.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BaseWorkflow {

    private static Logger log = LoggerFactory.getLogger(BaseWorkflow.class);

    protected Workflow.WorkflowDefinition workflowDefinition;

    public BaseWorkflow(Workflow.WorkflowDefinition workflow) {
        this.workflowDefinition = workflow;
    }
    public BaseWorkflow() {
        this.workflowDefinition = null;
    }


    public String getWorkflowDescription(){
        if(this.workflowDefinition.hasDescription()){
            return this.workflowDefinition.getDescription();
        }

        return "";
    }

    public Workflow.WorkflowDefinition getWorkflowDefinition(){
        return this.workflowDefinition;
    }

    /*
            The names of analysis tasks supported by this Workflow
         */
    public List<String> getAnalysisTaskNames(){
        return getTaskNames(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE);
    }

    /*
        The names of enrollments tasks supported by this workflow
         */
    public List<String> getEnrollmentTaskNames(){
        return getTaskNames(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE);
    }

    /**
     * Complex workflows may have multiple jobs, but generally the user should assume there is just one job.  Exposing
     * multiple jobs to the client may be removed in future versions of this API as this may pass on un-necessary
     * complexity to the client.
     *
     * @return
     */
    public List<String> getAnalysisJobNames(){
        return getJobNames(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE);
    }

    /**
     * Complex workflows may have multiple jobs, but generally the user should assume there is just one job.  Exposing
     * multiple jobs to the client may be removed in future versions of this API as this may pass on un-necessary
     * complexity to the client.
     *
     * @return
     */
    public List<String> getEnrollmentJobNames(){
        return getJobNames(Workflow.WorkflowType.WORKFLOW_ENROLLMENT_TYPE);
    }

    /**
     * Return the tasks, grouped by job
     */
    public List<JobDefinitionWrapper> getAnalysisTasks(){
        return getTasksImpl(Workflow.WorkflowType.WORKFLOW_ANALYSIS_TYPE);
    }


    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BaseWorkflow pair = (BaseWorkflow) o;

        // Compare Workflow definitions
        return ((BaseWorkflow) o).workflowDefinition.equals(this.workflowDefinition);

    }

    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder();

        for(JobDefinitionWrapper jdw : getAnalysisTasks()){
            // There should be only one job...
            builder.append(String.format("Job definition name: %s, data type: %s\n",jdw.getJobName(), jdw.getDataHandlerProperty().getType())) ;
            for(WorkflowTaskWrapper task : jdw.getTasks()){

                builder.append(String.format("Task name: %s, analysis trait: %s, task: %s, plugin: %s, domain: %s)",
                        task.getTaskName(),
                        task.getTraitType(),
                        task.getTaskType(),
                        task.getPluginName(),
                        task.getDomainName() ) );
            }
        }

        return builder.toString();
//        return "BaseWorkflow{" +
//                "workflowDefinition=" + workflowDefinition +
//                '}';
    }

    @Override
    public int hashCode() {
        return workflowDefinition.hashCode();
    }

    private List<JobDefinitionWrapper> getTasksImpl(Workflow.WorkflowType type){

        List<JobDefinitionWrapper> jobTasks = new ArrayList<>();
        try {
            List<Workflow.JobDefinition> jobDefs = getOrderJobs(type);
            for (Workflow.JobDefinition jobDef : jobDefs){
                // Each job has it's own tasks and data properties
                JobDefinitionWrapper jdw = new JobDefinitionWrapper(jobDef);
                jobTasks.add(jdw);
            }
        } catch (Exception e) {
            // assume okay?
            log.error("Unable to extract job definition becuase: ", e);
        }

        return jobTasks;
    }

    private List<String> getTaskNames(Workflow.WorkflowType type){
        List<String> taskNames = new ArrayList<>();

        // Right now we only support one analysis job, so we return all task names without grouping by job - even
        // when we support jobs we likely want to just list the tasks, as the job groupings is unlikely to be helpful
        // to the client, this is more important for how OLIVE works
        try {
            List<Workflow.JobDefinition> jobDefs = getOrderJobs(type);
            for (Workflow.JobDefinition jobDef : jobDefs){
                for (Workflow.WorkflowTask task : jobDef.getTasksList()){
                    // add a debug option to print this info?
                    if(task.hasReturnResult() && task.getReturnResult()){
                        taskNames.add(task.getConsumerResultLabel());
                    }
                }
            }
        } catch (Exception e) {
            // assume okay
            log.error("Unable to extract job definition becuase: ", e);
        }

        return taskNames;
    }

    private List<String> getJobNames(Workflow.WorkflowType type){
        List<String> jobNames = new ArrayList<>();

        // We /should/ have only one job in a workflow definition but this might change with conditional workflows
        // and/or if we need to support some odd use cases with tasks that consume different sets of data
        try {
            List<Workflow.JobDefinition> jobDefs = getOrderJobs(type);
            for (Workflow.JobDefinition jobDef : jobDefs){
                jobNames.add(jobDef.getJobName());
            }
        } catch (Exception e) {
            // assume okay
            log.error("Unable to extract job definition becuase: ", e);
        }

        return jobNames;
    }

    private List<Workflow.JobDefinition> getOrderJobs(Workflow.WorkflowType type) throws WorkflowException {

        for (Workflow.WorkflowOrderDefinition wod : workflowDefinition.getOrderList()){
            if (wod.getWorkflowType() == type){
                return wod.getJobDefinitionList();
            }
        }

        throw new WorkflowException("No jobs found for workflow type:" + type.toString());

    }
}
