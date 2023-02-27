package com.sri.speech.olive.api.utils.reports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Base class for utility for writing (score?) output to disk
 */

public class AbstractReportWriter {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractReportWriter.class);

    public final static String DEFAULT_FILE_DATE_FORMAT = "yyyy-MM-dd_HHmmss";

    private DateFormat logFileFormat;

    // Don't set these until we have our first valid row of calibration data
    protected Path filename;
    protected PrintWriter writer;
    protected String channel;

    private boolean closed = false;

    protected final Object writeEvent = new Object();

    AbstractReportWriter(){
        logFileFormat = new SimpleDateFormat(DEFAULT_FILE_DATE_FORMAT);
    }

    public AbstractReportWriter(String rootPathName, String channel, String prefix, String extension) throws ReportException{
        init(rootPathName, channel, prefix, extension);
    }

    void init(String rootPathName, String channel, String prefix, String extension) throws ReportException{
        // TODO WHAT IF THE FILE EXISTS?
        try {
            // init the report
            filename    = createFilename(rootPathName, channel, prefix, extension);
            writer      = createFileWriter(filename);
            this.channel = channel;
            logger.debug("Writing report to: {}", filename);

        } catch (ReportException e) {
            closed = true;
            //logger.error("Could not create a calibration report");
            //logger.debug("Error detail: {}", e);
            throw e;

        }
    }

    /**
     * Get the current channel number or "1" if no channel is set
     *
     * @return channel
     */
    public String getChannel(){
        if (null == channel || channel.isEmpty()){
            return  "1";
        }

        return  channel;
    }


    Path createFilename(String parentPath, String channel, String prefix, Date date, String extension ) throws ReportException {

        try {
            if (!Paths.get(parentPath).toFile().exists()) {
                Files.createDirectories(Paths.get(parentPath));
            }

            String filename;
            if( null == channel || channel.isEmpty()) {
                filename = String.format("%s_%s.%s", prefix, logFileFormat.format(date), extension);
            }
            else {
                filename = String.format("%s_ch%s_%s.%s", prefix, channel, logFileFormat.format(date), extension);
            }

            return  Paths.get(parentPath, filename);
        } catch (IOException e) {
            throw  new ReportException(e.getMessage());
        }
    }

    /**
     * Create a Path without including current date in the filename
     * @param parentPath the location of the file
     * @param prefix the file prefix
     * @param extension the extension
     * @return a new path
     * @throws ReportException
     */
    Path createFilename(String parentPath, String channel, String prefix, String extension ) throws ReportException {

        try {

            String filename;
            if( null == channel || channel.isEmpty()) {
                filename = String.format("%s.%s", prefix, extension);
            }
            else {
                filename = String.format("%s_ch%s.%s", prefix, channel, extension);
            }

            Files.createDirectories(Paths.get(parentPath));
            return  Paths.get(parentPath, filename);
        } catch (IOException e) {
            throw  new ReportException(e.getMessage());
        }
    }

    protected PrintWriter createFileWriter(Path filename) throws ReportException {

        try {
            //Create the file
            return new PrintWriter(new FileWriter(filename.toFile()));
        }
        catch (IOException e) {
            throw new ReportException(e.getMessage());
        }

    }

    public void close() throws ReportException{

        synchronized (writeEvent){
            if (null != writer){
                writer.close();
                closed = true;
            }
        }

    }

    public boolean isClosed(){
        return closed;
    }

    public boolean validate() {


        // validate scores, make sure they have calibration data
        boolean valid = true;
        // todo validate or filter?

        if (!valid || isClosed()) {
            // Not creating a calibration report.. no calibration data
            return false;
        }

        return true;
    }

    public String getFilename(){
        return filename.toString();
    }
}
