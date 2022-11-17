package com.sri.speech.olive.api.utils.reports;

/**
 *
 */
public interface ReportGenerator {

    AbstractReportWriter createWriter(/*String rootPth, String audioFilename, String channel*/) throws ReportException;
}
