package com.sri.speech.olive.api.utils.reports;

import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.client.SADWord;
import com.sri.speech.olive.api.utils.ClientUtils;

import java.io.File;
import java.util.List;

/**
 */
public class FrameScorerReportWriter extends  AbstractReportWriter {


    private String delimiter = " ";
    private boolean applyPEMFormat = false;
    private double thresholdNumber;
    private String audioFilename;

    public FrameScorerReportWriter(String rootPathName, String filename, String channel, boolean applyThreshold, double threshold) throws ReportException{

        if(applyThreshold){
            //All scores are written to a 'PEM' formatted file (filename, channel, class, start, end)
            // We don't use channel number when creating the names of a pem formatted file, since a pem file can contain multiple channels
            init(rootPathName.isEmpty() ? "OUTPUT/" : rootPathName, "", String.format("%.1f", threshold), "pem");
            // now set the channel
            this.channel = channel;
        }
        else {
            // todo get just the base file
            init(rootPathName.isEmpty() ? "OUTPUT/" : rootPathName, channel, new File(filename).getName() , "scores");
        }



        //  todo use filename for frame scores
        //super(rootPathName.isEmpty() ? "OUTPUT/" : rootPathName, channel, "frame.output", "txt");

        applyPEMFormat = applyThreshold;
        thresholdNumber  = threshold;
        audioFilename = filename;
    }


    public void addData(Olive.FrameScores scores, String channel) throws ReportException {

        // validate scores, make sure they have calibration data
        boolean valid = true;
        // todo validate or filter?

        if (!valid || isClosed()) {
            // Not creating a calibration report.. no calibration data
            return;
        }

        int count = 0;
        if(applyPEMFormat){
            List<SADWord> words = ClientUtils.thresholdFrames(scores, thresholdNumber);

            for (SADWord s : words) {
                writePEMBody(s, channel);
                count++;

            }
        }
        else {
            synchronized (writeEvent) {


                for (Double s : scores.getScoreList()) {
                    writeBody(s);
                    count++;

                }
            }
        }

        logger.debug("Wrote '"
                + count
                + "'  frame scores to report file '"
                + filename
                + "'");
    }

    void writeBody(Double s){

            writer.println(String.format("%.5f", s));


    }

    void writePEMBody(SADWord word, String channel){

            writer.println(String.format("%s %s %s %.3f %.3f", audioFilename, channel, "speech", word.getStartTimeSeconds(), word.getEndTimeSeconds()));


    }



}
