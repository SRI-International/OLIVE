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
 * Utility to parse an input file for use with a MT plugin.  Rules TBD...
 * {ul>}
 *     <li>Currenlty each line is assumed to be a unique score request</li>
 * </ul>
 *
 */
public class TextParser {

    private static Logger log = LoggerFactory.getLogger(TextParser.class);
    private static final String DEFAULT_FILE_KEY = "DEFAULT_FILE_KeY";


    private List<String> sentences = new ArrayList<>();


    public boolean isValid(){
        return !sentences.isEmpty();
    }


    /**
     * Return the list of sentences to translate from the input file.
     * @return zero or more sentences in the input
     */
    public List<String> getSentences(){
        return sentences;
    }

    public TextParser() {
    }

    public boolean parse(String  filePath){


        Path fp = CommonUtils.resolvePath(filePath);
            String line;
            // Get the system env variables just in case
            Map<String, String> envMap = System.getenv();

            try {
                BufferedReader reader = new BufferedReader(new FileReader(fp.toFile()));
                while ((line = reader.readLine()) != null) {

                    sentences.add(line);

                }
            } catch (Exception e) {
                log.error("Failed to parse input file '{}' because: {}", fp.toString(), e.getMessage(), e);
                return false;
            }
            return !sentences.isEmpty();
        }






}
