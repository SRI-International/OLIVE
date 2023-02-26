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
 * Utility to parse an input file contains  2 columns, with the first column the filename and the
 * second the class ID.  Similar to the input file used when testing the OLIVE Python scenicenroll tool.
 *
 */
public class ClassRegionParser {

    private static Logger log = LoggerFactory.getLogger(ClassRegionParser.class);

    // a list of class enrollments, where the filename is the key.  The same file can not be enrolled for multiple classes
    private Map<String, String> classEnrollments = new HashMap<>();


    public boolean isValid(){
        return !classEnrollments.isEmpty();
    }


    public Collection<String> getFilenames(){

        return classEnrollments.keySet();
    }

    public String getClass(String filename){
        return classEnrollments.get(filename);
    }

    /**
     * Return the list of file/class enrollements from the file.
     * @return zero or more class enrollments
     */
    public List<Pair<String, String>> getEnrollments(){

        List<Pair<String, String>> rtn = new ArrayList<>();

        for(String f : classEnrollments.keySet()){
            rtn.add(new Pair<>(f, classEnrollments.get(f)));
        }

        return rtn;


    }

    public ClassRegionParser() {
    }

    public boolean parse(String  filePath){


            String line;
            // Get the system env variables just in case
            Map<String, String> envMap = System.getenv();

            try {

                Path fp = CommonUtils.resolvePath(filePath);
                BufferedReader reader = new BufferedReader(new FileReader(fp.toFile()));
                while ((line = reader.readLine()) != null) {

                    String[] splits = line.split("\\s+");

                    // columns should be consistent
                    String filename = null;
                    String classID = null;


                    // Validate
                    // Confirm 2 columns
                    if (splits.length == 2){ // 2 column
                        filename = splits[0];
                        classID = splits[1];
                    }
                    else { //  invalid
                        log.warn("Invalid input line '{}' in file '{}'.  Line should have 2 columns: a audio filename followed by a class ID (speaker name)", line, filePath);
                        continue;
                    }

                    if (!Files.exists(Paths.get(filename))){
                        // That path couldn't be found try to replace any environmenat variables in it and try again
                        String resolvedPath = CommonUtils.expandEnvVars(filename, envMap);
                        Path rfp = CommonUtils.resolvePath(resolvedPath);
                        if (!Files.exists(rfp)) {
                            log.warn("Input file '{}'  does not exist. Ignoring", filename);
                            continue;
                        }
                        filename = rfp.toString();
                    }

                    if(classEnrollments.containsKey(filename)){
                        log.warn("File {} is already associated with class {}, Ignoring line '{}'", filename, classEnrollments.get(filename), line);
                    }
                    else {
                        // Add the class
                        classEnrollments.put(filename, classID);
                    }


                }
            } catch (Exception e) {
                log.error("Failed to parse input file '{}' because: {}", filePath, e.getMessage(), e);
                return false;
            }
            return !classEnrollments.isEmpty();
        }






}
