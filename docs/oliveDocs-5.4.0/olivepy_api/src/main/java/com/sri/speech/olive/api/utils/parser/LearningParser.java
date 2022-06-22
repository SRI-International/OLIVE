package com.sri.speech.olive.api.utils.parser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sri.speech.olive.api.utils.CommonUtils;
import com.sri.speech.olive.api.utils.LearningRecord;
import com.sri.speech.olive.api.utils.Pair;
import com.sri.speech.olive.api.utils.RegionWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to parse an input file that may contain 1, 2, or 3 columns. Similar to the input file
 * used with the Python tool scenictrain
 *
 * <ul>
 *   <li>A file with one column includes only file name inputs (no regions): audio_path
 *   <li>A file with two columns contains a file name and class ID: audio_path class_id
 *   <li>A file with 4 columns includes a filename in the first column, followed by a start time and
 *       end time (in seconds), ending with a class id: audio_path start end class_id
 * </ul>
 *
 * Note these values are NOT comma separated, they are separated by spaces.
 */
public class LearningParser {

  private static Logger log = LoggerFactory.getLogger(LearningParser.class);


    public enum LearningDataType {
        UNSUPERVISED,
        SUPERVISED,
        SUPERVISED_WITH_REGIONS
    }


    //    private Map<String, List<RegionWord>> regions = new HashMap<>();
  // Filenames, with zero or more class/region records
//  private Map<String, List<Pair<String, RegionWord>>> regions = new HashMap<>();
//  private Map<String, List<LearningRecord>> regions = new HashMap<>();
  List<LearningRecord> records = new ArrayList<>();


  private Set<String>  filePaths = new HashSet<>();

  // Track annotations by filename/class ID, with zero or more regions per key pair:
  private HashMap<Pair<String, String>, List<RegionWord>> annotations = new HashMap<>();

  // We support files that have one of these formats:
  // 1: <audio_path>
  // 2: <audio_path> <class_id>
  // 3: <audio_path> <class_id> <start> <end>

  // True depending on column format of file:
//  private boolean filesOnly = false;
//  private boolean classes = false;
//  private boolean regionsAndClasses = false;

  private LearningDataType dataType = null;

  public LearningParser() {}

  public boolean isValid() {
    return !filePaths.isEmpty();
  }

  public boolean isUnsupervised(){
      return dataType == LearningDataType.UNSUPERVISED;
  }

  public boolean hasClasses() {
    return dataType == LearningDataType.SUPERVISED_WITH_REGIONS | dataType == LearningDataType.SUPERVISED;
  }

  public boolean hasRegions() {
    return dataType == LearningDataType.SUPERVISED_WITH_REGIONS;
  }

 /* public boolean hasRegionsAndClasses() {
    return regionsAndClasses;
  }*/

  public Collection<String> getFilenames() {
    return filePaths;
  }

    /**
     * Return all class/region annotations matching the audio filename/id
     *
     * @param filename
     *
     * @return a list of annotations for the specified audio filename
     */
  public Map<String, List<RegionWord>> getAnnotations(String filename){

      Map<String, List<RegionWord>> rtn = new HashMap<>();
      if(!isValid() || isUnsupervised()){
          // no class/region annotations
          return  rtn;
      }

      for(Pair<String, String> key : annotations.keySet()){
          if(key.getFirst().equals(filename)){

              List<RegionWord> regions = null;
              if(rtn.containsKey(key.getSecond())){
                  regions = rtn.get(key.getSecond());
              }
              else {
                  regions = new ArrayList<>();
                  rtn.put(key.getSecond(), regions);
              }
              regions.addAll(annotations.get(key));
          }
      }

      return rtn;
  }


 /* public List<RegionWord> getRegions(String filename) {

    if (isRegionsAndClasses()) {
      return regions.get(DEFAULT_FILE_KEY);
    } else {
      if (regions.containsKey(filename)) {
        return regions.get(filename);
      }

      return new ArrayList<>();
    }
  }*/


 public LearningDataType getDataType(){
     return dataType;
 }



  public boolean parse(String filePath) {

    String line;

    try {
        Path fp = CommonUtils.resolvePath(filePath);

      BufferedReader reader = new BufferedReader(new FileReader(fp.toFile()));
      boolean firstPass = true;
        // Get the system env variables just in case
        Map<String, String> envMap = System.getenv();

      while ((line = reader.readLine()) != null) {

        String[] splits = line.split("\\s+");

        // columns should be consistent

        String filename = null;
        String classID = null;
        int startIndex = -1;
        int endIndex = -1;

        // Validate, we support 1, 2, and 4 columns
        if (splits.length < 1) {
          log.warn("Invalid input line '{}' in file '{}'", line, filePath);
          continue;
        } else if (splits.length == 1) { // 1 column
          filename = splits[0];
          if (firstPass){
              dataType = LearningDataType.UNSUPERVISED;
          }
          else if (dataType != LearningDataType.UNSUPERVISED){
              // invalid line input
              log.warn("Invalid unsupervised input line '{}' in file '{}'", line, filePath);
              continue;
          }

          // No region
        } else if (splits.length == 2) { // 2 column
          filename = splits[0];
          classID = splits[1];
            if (firstPass){
                dataType = LearningDataType.SUPERVISED;
            }
            else if (dataType != LearningDataType.SUPERVISED){
                // invalid line input
                log.warn("Invalid supervised input line '{}' in file '{}'", line, filePath);
                continue;
            }

        } else if (splits.length == 3 || splits.length > 4) { // 3 or more than 4 columns is invalid
          log.warn("Invalid input line '{}' in file '{}'.  Too many columns", line, filePath);
          continue;
        } else { // 4 columns
          // regionsAndClasses = true;
          filename = splits[0];
          classID = splits[1];
          startIndex = 2;
          endIndex = 3;
            if (firstPass){
                dataType = LearningDataType.SUPERVISED_WITH_REGIONS;
            }
            else if (dataType != LearningDataType.SUPERVISED_WITH_REGIONS){
                // invalid line input
                log.warn("Invalid supervised with regions input line '{}' in file '{}'", line, filePath);
                continue;
            }
        }

        if (!Files.exists(Paths.get(filename))) {
            String resolvedPath = CommonUtils.expandEnvVars(filename, envMap);
            Path rfp = CommonUtils.resolvePath(resolvedPath);
            if (!Files.exists(rfp)) {
                log.warn("Input file '{}'  does not exist. Ignoring", filePath);
                continue;
            }
            filename = rfp.toString();
        }
          filePaths.add(filename);


        if(firstPass){
          firstPass = false;
        }


          Pair<String, String> key = new Pair<>(filename, classID);
        if (startIndex >= 0) {
          try {
              List<RegionWord> regions = null;
              if (!annotations.containsKey(key)) {
                  regions = new ArrayList<>();
                  annotations.put(key, regions);
              }
              else {
                  regions = annotations.get(key);
              }

            double start = Double.parseDouble(splits[startIndex]);
            double end = Double.parseDouble(splits[endIndex]);
            // We assume input time is in seconds, but we need to convert to milliseconds
            regions.add(new RegionWord((int) (start * 1000), (int) (end * 1000)));

          } catch (NumberFormatException e) {
            log.warn("Invalid regions in line '{}', values must be in seconds", line);
            // continue;
          }
        }
        else if (null != classID) {
            annotations.put(key, new ArrayList<>());
        }

      }
    } catch (Exception e) {
      log.error("Failed to parse input file '{}' because: {}", filePath, e.getMessage(), e);
      return false;
    }
    return !filePaths.isEmpty();
  }
}
