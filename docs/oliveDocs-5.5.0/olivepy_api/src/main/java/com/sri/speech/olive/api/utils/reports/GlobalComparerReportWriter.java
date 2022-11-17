package com.sri.speech.olive.api.utils.reports;

import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Server;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 */
public class GlobalComparerReportWriter extends  AbstractReportWriter {

    //private static final Logger logger = LoggerFactory.getLogger(GlobalScorerReportWriter.class);

    private String delimiter = " ";

    private String rootPathName;
    private String file1;
    private String file2;


//    // Don't set these until we have our first valid row of calibration data
//    private Path filename;
//    private PrintWriter writer;
//
//    private boolean closed = false;

    public GlobalComparerReportWriter(String rootPathName, String file1, String file2, String channel) throws ReportException{

        super(rootPathName, channel, String.format("%s-%s-compare.output", new File(file1).getName(), new File(file2).getName()), "txt");
//        rootPathName = rootPathName + String.format("%s-%s-compare.output", file1, file2)
        this.rootPathName = rootPathName;
        this.file1 = new File(file1).getName();
        this.file2 = new File(file2).getName();

    }


    public void addData( Map<String, Server.MetadataWrapper> results, Olive.GlobalComparerReport report) throws ReportException {

        if (!validate())
            return;

        int count = 0;
        synchronized (writeEvent){


            for (String key : results.keySet()) {
//                Server.MetadataWrapper mw = Olive.server.deserializeMetadata(meta);
                writeBody(key, results.get(key));
                count++;

            }

            // Save report
            if (report.getType() == Olive.ReportType.PDF) {
                String rptName = String.format("%s-%s.pdf", file1, file2);
                Path path = Paths.get(rootPathName, rptName);

                try {
                    // Save the buffer as a PDF:
                    InputStream buffer = new ByteArrayInputStream(report.getReportData().toByteArray());
                    Files.copy(buffer, path, StandardCopyOption.REPLACE_EXISTING);
                    //FileOutputStream fos = new FileOutputStream(path.toFile());

                } catch (Exception e) {
                    logger.error("Failed to save comparison report because: {}", e.getMessage());
                }

            }


        }

        logger.debug("Wrote '"
                + count
                + "'  scores to report file '"
                + filename
                + "'");
    }

    void writeBody(String meta, Server.MetadataWrapper m){

            StringBuilder buffer = new StringBuilder(meta);
            buffer.append(delimiter);
            // todo improve formatting
//            buffer.append(String.format("%.10f", s.getScore()));
            buffer.append(m.toString());

            writer.println(buffer.toString());




    }



}
