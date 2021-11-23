package com.sri.speech.olive.api.utils.parser;

import com.sri.speech.olive.api.utils.CommonUtils;
import com.sri.speech.olive.api.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Utility to parse an input file containing only class IDs, where each line is a class (that can contain spaces)
 *
 */
public class ClassParser {

    private static Logger log = LoggerFactory.getLogger(ClassParser.class);

    // a list of class enrollments, where the filename is the key.  The same file can not be enrolled for multiple classes
    private List<String> classEnrollments = new ArrayList<>();

    public boolean isValid(){
        return !classEnrollments.isEmpty();
    }

    public Collection<String> getClasses(){
        return classEnrollments;
    }


    public ClassParser() {
    }

    public boolean parse(String  filePath){


            String line;
            // Get the system env variables just in case
            Map<String, String> envMap = System.getenv();

            // Should we validate this isn't files?
            boolean fileCheck = true;
            try {
                Path fp = CommonUtils.resolvePath(filePath);
                BufferedReader reader = new BufferedReader(new FileReader(fp.toFile()));
                while ((line = reader.readLine()) != null) {
//                    String[] splits = line.split("\\s+");
                    classEnrollments.add(line);

                    // Validate not a file
                    // Confirm 1 column
                    if (fileCheck) { // validate this isn't a filename
                        if (Files.exists(Paths.get(line))) {
                            log.warn("File '{}' appears to contain filenames not class IDs: {}", filePath, line);
                            // Warn don't give an error... I guess they could have filenames as classes?
                        }
                        // We only check the first line
                        fileCheck = false;
                    }



                }
            } catch (Exception e) {
                log.error("Failed to parse input file '{}' because: {}", filePath, e.getMessage(), e);
                return false;
            }
            return !classEnrollments.isEmpty();
        }






}
