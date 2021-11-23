package com.sri.speech.olive.api.utils.parser;

import com.sri.speech.olive.api.utils.CommonUtils;
import com.sri.speech.olive.api.utils.RegionWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Utility to parse an input file that may contain 1, 2, or 3 columns.  Similar to the input file used when testing the OLIVE Python CLI tools
 * <ul>
 *     <li>A file with one column includes only file name inputs (no regions).</li>
 *     <li>A file with two columns contains only region inputs (start and end time) (no filename, the input audio is specified some other way.
 *     These regions are assumed to belong to one file)</li>
 *     <li>A file with 3 columns includes a filename in the first column, followed by a start time and end time (in seconds)</li>
 * </ul>
 *
 */
public class RegionParser {

    private static Logger log = LoggerFactory.getLogger(RegionParser.class);
    private static final String DEFAULT_FILE_KEY = "DEFAULT_FILE_KeY";


    private Map<String, List<RegionWord>> regions = new LinkedHashMap<>();


    private boolean regionsOnly = false;

    public boolean isValid(){
        return !regions.isEmpty();
    }

    public boolean isRegionsOnly(){
        return regionsOnly;
    }

    public Collection<String> getFilenames(){
        if (regionsOnly) {
            return new ArrayList<>();
        }
        return regions.keySet();
    }

    /**
     * Return the list of regions in the input file for the specified file.
     * @param filename fetch regions for this file, If this is a regions only file then this value may be null
     * @return zero or more regions in the input for this file
     */
    public List<RegionWord> getRegions(String filename){

        if(isRegionsOnly()){
            return regions.get(DEFAULT_FILE_KEY);
        }
        else {
            if (regions.containsKey(filename)){
                return regions.get(filename);
            }

            return new ArrayList<>();
        }

    }

    public RegionParser() {
    }

    public boolean parse(String  filePath){


        Path fp = CommonUtils.resolvePath(filePath);
            String line;
            // Get the system env variables just in case
            Map<String, String> envMap = System.getenv();

            try {
                BufferedReader reader = new BufferedReader(new FileReader(fp.toFile()));
                while ((line = reader.readLine()) != null) {

                    String[] splits = line.split("\\s+");

                    // columns should be consistent
                    String filename = DEFAULT_FILE_KEY;
                    boolean noRegions = false;
                    int startIndex = -1;
                    int endIndex = -1;

                    // Validate
                    if (splits.length < 1){
                        log.warn("Invalid input line '{}' in file '{}'", line, fp.toString());
                        continue;
                    }
                    else if (splits.length == 1){  // 1 column
                        noRegions = true;
                        filename = splits[0];
                        // No region
                    }
                    else if (splits.length == 2){ // 2 column
                        regionsOnly = true;
                        startIndex = 0;
                        endIndex = 1;

                    }
                    else if (splits.length > 3){ // 3 invalid
                        log.warn("Invalid input line '{}' in file '{}'.  Too many columns", line, fp.toString());
                        continue;
                    }
                    else { // 3 columns
                        filename = splits[0];
                        startIndex = 1;
                        endIndex = 2;
                    }


                    if (!filename.equals(DEFAULT_FILE_KEY)  && !Files.exists(Paths.get(filename))){
                        String resolvedPath = CommonUtils.expandEnvVars(filename, envMap);
                        Path rp = CommonUtils.resolvePath(resolvedPath);
                        if (!Files.exists(rp)) {
                            log.warn("Input file '{}'  does not exist. Ignoring", filename);
                            continue;
                        }
                        filename = rp.toString();
                    }
                    else if (filename.isEmpty()){
                        // SCENIC-1198: Files.exists() considers an empty filename to be valid
                        log.warn("Ignoring empty line in input list, but this may indicate an error in your dataset");
                        continue;
                    }





                    List<RegionWord> regionWords;
                    if (regions.containsKey(filename)) {
                        regionWords = regions.get(filename);
                    }
                    else {
                        regionWords = new ArrayList<>();
                        regions.put(filename, regionWords);
                    }

                    if(startIndex >= 0){
                        try {
                            double start = Double.parseDouble(splits[startIndex]);
                            double end = Double.parseDouble(splits[endIndex]);
                            // We assume input time is in seconds, but we need to convert to milliseconds
                            regionWords.add(new RegionWord((int) (start * 1000), (int) (end * 1000)));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid regions in line '{}', values must be in seconds", line);
                            //continue;
                        }
                    }


                }
            } catch (Exception e) {
                log.error("Failed to parse input file '{}' because: {}", fp.toString(), e.getMessage(), e);
                return false;
            }
            return !regions.isEmpty();
        }






}
