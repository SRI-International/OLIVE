package com.sri.speech.olive.api.utils.reports;

import com.sri.speech.olive.api.client.TaskType;
import com.sri.speech.olive.api.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Maintains a collection of reports based on task type and channel id
 */
public class TaskReportManager {


    private static Logger log = LoggerFactory.getLogger(TaskReportManager.class);


    private final Object reportEvent = new Object();

    private Map<Pair<TaskType, String>, AbstractReportWriter> reportWriters = new HashMap<>();

    public TaskReportManager(){

    }


    /**
     *
     * @return a new ReportGenerator if none exists or an existing
     */
    public AbstractReportWriter getWriter(Pair<TaskType, String> key, ReportGenerator generator) throws ReportException {

        // First see if we have already created a report
        if(reportWriters.containsKey(key)){
            return reportWriters.get(key);
        }
        else {
            synchronized (reportEvent) {
                // double check now that we have the lock
                if(reportWriters.containsKey(key)){
                    return reportWriters.get(key);
                }

                reportWriters.put(key, generator.createWriter());

            }
        }

        return reportWriters.get(key);

        //throw new ReportException(String.format("Reports for task type '%s' and channel '%s' are unsupported", type, channel == null ? "mono" : channel));

    }

    public void closeAllReports(){
        for(Pair<TaskType, String> key : reportWriters.keySet()){
            try {
                reportWriters.get(key).close();
            } catch (ReportException e) {
                log.error("Unable to close report for task '{}' and channel: '{}' ", key.getFirst(), key.getSecond());
            }
        }
    }



}
