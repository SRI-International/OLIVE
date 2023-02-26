package com.sri.speech.olive.api.utils.parser.pem;

import com.sri.speech.olive.api.utils.ChannelClassPair;
import com.sri.speech.olive.api.utils.CommonUtils;
import com.sri.speech.olive.api.utils.RegionWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Utility to parse all PEM records from a file;
 */
public class PemParser {

    private static Logger log = LoggerFactory.getLogger(PemParser.class);

    // filename-><channel:className> --> regions
//    private Map<String, Map<Pair<String, String>, List<RegionWord>>> regions = new HashMap<>();
    private Map<String, Map<ChannelClassPair, List<RegionWord>>> regions = new LinkedHashMap<>();

    public  PemParser(){

    }

    public  Map<String, Map<ChannelClassPair, List<RegionWord>>>   getChannelRegions(){
        return  regions;
    }

    /**
     *
     * @return the file/channel/class regions as a set of PemRecords, where there is a record for each region
     */
    public Collection<PemRecord> getRegions(){

        List<PemRecord> records = new ArrayList<>();
        for (String sourceID : regions.keySet()){
            for(ChannelClassPair key : regions.get(sourceID).keySet()){
                Integer channel = key.getChannel();
                String className  = key.getClassID();

                for(RegionWord word : regions.get(sourceID).get(key)){
                    try {
                        records.add(new PemRecord(sourceID, channel.toString(), className, word.getStartTimeSeconds(), word.getEndTimeSeconds()));
                    } catch (PemException e) {
                        log.warn("Invalid pem record: {}", e.getMessage());
                    }
                }

            }
        }

        return records;
    }


    public boolean isValid(){
        return !regions.isEmpty();
    }

    public boolean parse(String filePath){

        String line;

        try {
            // Get the system env variables just in case
            Map<String, String> envMap = System.getenv();
            BufferedReader reader = new BufferedReader(new FileReader(filePath));

            while ((line = reader.readLine()) != null) {

                // Line should be separated by spaces
                String[] splits = line.split("\\s+");

                // columns should be constant
                if(splits.length != 5){
                    log.warn("Invalid PEM input line '{}' in file '{}'.  Skipping line since it contains {} columns instead of 5", line, filePath, splits.length);
                    continue;
                }

                // Extract values from the line: filename (id), channel number, class label, start time, end time
                String id       = splits[0];
                String ch       = splits[1];
                String label    = splits[2];

                // Validate the path
                if (!Files.exists(Paths.get(id))){
                    // That path couldn't be found try to replace any environmenat variables in it and try again
                    String resolvedPath = CommonUtils.expandEnvVars(id, envMap);
                    if (!Files.exists(Paths.get(resolvedPath))) {
                        log.warn("Input file '{}'  does not exist. Ignoring line: {}", id, line);
                        continue;
                    }
                    id = resolvedPath;
                }


                Double startSec = null;
                Double endSec   = null;
                try {
                    startSec = Double.parseDouble(splits[3]);
                    endSec = Double.parseDouble(splits[4]);
                } catch (NumberFormatException e) {
                    log.warn("Invalid PEM input line '{}' in file '{}'.  Start region '{}' and/or end region'{}' is not in seconds.  Skipping line", line, filePath, splits[3], splits[4]);
                    continue;
                }

                // Lets do some minor validation
                if(startSec >= endSec){
                    log.warn("Invalid PEM input line '{}' in file '{}'.  Start region '{}' must be less than end region '{}'.  Skipping line", line, filePath, startSec, endSec);
                    continue;
                }

                if(startSec < 0){
                    log.warn("Invalid PEM input line '{}' in file '{}'.  Start region '{}' must be greater than or equal to 0.  Skipping line", line, filePath, startSec);
                    continue;
                }

                if(endSec < 0){
                    log.warn("Invalid PEM input line '{}' in file '{}'.  End region '{}' must be greater than or equal to 0.  Skipping line", line, filePath, endSec);
                    continue;
                }

                if(regions.containsKey(id)){

                    Map<ChannelClassPair, List<RegionWord>> rec = regions.get(id);
//                    Pair<String, String> key = new  Pair<>(ch, label);
                    ChannelClassPair key = new ChannelClassPair(Integer.parseInt(ch), label);

                    if(rec.containsKey(key)){
                        // Verify region is valid
                        RegionWord newWord = new RegionWord( (int)(startSec*1000), (int)(endSec*1000));
                        List<RegionWord> words = rec.get(key);


                        // Check and validate overlap
                        boolean insert = true;
                        for(RegionWord rw : words) {

                            if (newWord.getEndTimeSeconds() < rw.getStartTimeSeconds() && newWord.getEndTimeSeconds() > rw.getEndTimeSeconds()) {
                                // existing record is contained within new record bounds
                                // replace existing record with the new record
                                rw.setStartTimeSeconds(startSec);
                                rw.setEndTimeSeconds(endSec);
                                insert = false;
                                break;
                            } else if (newWord.getStartTimeSeconds() >= rw.getStartTimeSeconds() && newWord.getEndTimeSeconds() <= rw.getEndTimeSeconds()) {
                                // this new region is contained within this record
                                insert = false;
                                break;
                            } else if (newWord.getStartTimeSeconds() < rw.getStartTimeSeconds() && newWord.getEndTimeSeconds() > rw.getStartTimeSeconds() && newWord.getEndTimeSeconds() <= rw.getEndTimeSeconds()) {
                                //new record overlaps existing record at the start
                                rw.setStartTimeSeconds(startSec);
                                insert = false;
                                break;
                            } else if (newWord.getStartTimeSeconds() >= rw.getStartTimeSeconds() && newWord.getStartTimeSeconds() < rw.getEndTimeSeconds() && newWord.getEndTimeSeconds() > rw.getEndTimeSeconds())
                            {
                                // new record overlaps existing record at the end
                                rw.setEndTimeSeconds(endSec);
                                insert = false;
                                break;
                            }
                        }

                        if (insert){
                            words.add(newWord);
                        }


                        // Finally sort regions after every insert - could be more efficient but I'm in a hurry....
                        words.sort(new Comparator<RegionWord>() {
                            @Override
                            public int compare(RegionWord w1, RegionWord w2) {

                                int n = Double.compare(w1.getStartTimeSeconds(), w2.getStartTimeSeconds());
                                if (n != 0) return n;
                                return Double.compare(w1.getEndTimeSeconds(), w2.getEndTimeSeconds());
                            }
                        });
                    }
                    else {
                        // New channel/class pair
                        List<RegionWord> word  = new ArrayList<>();
                        word.add(new RegionWord( (int)(startSec*1000), (int)(endSec*1000)));
                        rec.put(key, word);

                    }


                }
                else {
                    // New record, just add it without any fuss
                    Map<ChannelClassPair, List<RegionWord>> rec = new LinkedHashMap<>();
                    List<RegionWord> word  = new ArrayList<>();
                    word.add(new RegionWord( (int)(startSec*1000), (int)(endSec*1000)));
                    // TODO SUPPORT CHANNEL AS A LIST (1,2)
                    rec.put(new ChannelClassPair(Integer.parseInt(ch), label), word);
                    regions.put(id, rec);
                }

            }
        } catch (Exception e) {
            log.error("Failed to parse PEM input file '{}' because: {}", filePath, e.getMessage(), e);
            return false;
        }

        return !regions.isEmpty();

    }

}
