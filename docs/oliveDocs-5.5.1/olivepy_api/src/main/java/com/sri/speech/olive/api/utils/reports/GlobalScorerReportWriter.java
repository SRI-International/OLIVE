package com.sri.speech.olive.api.utils.reports;

import com.sri.speech.olive.api.Olive;

import java.util.List;

/**
 */
public class GlobalScorerReportWriter  extends  AbstractReportWriter {

    //private static final Logger logger = LoggerFactory.getLogger(GlobalScorerReportWriter.class);

    private String delimiter = " ";

    private String rootPathName;

//    // Don't set these until we have our first valid row of calibration data
//    private Path filename;
//    private PrintWriter writer;
//
//    private boolean closed = false;

    public GlobalScorerReportWriter(String rootPathName, String channel) throws ReportException{

        super(rootPathName, channel, "global.output", "txt");

    }


    public void addData(String audioFilename, List<Olive.GlobalScore> scores) throws ReportException {

        // validate scores, make sure they have calibration data
        boolean valid = true;
        // todo validate or filter?

        if (!valid || isClosed()) {
            // Not creating a calibration report.. no calibration data
            return;
        }

        int count = 0;
        synchronized (writeEvent){


            for (Olive.GlobalScore s : scores) {
                writeBody(audioFilename, s);
                count++;

            }
        }

        logger.debug("Wrote '"
                + count
                + "'  scores to report file '"
                + filename
                + "'");
    }

    void writeBody(String audioFilename, Olive.GlobalScore s){

            StringBuilder buffer = new StringBuilder(audioFilename);
            buffer.append(delimiter);
            buffer.append(s.getClassId());
            buffer.append(delimiter);
            buffer.append(String.format("%.10f", s.getScore()));

            writer.println(buffer.toString());


    }



}
