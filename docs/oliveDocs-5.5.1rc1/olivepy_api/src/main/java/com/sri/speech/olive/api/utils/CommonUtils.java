package com.sri.speech.olive.api.utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher;

public class CommonUtils {


    private static final Logger logger = LoggerFactory.getLogger(CommonUtils.class);

    public enum OSType {
        Linux,
        Windows,
        MacOS,
        Other
    }

    public static OSType getOS(){

        String osName = System.getProperty("os.name").toLowerCase();
        //logger.info("OS name: {}", osName);

        if(osName.contains("mac") || osName.contains("darwin")){
            return OSType.MacOS;
        }
        else if(osName.contains("nux")){
            return OSType.Linux;
        }
        else if(osName.contains("win")){
            return OSType.Windows;
        }

        return OSType.Other;
    }


    public static Path resolvePath(String filename){
        Path fp = Paths.get(filename);
        if (Files.exists(fp)){
            // Seems to be a valid path.. don't change
            return  fp;
        }
        // Might be a relative home path...
        // "^~" or "~"?
        String reslovedPath = filename.replaceFirst("~", Matcher.quoteReplacement(System.getProperty("user.home"))).trim();
//                filename.replaceFirst("^~", System.getProperty("user.home"))

        if (Files.exists(Paths.get(reslovedPath))){
            return Paths.get(reslovedPath);
        }
        // FIXME THROW and exception or just return the default filepath?
        return fp;
    }

    public static String expandEnvVars(String text, Map<String, String> envMap) {
        for (Map.Entry<String, String> entry : envMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (getOS() == OSType.Windows){
                // Look for %{OLIVE_FOO} instead of ${OLIVE_FOO}
                text = text.replaceAll("%\\{" + key + "}", value);
            }
            else {
                text = text.replaceAll("\\$\\{" + key + "}", value);
            }
        }
        return text;
    }
}
