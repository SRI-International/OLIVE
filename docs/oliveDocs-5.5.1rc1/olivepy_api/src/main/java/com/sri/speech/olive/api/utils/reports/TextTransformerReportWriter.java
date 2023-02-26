package com.sri.speech.olive.api.utils.reports;

import com.sri.speech.olive.api.Olive;

import java.util.List;

/**
 */
public class TextTransformerReportWriter extends  AbstractReportWriter {

    //private static final Logger logger = LoggerFactory.getLogger(GlobalScorerReportWriter.class);

    private String delimiter = " ";




    public TextTransformerReportWriter(String rootPathName, String channel) throws ReportException{

        super(rootPathName, channel, "text.output", "txt");

    }


    public void addError(String errMsg) throws ReportException {

        if (isClosed()) {
            // Not creating a calibration report.. no calibration data
            return;
        }

        synchronized (writeEvent){
            writer.println(errMsg);
        }


    }

    public void addData(List<Olive.TextTransformation> scores) throws ReportException {

        // validate scores, make sure they have calibration data
        boolean valid = true;

        if (!valid || isClosed()) {
            // Not creating a calibration report.. no calibration data
            return;
        }

        int count = 0;
        synchronized (writeEvent){


            for (Olive.TextTransformation s : scores) {
                writeBody(s);
                count++;

            }
        }

        logger.debug("Wrote '"
                + count
                + "'  transformation results to report file '"
                + filename
                + "'");
    }

    void writeBody(Olive.TextTransformation s){

        StringBuilder buffer = new StringBuilder(s.getTransformedText());
//        buffer.append(s.getTransformedText());
        writer.println(buffer.toString());


    }



}
