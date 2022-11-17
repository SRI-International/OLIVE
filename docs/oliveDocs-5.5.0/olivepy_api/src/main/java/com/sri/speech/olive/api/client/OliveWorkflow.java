package com.sri.speech.olive.api.client;


import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Server;
import com.sri.speech.olive.api.utils.*;
import com.sri.speech.olive.api.utils.parser.ClassParser;
import com.sri.speech.olive.api.utils.parser.RegionParser;
import com.sri.speech.olive.api.utils.parser.pem.PemParser;
import com.sri.speech.olive.api.utils.reports.*;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class OliveWorkflow {

    private static Logger log = LoggerFactory.getLogger(OliveWorkflow.class);
    private static final int        TIMEOUT                = 10000;
    private static final String     DEFAUL_SERVERNAME   = "localhost";
    private static final int        DEFAULT_PORT           = 5588;

    private final static AtomicInteger seq = new AtomicInteger();

    // Command line options
    private static final String helpOptionStr = "h";

    //  Is that even necessary?

    private static final String audioOptionStr      = "a";
    private static final String audioListOptionStr  = "audio_list"; // a file containing regions

    private static final String printOptionStr      = "tasks";
    private static final String enhanceOptionStr    = "tasks"; // print the
    private static final String channelOptionStr    = "channel"; //
    private static final String thresholdOptionStr  = "threshold"; // scoring threshold
    // Use PEM file for regions/options?
    private static final String annotationOptionStr = "annotation"; // a file containing regions
    private static final String pemOptionStr        = "pem";  // get channel/class annotations from a PEM file


    private static final String outputOptionStr     = "output"; // the directory to write any output
    private static final String classesOptionStr    = "class_ids"; // the subset of classes to use when scoring

    private static final String oliveServerOptionStr = "s"; // server
    private static final String portOptionStr       = "p";  // port
    private static final String timeoutOptionStr    = "t";  // timeout

    // Option/properties file:
    private static final String optionOptionStr         = "options";

    //
    private static final String workflowAnalysisOptionStr = "analysis";

    // Common options
//    private static final String serializeOptionStr    = "serialized";  // send the raw file as a buffer not the decoded audio (DEFAULT)
    private static final String pathOptionStr         = "path";  // send the file path, not an audio buffer
    private static final String decodedOptionStr      = "decoded";  // send the audio file as a decoded sample buffer (PCM-16).  Audio file must be a wav file



    // Not yet supported, but provide an alternate speaker/class id when importing an enrollment model:
    //static final String enrollmentOptionStr     = "enrollment"; // enrollment class name (for importing)

    private static String oliveSeverName;
    private static int olivePort;

    private static String outputDirName;
    private static int timeout;

    private static boolean printTasks = false;
    // Do we need a verbose mode?
    private static boolean printVerbose     = false;

    private static ClientUtils.AudioTransferType transferType;
    private static Integer channelNumber = -1;  // by default, assume non-stereo file(s) so a channel number is not specified, but if is stereo then merge it
    private static int thresholdNumber = 0;
    private static boolean applyThresholdToFrameScores = false;

//    private static RegionParser regionParser = new RegionParser();
//    private static List<String> audioFiles = new ArrayList<>();


    // options
    private static boolean workflowRequest; // hack

    private static String optionPropFilename;
    private static List<Pair<String, String>> propertyOptions = new ArrayList<>();
    private static List<String> classIDs = new ArrayList<>();

    private TaskReportManager rptMgr = new TaskReportManager();


    public interface ReportGenerator {

        AbstractReportWriter createWriter(String audioFilename);

    }

    // Stores requested tasks
    static final List<TaskType> taskList = new ArrayList<>();


    // Filenames -> * <channel/class> -> * regions
    // We only have channel/class IDs if input is from a PEM file
    private static Map<String, Map<ChannelClassPair, List<RegionWord>>> fileRegions = new HashMap<>();



    // Helper class to track score request/results
    public class AnalysisResult {

        private int id;
        boolean isError;
        TaskType type;
        String audioFilename;
        Server.Result result;

        public AnalysisResult(int id, TaskType type, boolean error, String audioFilename, Server.Result result){
            this.id = id;
            this.type = type;
            this.isError = error;
            this.audioFilename = audioFilename;
            this.result = result;

        }

        public TaskType getType(){
            return type;
        }

        public boolean isError(){
            return isError;
        }

        public int getId(){
            return id;
        }

        public String getAudioFilename(){return  audioFilename;}

        public Server.Result getResult() {return result;}
    }

    // Async request add result to this queue
    public BlockingQueue<AnalysisResult> queue = new ArrayBlockingQueue<>(4);

    private Map<TaskType, Integer> analysisSuccess = new HashMap<>();
    private Map<TaskType, List<AnalysisResult>> analysisSuccessStatus = new HashMap<>();
    private Map<TaskType, Integer> analysisFailures = new HashMap<>();

    /**
     * Main execution point
     *
     */
    public static void main(String[] args)  {

        parseCommandLineArgs(args);

        // Setup the connection to the (scenic) server
        Server server = new Server();
        server.connect("java_workflow_client", oliveSeverName,
                olivePort,
                olivePort +1,
                TIMEOUT/100);    // may need to adjust timeout
        // wait for the connection
        long start_t = System.currentTimeMillis();
        while (!server.getConnected().get() && System.currentTimeMillis() - start_t < TIMEOUT) {
            try {
                synchronized (server.getConnected()) {
                    server.getConnected().wait(TIMEOUT);
                }
            } catch (InterruptedException e) {
                // Keep waiting
            }
        }

        if (!server.getConnected().get()) {
            log.error("Unable to connect to the OLIVE server: {}", oliveSeverName);
             System.err.println("Unable to connect to server");
        }

        // Perform task(s)
        OliveWorkflow sc = new OliveWorkflow();
        sc.handleRequests(server);




    }

    private static String getShortProgramName() {
        return "OliveAnalyze";
    }

    private static CommandLine parseCommandLineArgs(String args[]) {

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        Options options = new Options();

        options.addOption(helpOptionStr,    false, "Print this help message");
        options.addOption(oliveServerOptionStr,  "server",   true, "Scenicserver hostname. Default is " + DEFAUL_SERVERNAME);
        options.addOption(portOptionStr,    "port",     true, "Scenicserver port number. Defauls is " + DEFAULT_PORT);
        options.addOption(Option.builder().longOpt(outputOptionStr).desc("Write any output to DIR, default is ./").hasArg().build());
        options.addOption(Option.builder().longOpt(optionOptionStr).desc("options from FILE ").hasArg().build());
        options.addOption(Option.builder().longOpt(workflowAnalysisOptionStr).desc("Request a workflow").build());


        options.addOption(timeoutOptionStr, "timeout",  true, "timeout (in seconds) when waiting for server response.  Default is 10 seconds");

        // Audio input options
        options.addOption(audioOptionStr,     "audio",      true, "NAME of the audio file ");
        options.addOption(Option.builder().longOpt(audioListOptionStr).desc("Use an input list FILE having multiple filenames/regions").hasArg().build());
        options.addOption(Option.builder().longOpt(pemOptionStr).desc("Use input from a pem FILE having multiple filenames/channels/class/regions").hasArg().build());

        // Long only options:
        options.addOption(Option.builder().longOpt(classesOptionStr).desc("Use Class(s) from FILE for scoring.  Each line in the file contains a single class, including any white space").hasArg().build());


        options.addOption(Option.builder().longOpt(printOptionStr).desc("Print analysis tasks for the workflow.  Optionally add 'verbose' as a print option to print full task details").optionalArg(true).hasArg().build());

        // audio processing
        options.addOption(Option.builder().longOpt(decodedOptionStr).desc("Send audio file as a buffer of decoded PCM16 samples (serialized by default)").build());
        options.addOption(Option.builder().longOpt(pathOptionStr).desc("Send audio file path instead of a buffer (serialized by default).  Server and client must share a filesystem to use this option").build());
        options.addOption(Option.builder().longOpt(channelOptionStr).desc("Process stereo files using channel NUMBER").hasArg().build());
        options.addOption(Option.builder().longOpt(thresholdOptionStr).desc("Apply threshold NUMBER when scoring").hasArg().build());



        // Parse options
        try {
            cmd = parser.parse(options, args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            printUsageAndExit(options);
        }

        // check if help is needed
        if (cmd.hasOption(helpOptionStr)) {
            printUsageAndExit(options);
        }

        if (cmd.hasOption(workflowAnalysisOptionStr)) {
            workflowRequest = true;
        }

        // check if there is a properties file
        if (cmd.hasOption(optionOptionStr)) {
            optionPropFilename = cmd.getOptionValue(optionOptionStr);

            if (!Files.exists(Paths.get(optionPropFilename).toAbsolutePath())){
                System.err.println("ERROR: The requested properties file '" + optionPropFilename + "' does not exist");
                printUsageAndExit(options);
            }

            try {
                Properties properties = new Properties();
                properties.load(new FileInputStream(optionPropFilename));

                for(String name: properties.stringPropertyNames()){
                    propertyOptions.add(new Pair<>(name, properties.getProperty(name)));
                }

            } catch (IOException e) {
                System.err.println("ERROR: failed to open the options file  '" + optionPropFilename + "'");
                printUsageAndExit(options);
            }

        }

        // check if we should print plugin names
        if (cmd.hasOption(printOptionStr)) {

            String val = cmd.getOptionValue(printOptionStr);

            if(null != val){
                printVerbose = true;
            }

            printTasks = true;

        }
       /* // check if we should print plugin names
        if (printPlugins && cmd.hasOption(printClassesOptionStr)) {
            printClasses = true;

        }*/

        if (cmd.hasOption(outputOptionStr)) {
            outputDirName = cmd.getOptionValue(outputOptionStr);
            if (!Files.isDirectory(Paths.get(outputDirName))){
                // Create the output dir
                try {
                    Files.createDirectory(Paths.get(outputDirName));
                } catch (IOException e) {
                    System.err.println("ERROR: Output directory '" + outputDirName + "' could not be created");
                }
            }
        }else {
            outputDirName = "";
        }

        // support his??
        if (cmd.hasOption(classesOptionStr)) {
            String listInputFilename = cmd.getOptionValue(classesOptionStr);
            ClassParser classParser = new ClassParser();
            classParser.parse(listInputFilename);
            if (!classParser.isValid()){
                System.err.println("Invalid class input file: " + listInputFilename);
                printUsageAndExit(options);
            }
            classIDs.addAll(classParser.getClasses());
        }

        // check if we are starting scenicserver
        if (cmd.hasOption(oliveServerOptionStr)){
            oliveSeverName = cmd.getOptionValue(oliveServerOptionStr);
        }
        else {
            oliveSeverName = DEFAUL_SERVERNAME;
        }

        if (cmd.hasOption(portOptionStr)){
            try {
                olivePort = Integer.parseInt(cmd.getOptionValue(portOptionStr));
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: '" + cmd.getOptionValue(portOptionStr) + "' ");
                printUsageAndExit(options);
            }
        }
        else {
            olivePort = DEFAULT_PORT;
        }

        if (cmd.hasOption(timeoutOptionStr)){
            try {
                // get timeout and convert to MS
                timeout = Integer.parseInt(cmd.getOptionValue(timeoutOptionStr)) * 1000;
            } catch (NumberFormatException e) {
                System.err.println("Invalid timeout: '" + cmd.getOptionValue(timeoutOptionStr) + "' ");
                printUsageAndExit(options);
            }
        }
        else {
            timeout = TIMEOUT;
        }

        boolean requireAudioInput = false;

        // check if using alternate config file

        if (cmd.hasOption(workflowAnalysisOptionStr)) {
            taskList.add(TaskType.WORKFLOW_ANALYSIS);
            requireAudioInput = true;
        }


        // Support channel number?
        if (cmd.hasOption(channelOptionStr)) {
            try {
                channelNumber = Integer.parseInt(cmd.getOptionValue(channelOptionStr));
            } catch (NumberFormatException e) {
                System.err.println("Ignoring non integer channel number: " + cmd.getOptionValue(channelOptionStr));
            }
        }

        boolean sendAudioAsSerializedBuffer = true;
        boolean sendAudioAsPath = false;

        if (cmd.hasOption(decodedOptionStr)) {
            sendAudioAsSerializedBuffer = false;
        }

        if (cmd.hasOption(pathOptionStr)) {
            sendAudioAsPath = true;
        }

        if(sendAudioAsPath && sendAudioAsSerializedBuffer){
            System.err.println("Both the decoded audio and send audio as a path arguments were specified, but only one can be specified.  Remove one argument and run again ");
            printUsageAndExit(options);
        }

        // Select the audio transfer type:
        if (sendAudioAsPath) {
            transferType = ClientUtils.AudioTransferType.SEND_AS_PATH;
        }
        else if (sendAudioAsSerializedBuffer){
            transferType = ClientUtils.AudioTransferType.SEND_SERIALIZED_BUFFER;
        }
        else {
            transferType = ClientUtils.AudioTransferType.SEND_SAMPLES_BUFFER;
        }


        // Check for a wave, audio vector, or enrollment vector input
        if(cmd.hasOption(audioListOptionStr)){
            String listInputFilename = cmd.getOptionValue(audioListOptionStr);
            RegionParser regionParser = new RegionParser();
            regionParser.parse(listInputFilename);
            if (!regionParser.isValid()){
                System.err.println("Invalid list input file: " + listInputFilename);
                printUsageAndExit(options);
            }

            for (String filename : regionParser.getFilenames() ){
                ChannelClassPair ccp = new ChannelClassPair(channelNumber);
                Map<ChannelClassPair, List<RegionWord>> r = new HashMap<>();
                r.put(ccp, regionParser.getRegions(filename));
                fileRegions.put(filename, r);
            }
//            audioFiles.addAll(regionParser.getFilenames());   // note - this might be empty but will have regions for wavOptionStr

        }
        else  if (cmd.hasOption(pemOptionStr)) {
            String pemFileName = cmd.getOptionValue(pemOptionStr);
            if (!Files.exists(Paths.get(pemFileName).toAbsolutePath())) {
                System.err.println("ERROR: PEM file '" + pemFileName + "' does not exist");
                printUsageAndExit(options);
            }

            PemParser pp = new PemParser();
            if (!pp.parse(pemFileName)) {
                System.err.println("ERROR: PEM file '" + pemFileName + "' contains no valid records");
                printUsageAndExit(options);
            }

            fileRegions = pp.getChannelRegions();

        }
        else if (cmd.hasOption(audioOptionStr) ) {
            String audioFileName = cmd.getOptionValue(audioOptionStr);
            // Make sure file exists
            if (!Files.exists(Paths.get(audioFileName).toAbsolutePath())){
                System.err.println("ERROR: Audio file '" + audioFileName + "' does not exist");
                printUsageAndExit(options);
            }

            // File only, not channel/class/regions?
//            audioFiles.add(audioFileName);
            ChannelClassPair ccp = new ChannelClassPair(channelNumber);
            Map<ChannelClassPair, List<RegionWord>> r = new HashMap<>();
            r.put(ccp, new ArrayList<>());
            fileRegions.put(audioFileName, r);

        }


        // Check that tasks require audio/vector input  have such an input
        if(requireAudioInput  ){
            if(fileRegions.keySet().size() == 0){
                System.err.println(String.format("Requested task(s) '%s' required an audio input.  Add a wave file name (or audio vector name - if supported)", taskList.toString()));
                printUsageAndExit(options);
            }



        }

        return cmd;
    }

    private static void printUsageAndExit(Options options)
    {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( getShortProgramName(), options );

        System.exit(1);
    }

    public void handleRequests(Server server){

        try {

            // First load the workflow definition

            // Request the server actualizes the workflow

            // Use our OliveWorkflow for any requested tasks

            if (printTasks){
                // todo print the (anlysis?) tasks
            }

            // Next see if we need to preload a plugin


            int numAsyncRequest = 0;

            // First, package the audio
            List<Olive.Audio> audios = new ArrayList<>();
            int numErrors = 0;

            for(String audioFileName : fileRegions.keySet()) {
//                Map<ChannelClassPair, List<RegionWord>> channelClass = fileRegions.get(audioFileName);
                for(ChannelClassPair ccp : fileRegions.get(audioFileName).keySet()){
                    // Create audio objects
                    try {
                        audios.add(ClientUtils.createAudioFromFile(audioFileName, ccp.getChannel(), transferType, fileRegions.get(audioFileName).get(ccp)).build());
                    }
                    catch (IOException | UnsupportedAudioFileException e) {
                        log.debug("Audio error detail: ",e);
                        log.error("Unable to process file {} because: {}", audioFileName, e.getMessage());
                        numErrors++;
                    }

                }

            }

            boolean syncTasks = false;
            // Next process any request that don't require audio input
            if (taskList.contains(TaskType.WORKFLOW_ANALYSIS)) {
                // todo workflow analysis
                numAsyncRequest++;
            }

            if (taskList.contains(TaskType.WORKFLOW_ENROLL)) {
                // sync call
                // todo implement
                syncTasks = true;
            }

            if (taskList.contains(TaskType.WORKFLOW_ADAPT)) {
                // todo implement
                numAsyncRequest++;
            }




            int numTimeouts = 0;

            if(numAsyncRequest == 0 && ! syncTasks){
                System.err.println("OliveWorkflow finished without completing any workflow tasks.");
                System.exit(1);
            }

            while(numAsyncRequest > 0){
                // TOOD HANDLE OUTPUT - WRITE TO OUTPUT
                AnalysisResult sr = queue.poll(timeout, TimeUnit.MILLISECONDS);

                if(null == sr) {
                    if (numTimeouts++ > 3) {
                        System.err.println("Timeout exceeded waiting for response.");
                        numErrors++;
                        break;
                    }
                }
                else{
                    System.out.println(String.format("Received %s result for task: %s and id: %s", sr.isError ? "unsuccessful" : "successful", sr.getType().toString(), sr.getId()));
                    numAsyncRequest--;

                    if(sr.isError){
                        analysisFailures.merge(sr.getType(), 1, Integer::sum);
                    }
                    else{
                        analysisSuccess.merge(sr.getType(), 1, Integer::sum);
                        List<AnalysisResult> list  = analysisSuccessStatus.get(sr.getType());
                        if(null == list){
                            list = new ArrayList<>();
                            list.add(sr);
                            analysisSuccessStatus.put(sr.getType(), list);
                        }
                        else {
                            list.add(sr);
                        }

                    }
                }
            }

            rptMgr.closeAllReports();

            System.out.println("");
            if(numErrors>0 || analysisFailures.size() > 0 ){

                for(TaskType t : analysisFailures.keySet()){
                    if(!analysisSuccess.containsKey(t)){
                        // All analysis requests for this type failed
                        // TODO handle failure
//                        rptMgr.
                        System.err.println("No results for analysis type: " + t);
                    }
                }
                // if
                if(analysisFailures.containsKey(TaskType.REGION_SCORE)
                        && !analysisSuccess.containsKey(TaskType.REGION_SCORE)){

                    RegionScorerReportWriter arw = new RegionScorerReportWriter(outputDirName, "");
                    arw.addError("No regions found due to errors");


                }

                System.out.println(String.format("OliveAnalyze finished with %d errors.  Exiting...", numErrors));

            }
            else {

                // Often a region scorer will return no results
                int rsCount = 0;
                if (analysisSuccessStatus.containsKey(TaskType.REGION_SCORE)) {
                  for (AnalysisResult ar : analysisSuccessStatus.get(TaskType.REGION_SCORE)) {
                    rsCount += ((Olive.RegionScorerResult) ar.result.getRep()).getRegionCount();
                  }
                  if (0 == rsCount) {
                    RegionScorerReportWriter arw = new RegionScorerReportWriter(outputDirName, "");
                    arw.addError("No regions found");
                    arw.close();
                  }
                }

                System.out.println("OliveAnalyze finished.  Exiting...");
            }


            System.exit(0);

        } catch (Exception e) {
            log.error("\nFatal Error:", e);
            System.out.println("OliveAnalyze fatal error.  Exiting...");
            System.exit(1);
        }
    }






    private String formatPluginErrorMsg(String pluginName, String domainName, Olive.TraitType type){


        return String.format("No plugin-domain found having trait %s, plugin name: '%s' and domain: '%s' ", type.toString(), null == pluginName ? "*" : pluginName, null == domainName ? "*" : domainName);
    }







}
