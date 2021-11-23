package com.sri.speech.olive.api.utils.reports;

import com.sri.speech.olive.api.Olive;

import java.util.List;

/**
 */
public class RegionScorerReportWriter extends  AbstractReportWriter {

    //private static final Logger logger = LoggerFactory.getLogger(GlobalScorerReportWriter.class);

    private String delimiter = " ";




    public RegionScorerReportWriter(String rootPathName, String channel) throws ReportException{

        super(rootPathName, channel, "region.output", "txt");

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

    public void addData(String audioFilename, List<Olive.RegionScore> scores) throws ReportException {

        // validate scores, make sure they have calibration data
        boolean valid = true;
        // todo validate or filter?

        if (!valid || isClosed()) {
            // Not creating a calibration report.. no calibration data
            return;
        }

        int count = 0;
        synchronized (writeEvent){


            for (Olive.RegionScore s : scores) {
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

    void writeBody(String audioFilename, Olive.RegionScore s){

        StringBuilder buffer = new StringBuilder(audioFilename);
        buffer.append(delimiter);
        buffer.append(String.format("%.2f", s.getStartT()));
        buffer.append(delimiter);
        buffer.append(String.format("%.2f", s.getEndT()));
        buffer.append(delimiter);
        buffer.append(s.getClassId());
        buffer.append(delimiter);
        buffer.append(String.format("%.10f", s.getScore()));
        writer.println(buffer.toString());


    }



}
