package com.sri.speech.olive.api.client;


import com.google.protobuf.InvalidProtocolBufferException;
import com.sri.speech.olive.api.Server;
import com.sri.speech.olive.api.Olive;
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple example of using the Scenic API to make SAD, SID, LID, and analysis requests.
 * Both asynchronous and synchronous callbacks from the OLIVE are demonstrated.
 *
 * No longer so simple...
 */
public class OliveAnalyze {

    private static Logger log = LoggerFactory.getLogger(OliveAnalyze.class);
    private static final int        TIMEOUT                = 10000;
    private static final String     DEFAUL_SERVERNAME   = "localhost";
    private static final int        DEFAULT_PORT           = 5588;

    private final static AtomicInteger seq = new AtomicInteger();

    // Command line options
    private static final String helpOptionStr = "h";

    //  Is that even necessary?
    private static final String sidOptionStr = "sid";
    private static final String lidOptionStr = "lid";
    private static final String kwsOptionStr = "kws";
    private static final String qbeOptionStr = "qbe";

    private static final String frameOptionStr      = "frame";
    private static final String globalOptionStr     = "global";
    private static final String regionOptionStr     = "region";
    private static final String compareOptionStr    = "compare";
    private static final String alignOptionStr      = "align";
    private static final String boxOptionStr        = "box";

    private static final String inputOptionStr = "i";
    private static final String audioVectorOptionStr = "v";  // Score from an audio vector
    private static final String domainOptionStr     = "domain";
    private static final String printOptionStr      = "print";
    private static final String pluginOptionStr     = "plugin";
    private static final String printClassesOptionStr = "includeclasses";
    private static final String unloadOptionStr = "r";
    private static final String enhanceOptionStr    = "enhance"; // audio 2 audio conversion
    private static final String vectorOptionStr     = "vector"; // vector conversion
    private static final String channelOptionStr    = "channel"; // vector conversion
    private static final String thresholdOptionStr  = "threshold"; // scoring threshold
    private static final String annotationOptionStr = "annotation"; // a file containing regions
//    private static final String pemOptionStr        = "pem";  // get channel/class annotations from a PEM file
    private static final String listOptionStr       = "input_list"; // a file containing regions
    private static final String outputOptionStr     = "output"; // the directory to write any output
    private static final String classesOptionStr    = "class_ids"; // the subset of classes to use when scoring
    private static final String healthOptionStr     = "status"; // print the current health and status of the server

    private static final String scenicOptionStr     = "s";
    private static final String portOptionStr       = "p";
    private static final String timeoutOptionStr    = "t";
    private static final String loadOptionStr       = "l";
    // Option/properties file:
    private static final String optionOptionStr         = "options";
    // Other
    private static final String shutdownOptionStr         = "shutdown";

    //
    private static final String workflowOptionStr         = "workflow";

    // Common options
    private static final String decodedOptionStr = "decoded";  // send the raw file as a buffer not the decoded audio
    private static final String pathOptionStr         = "path";  // send the file path, not an audio buffer

    // todo we might want to move this to a new utility
    private static final String getUpdateStatusOptionStr    = "update_status";  // get the update status
    private static final String applyUpdateOptionStr        = "apply_update";  // request a plugin update


    // Not yet supported, but provide an alternate speaker/class id when importing an enrollment model:
    //static final String enrollmentOptionStr     = "enrollment"; // enrollment class name (for importing)

    private static String oliveSeverName;
    private static int scenicPort;
    private static String audioVectorFileName;
    private static String removeSpeakerName;
    private static String domainName;
    private static String pluginName;
    private static String outputDirName;
    private static int timeout;
    private static boolean printPlugins     = false;
    private static boolean printVerbose     = false;
    private static boolean healthAndStatus  = false;

    private static ClientUtils.AudioTransferType transferType;
    private static Integer channelNumber = -1;  // by default, assume non-stereo file(s) so a channel number is not specified
    private static int thresholdNumber = 0;
    private static boolean applyThresholdToFrameScores = false;

    // options
    private static boolean shutdownServer;
    private static boolean workflowRequest; // hack

    private static String optionPropFilename;
    private static List<Pair<String, String>> propertyOptions = new ArrayList<>();
    private static List<String> classIDs = new ArrayList<>();

    private TaskReportManager rptMgr = new TaskReportManager();


    public interface ReportGenerator {

        AbstractReportWriter createWriter(String audioFilename);

    }
    //public Olive.TraitType[] learningTraits =  {Olive.TraitType.SUPERVISED_ADAPTER, Olive.TraitType.SUPERVISED_TRAINER, Olive.TraitType.UNSUPERVISED_ADAPTER };


    // Stores requested tasks
    static final List<TaskType> taskList = new ArrayList<>();


    // Filenames -> * <channel/class> -> * regions
    // We only have channel/class IDs if input is from a PEM file
    private static Map<String, Map<ChannelClassPair, List<RegionWord>>> fileRegions = new LinkedHashMap<>();



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
    private Map<TaskType,  List<AnalysisResult>> analysisFailures = new HashMap<>();

    /**
     * Main execution point
     *
     */
    public static void main(String[] args)  {

        parseCommandLineArgs(args);

        // Setup the connection to the (scenic) server
        Server server = new Server();
        server.connect("OliveAnalyze", oliveSeverName,
                scenicPort,
                scenicPort +1,
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
        OliveAnalyze sc = new OliveAnalyze();
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
        options.addOption(scenicOptionStr,  "server",   true, "Scenicserver hostname. Default is " + DEFAUL_SERVERNAME);
        options.addOption(portOptionStr,    "port",     true, "Scenicserver port number. Defauls is " + DEFAULT_PORT);
        options.addOption(Option.builder().longOpt(outputOptionStr).desc("Write any output to DIR, default is ./").hasArg().build());
        options.addOption(Option.builder().longOpt(optionOptionStr).desc("options from FILE ").hasArg().build());
        options.addOption(Option.builder().longOpt(shutdownOptionStr).desc("Request a clean shutdown of the server").build());
        options.addOption(Option.builder().longOpt(workflowOptionStr).desc("Request a workflow").build());


        options.addOption(timeoutOptionStr, "timeout",  true, "timeout (in seconds) when waiting for server response.  Default is 10 seconds");
        options.addOption(loadOptionStr,    "load",     false,"load a plugin now, must use --plugin and --domain to specify the plugin/domain to preload");
        options.addOption(unloadOptionStr,  "unload",   false,"unload a loaded plugin now, must use --plugin and --domain to specify the plugin/domain to unload" );

        // Audio input options
        options.addOption(inputOptionStr,     "input",      true, "NAME of the input file (audio/video/image as required by the plugin");
        // todo support? options.addOption(pemOptionStr,     "pem",      true, "NAME of the PEM file to use to read audio inputs");
        options.addOption(audioVectorOptionStr,     "vec",      true, "PATH to a serialized AudioVector, for plugins that support audio vectors in addition to wav files");
        options.addOption(Option.builder().longOpt(listOptionStr).desc("Use an input list FILE having multiple filenames/regions or PEM formatted").hasArg().build());
//        options.addOption(Option.builder().longOpt(pemOptionStr).desc("Use input from a pem FILE having multiple filenames/channels/class/regions").hasArg().build());

        // Long only options:

        options.addOption(Option.builder().longOpt(frameOptionStr).desc("Perform frame scoring analysis").build());
        options.addOption(Option.builder().longOpt(globalOptionStr).desc("Perform global scoring analysis").build());
        options.addOption(Option.builder().longOpt(regionOptionStr).desc("Perform region scoring analysis").build());
        options.addOption(Option.builder().longOpt(compareOptionStr).desc("Perform audio compare analysis.  Must specify the two files to compare using an input list file via the--list argument ").build());
        options.addOption(Option.builder().longOpt(alignOptionStr).desc("Perform audio alignment analysis.  Must specify the two files to compare using an input list file via the--list argument ").build());
        options.addOption(Option.builder().longOpt(boxOptionStr).desc("Perform bounding box  analysis.  Must specify an image or video input ").build());
        options.addOption(Option.builder().longOpt(classesOptionStr).desc("Use Class(s) from FILE for scoring.  Each line in the file contains a single class, including any white space").hasArg().build());


        options.addOption(Option.builder().longOpt(enhanceOptionStr).desc("Perform audio conversion (enhancement)").build());
        options.addOption(Option.builder().longOpt(vectorOptionStr).desc("Perform audio vectorization").build());

        // TODO MAKE PLUGIN REQUIRED?
        options.addOption(Option.builder().longOpt(domainOptionStr).desc("Use Domain NAME").hasArg().build());
        options.addOption(Option.builder().longOpt(pluginOptionStr).desc("Use Plugin NAME").hasArg().build());

        options.addOption(Option.builder().longOpt(printOptionStr).desc("Print all available plugins and domains.  Optionally add 'verbose' as a print option to print full plugin details including traits and classes").optionalArg(true).hasArg().build());
//        options.addOption(Option.builder().longOpt(printClassesOptionStr).desc("Print class names if also printing plugin/domain names.  Must use with --print option.  Default is to not print class IDs").build());
        options.addOption(Option.builder().longOpt(healthOptionStr).desc("Print the current status of the server").build());

        // audio processing
        options.addOption(Option.builder().longOpt(decodedOptionStr).desc("Send audio file as decoded PCM16 samples instead of sending as serialized buffer.  Input file must be a wav file").build());
        options.addOption(Option.builder().longOpt(pathOptionStr).desc("Send audio file path instead of a buffer.  Server and client must share a filesystem to use this option").build());
        options.addOption(Option.builder().longOpt(channelOptionStr).desc("Process stereo files using channel NUMBER").hasArg().build());
        options.addOption(Option.builder().longOpt(thresholdOptionStr).desc("Apply threshold NUMBER when scoring").hasArg().build());

        // Update options
        options.addOption(Option.builder().longOpt(applyUpdateOptionStr).desc("Request the plugin is update (if supported)").build());
        options.addOption(Option.builder().longOpt(getUpdateStatusOptionStr).desc("Get the plugin's update status ").build());


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

        if (cmd.hasOption(shutdownOptionStr)) {
            shutdownServer = true;
        }

        if (cmd.hasOption(workflowOptionStr)) {
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

            printPlugins = true;

        }
       /* // check if we should print plugin names
        if (printPlugins && cmd.hasOption(printClassesOptionStr)) {
            printClasses = true;

        }*/

        if (cmd.hasOption(healthOptionStr)) {
            healthAndStatus = true;

        }

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


        if (cmd.hasOption(classesOptionStr)) {
            // We use to get class IDs from the command line:
//            String[] classIDStrs = cmd.getOptionValue(classesOptionStr).split(",");
//            classIDs.addAll(Arrays.asList(classIDStrs));
            // But get 'em from file:
            String listInputFilename = cmd.getOptionValue(classesOptionStr);
            ClassParser classParser = new ClassParser();
            classParser.parse(listInputFilename);
            if (!classParser.isValid()){
                System.err.println("Invalid class input file: " + listInputFilename);
                printUsageAndExit(options);
            }
            classIDs.addAll(classParser.getClasses());
        }


        // check for a threshold
        if (cmd.hasOption(thresholdOptionStr)) {
            try {
                thresholdNumber = Integer.parseInt(cmd.getOptionValue(thresholdOptionStr));
                // If a threshold set and if doing frame scoring then assume we will output resutls as regions:
                applyThresholdToFrameScores = true;
            } catch (NumberFormatException e) {
                System.err.println("Invalid threshold number: '" + cmd.getOptionValue(thresholdOptionStr) + "' ");
                printUsageAndExit(options);
            }

        }

        if (cmd.hasOption(pluginOptionStr)) {
            pluginName = cmd.getOptionValue(pluginOptionStr);

        }

        if (cmd.hasOption(domainOptionStr)) {
            domainName = cmd.getOptionValue(domainOptionStr);
        }

        // check if we are starting scenicserver
        if (cmd.hasOption(scenicOptionStr)){
            oliveSeverName = cmd.getOptionValue(scenicOptionStr);
        }
        else {
            oliveSeverName = DEFAUL_SERVERNAME;
        }

        if (cmd.hasOption(portOptionStr)){
            try {
                scenicPort = Integer.parseInt(cmd.getOptionValue(portOptionStr));
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: '" + cmd.getOptionValue(portOptionStr) + "' ");
                printUsageAndExit(options);
            }
        }
        else {
            scenicPort = DEFAULT_PORT;
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

        if (cmd.hasOption(frameOptionStr)) {
            taskList.add(TaskType.FRAME_SCORE);
            requireAudioInput = true;
        }
        if (cmd.hasOption(globalOptionStr)) {
            taskList.add(TaskType.GLOBAL_SCORE);
            requireAudioInput = true;
        }
        if (cmd.hasOption(boxOptionStr)) {
            taskList.add(TaskType.BOUNDING_BOX);
            // needs data (not audio)
            requireAudioInput = true;
        }
        if (cmd.hasOption(regionOptionStr)) {
            taskList.add(TaskType.REGION_SCORE);
            requireAudioInput = true;
        }

        if (cmd.hasOption(loadOptionStr)) {
            if(null == pluginName || null == domainName){
                System.err.println("Must specify both a plugin (--plugin) and domain (--domain) name to preload a pluign");
                printUsageAndExit(options);
            }
            taskList.add(TaskType.PRELOAD);
        }

        if (cmd.hasOption(unloadOptionStr)) {
            if(null == pluginName || null == domainName){
                System.err.println("Must specify both a plugin (--plugin) and domain (--domain) name to remove/unload a pluign");
                printUsageAndExit(options);
            }
            taskList.add(TaskType.REMOVE);
        }

        if (cmd.hasOption(enhanceOptionStr)) {
            taskList.add(TaskType.AUDIO);
            requireAudioInput = true;
        }
        if (cmd.hasOption(vectorOptionStr)) {
            taskList.add(TaskType.VECTOR);
            requireAudioInput = true;
        }

        if (cmd.hasOption(channelOptionStr)) {
            try {
                channelNumber = Integer.parseInt(cmd.getOptionValue(channelOptionStr));
            } catch (NumberFormatException e) {
                System.err.println("Ignoring non integer channel number: " + cmd.getOptionValue(channelOptionStr));
            }
        }

//        boolean sendAudioAsSerializedBuffer = true;
        boolean sendAudioAsPath = false;
        boolean sendAudioAsDecodedBuffer = false;

        if (cmd.hasOption(decodedOptionStr)) {
            sendAudioAsDecodedBuffer = true;
        }

        if (cmd.hasOption(pathOptionStr)) {
            sendAudioAsPath = true;
        }

        if(sendAudioAsPath && sendAudioAsDecodedBuffer){
            System.err.println(String.format("Options '%s' and '%s' are mutually exclusive options.  Please only specify one of these options and run again ", decodedOptionStr, pathOptionStr));
            printUsageAndExit(options);
        }

        // Select the audio transfer type:
        if (sendAudioAsPath) {
            transferType = ClientUtils.AudioTransferType.SEND_AS_PATH;
        }
        else if (sendAudioAsDecodedBuffer){
            transferType = ClientUtils.AudioTransferType.SEND_SAMPLES_BUFFER;
        }
        else {
            transferType = ClientUtils.AudioTransferType.SEND_SERIALIZED_BUFFER;
        }

        if (cmd.hasOption(getUpdateStatusOptionStr)) {
            taskList.add(TaskType.GET_UPDATE_STATUS);
        }

        if (cmd.hasOption(applyUpdateOptionStr)) {
            taskList.add(TaskType.APPLY_UPDATE);
        }

        // Check for a wave, audio vector, or enrollment vector input

        if(cmd.hasOption(listOptionStr)){

            String listInputFilename = cmd.getOptionValue(listOptionStr);
            if (!Files.exists(Paths.get(listInputFilename).toAbsolutePath())) {
                System.err.println("ERROR: input list file '" + listInputFilename + "' does not exist");
                printUsageAndExit(options);
            }

            // First try to parse this as a PEM file
            PemParser pp = new PemParser();
            if (pp.parse(listInputFilename)) {
                fileRegions = pp.getChannelRegions();
                System.out.println("Received PEM Input");
            }
            else{

                RegionParser regionParser = new RegionParser();
                regionParser.parse(listInputFilename);
                if (!regionParser.isValid()){
                    System.err.println("Invalid list input file: " + listInputFilename);
                    printUsageAndExit(options);
                }

                List<String> filenames = new ArrayList<>();
                if (regionParser.isRegionsOnly()){
                    // Not a good use case for this...
                    // We only have regions, so we assume they passed the filename as the --wav option
                    if (cmd.hasOption(inputOptionStr) ) {
                        String audioFileName = cmd.getOptionValue(inputOptionStr);
                        // Make sure file exists
                        if (!Files.exists(Paths.get(audioFileName).toAbsolutePath())) {
                            System.err.println("ERROR: Wave file '" + audioFileName + "' does not exist");
                            printUsageAndExit(options);
                        }
                        filenames.add(audioFileName);
                    }
                    else {
                        System.err.println("ERROR: list options file specified with regiions and no audio file name, but the -w option not used to specify a file");
                        printUsageAndExit(options);
                    }
                }
                else {
                    filenames.addAll(regionParser.getFilenames());
                }
                for (String filename : filenames ){
                    ChannelClassPair ccp = new ChannelClassPair(channelNumber);
                    Map<ChannelClassPair, List<RegionWord>> r = new LinkedHashMap<>();
                    r.put(ccp, regionParser.getRegions(filename));
                    fileRegions.put(filename, r);
                }
            }
        }
//        else  if (cmd.hasOption(pemOptionStr)) {
//            String pemFileName = cmd.getOptionValue(pemOptionStr);
//            if (!Files.exists(Paths.get(pemFileName).toAbsolutePath())) {
//                System.err.println("ERROR: PEM file '" + pemFileName + "' does not exist");
//                printUsageAndExit(options);
//            }
//
//            PemParser pp = new PemParser();
//            if (!pp.parse(pemFileName)) {
//                System.err.println("ERROR: PEM file '" + pemFileName + "' contains no valid records");
//                printUsageAndExit(options);
//            }
//
//            fileRegions = pp.getChannelRegions();
//
//        }
        else if (cmd.hasOption(inputOptionStr) ) {
            String audioFileName = cmd.getOptionValue(inputOptionStr);
            // Make sure file exists
            if (!Files.exists(Paths.get(audioFileName).toAbsolutePath())){
                System.err.println("ERROR: Wave file '" + audioFileName + "' does not exist");
                printUsageAndExit(options);
            }
            // File only, not channel/class/regions?
            ChannelClassPair ccp = new ChannelClassPair(channelNumber);
            Map<ChannelClassPair, List<RegionWord>> r = new LinkedHashMap<>();
            r.put(ccp, new ArrayList<>());
            fileRegions.put(audioFileName, r);

        }
        else if (cmd.hasOption(audioVectorOptionStr)) {
            // Use the audio from a serialized audio vector
            audioVectorFileName = cmd.getOptionValue(audioVectorOptionStr);
            // Make sure file exists
            if (!Files.exists(Paths.get(audioVectorFileName))){
                System.err.println("Audio vector file '" + audioVectorFileName + "' does not exist");
                printUsageAndExit(options);
            }
        }

        if (cmd.hasOption(compareOptionStr)) {
            taskList.add(TaskType.COMPARE_AUDIO);

            //requireAudioInput = true;
            //  needs two audio inputs!
            // caller should have passed in the compare list using the
            // list option...
            if(fileRegions.keySet().size() != 2){
                System.err.println(String.format("Audio comparison task requires 2 (and ONLY 2) audio inputs.  Use the %s option to specify the audio files to compare ",listOptionStr));
                printUsageAndExit(options);
            }

           /* // The caller should have used the
            String audioFileName = cmd.getOptionValue(regionOptionStr);
            // Make sure file exists
            if (!Files.exists(Paths.get(audioFileName).toAbsolutePath())){
                System.err.println("ERROR: Wave file '" + audioFileName + "' does not exist");
                printUsageAndExit(options);
            }
            audioFiles.add(audioFileName);*/
        }

        if (cmd.hasOption(alignOptionStr)) {
            taskList.add(TaskType.AUDIO_ALIGN);

            //  needs two audio inputs!
            // caller should have passed in the compare list using the
            // list option...
            if(fileRegions.keySet().size() < 2){
                System.err.println(String.format("Audio Align task requires at least 2 audio inputs.  Use the %s option to specify the audio files to compare ",listOptionStr));
                printUsageAndExit(options);
            }
        }



        else
        {
            if(!(printPlugins || healthAndStatus) && taskList.size() > 0) {
                if(taskList.contains(TaskType.EXPORT) && taskList.size() > 1) {
                    System.err.println("Missing required argument: wave file name (or audio vector name)");
                    printUsageAndExit(options);
                }
            }
        }

        // Check that tasks require audio/vector input  have such an input
        if(requireAudioInput  ){
            if(fileRegions.keySet().size() == 0  && null == audioVectorFileName){

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



            if(shutdownServer){
                // Send a shutdown request to server, then exit
                server.shutdownServer();
                System.out.println("Sending a shutdown request to the server.  Exiting...");
                System.exit(0);

            }
            // First request current plugins (using a synchronous/blocking request)
            List<Pair<Olive.Plugin, Olive.Domain>> pluginList =  requestPlugins(server, printPlugins, printVerbose);

            if(healthAndStatus){


                requestStatus(server);

                Server.ServerInfo info = server.getServerInfo();
                if(null == info || null == info.version){
                    // Have not received a heatbeat with status yet... lets wait and try again
                    System.out.println("WAITING FOR SERVER STATS...");
                    Thread.sleep(15000);
                    info = server.getServerInfo();
                }

                if(null == info){
                    System.err.println("Server status not available");
                }
                else{
                    System.out.println(info.toString());
                }
            }

            // Next see if we need to preload a plugin
            if(taskList.contains(TaskType.PRELOAD)){

                Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomain(pluginName, domainName, pluginList);
                if(null == pd){
                    log.error("Could not preload plugin: '{}', domain '{}' because no plugin/domain having that name is available to the server", pluginName, domainName);
                }

                if(preloadPlugin(server, pd)){
                    System.out.println(String.format("Successfully requested plugin: '%s' and domain '%s' for preload", pluginName, domainName));
                }
                else {
                    System.out.println(String.format("Unable to complete preload request for plugin: '%s' and domain '%s'", pluginName, domainName));
                }
            }

            int numAsyncRequest = 0;

            // SPECIAL HACK UNTIL WE GET THIS TO WORK
//            if(workflowRequest){
//                if(requestWorkflow(server)){
//                    numAsyncRequest++;
//                }
//            }

            // Next process any request that don't require audio input
            if (taskList.contains(TaskType.GET_UPDATE_STATUS)) {
                // sync call
                Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, null, Olive.TraitType.UPDATER, pluginList);
                requestGetUpdateStatus(server, pd);
            }

            if (taskList.contains(TaskType.APPLY_UPDATE)) {
                // sync call
                Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, null, Olive.TraitType.UPDATER, pluginList);
                requestApplyUpdate(server, pd);
            }

            if (taskList.contains(TaskType.REMOVE)) {

                Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomain(pluginName, domainName, pluginList);
                if (null == pd) {
                    log.error("Could not remove (unload) plugin: '{}', domain '{}' because no plugin/domain matching that name is available to the server", pluginName, domainName);
                }

                if (requestPluginRemoval(server, pd)) {
                    numAsyncRequest++;
                    System.out.println(String.format("Successfully requested plugin: '%s' and domain '%s' for removal", pluginName, domainName));
                } else {
                    System.out.println(String.format("Unable to complete preload request for plugin: '%s' and domain '%s'", pluginName, domainName));
                }
            }

            if (taskList.contains(TaskType.COMPARE_AUDIO)){
                // Special case were we compare two
                Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, null, Olive.TraitType.GLOBAL_COMPARER, pluginList);

                List<String> audioFiles = new ArrayList<>(fileRegions.keySet());
                List<Integer> channels = new ArrayList<>();
                List<List<RegionWord>> regions = new ArrayList<>();
                for(String f : audioFiles){
                    Map<ChannelClassPair, List<RegionWord>> channelClass = fileRegions.get(f);
                    for(ChannelClassPair ccp : fileRegions.get(f).keySet()){
                        channels.add(ccp.getChannel());
                        regions.add(fileRegions.get(f).get(ccp));
                    }
                }
                if(channels.size() != 2){
                    System.err.println(String.format("Audio comparison task requires 2 (and ONLY 2) audio inputs."));
                    return;
                }
                if(regions.size() !=2){
                    System.err.println(String.format("Audio comparison task requires 2 (and ONLY 2) audio inputs."));
                    return;
                }
                if (requestGlobalComparison(server, pd, "Audio Comparison",
                        audioFiles.get(0),
                        audioFiles.get(1),
                        channels.get(0),
                        channels.get(1),
                        regions.get(0),
                        regions.get(1))) {
                    numAsyncRequest++;
                }
            }

            if (taskList.contains(TaskType.AUDIO_ALIGN)){
                Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, null, Olive.TraitType.AUDIO_ALIGNMENT_SCORER, pluginList);

                if (requestAudioAlignment(server, pd, "Audio Comparison", fileRegions)) {
                    numAsyncRequest++;
                }
            }


            // Now perform the SAD, SID, and/or LID request(s) - these request are  asynchronous.
            int numErrors = 0;
            for(String audioFileName : fileRegions.keySet()) {

//                Map<ChannelClassPair, List<RegionWord>> channelClass = fileRegions.get(audioFileName);
                for(ChannelClassPair ccp : fileRegions.get(audioFileName).keySet()){

                    try {
                        if (taskList.contains(TaskType.SAD) || taskList.contains(TaskType.FRAME_SCORE)) {
                            String taskLabel = taskList.contains(TaskType.SAD) ? "SAD" : null;
                            Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, taskLabel, Olive.TraitType.FRAME_SCORER, pluginList);

                            // SAD Request
                            if (requestFrameScores(server, pd, audioFileName, ccp.getChannel(), fileRegions.get(audioFileName).get(ccp))) {
                                numAsyncRequest++;
                            }
                        }

//                        if (taskList.contains(TaskType.SID)) {
//
//                            Olive.TraitType tt = Olive.TraitType.GLOBAL_SCORER;
//                            Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, "SID", Olive.TraitType.GLOBAL_SCORER, pluginList);
//
//                            if (requestGlobalScores(server, pd, "SID", TaskType.SID, tt, audioFileName, ccp.getChannel(), fileRegions.get(audioFileName).get(ccp))) {
//                                numAsyncRequest++;
//                            }
//                        }

                        if (taskList.contains(TaskType.LID) | taskList.contains(TaskType.GLOBAL_SCORE)) {

                            String label = taskList.contains(TaskType.LID) ? "LID" : null;

                            Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, label, Olive.TraitType.GLOBAL_SCORER, pluginList);

                            if (requestGlobalScores(server, pd, "Global Score", TaskType.GLOBAL_SCORE, Olive.TraitType.GLOBAL_SCORER, audioFileName, ccp.getChannel(), fileRegions.get(audioFileName).get(ccp))) {
                                numAsyncRequest++;
                            }
                        }
                        if (taskList.contains(TaskType.KWS) | taskList.contains((TaskType.QBE)) | taskList.contains(TaskType.REGION_SCORE)) {
                            //                    log.info("Using region scoring domain: {}", domainName);

                            String label = taskList.contains(TaskType.KWS) ? "KWS" : taskList.contains(TaskType.QBE) ? "QBE" : null;

                            Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, label, Olive.TraitType.REGION_SCORER, pluginList);
                            if (requestRegionScores(server, pd, audioFileName, ccp.getChannel(), fileRegions.get(audioFileName).get(ccp))) {
                                numAsyncRequest++;
                            }
                        }

                        if (taskList.contains(TaskType.AUDIO)) {
                            log.debug("Using AUDIO domain: {}", domainName);
                            if (requestAudioConversion(server, ClientUtils.findPluginDomainByTrait(pluginName, domainName, null, Olive.TraitType.AUDIO_CONVERTER, pluginList), audioFileName, ccp.getChannel(), fileRegions.get(audioFileName).get(ccp))) {
                                numAsyncRequest++;
                            }
                        }

                        if (taskList.contains(TaskType.BOUNDING_BOX)) {

                            String label = "box";

                            Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, label, Olive.TraitType.BOUNDING_BOX_SCORER, pluginList);
                            if (requestBoxAnalysis(server, pd, audioFileName, true)){
                                numAsyncRequest++;
                            }

                        }

                        if (taskList.contains(TaskType.VECTOR)) {

                            log.debug("Using AUDIO domain: {}", domainName);
                            if (requestAudioVector(server, ClientUtils.findPluginDomainByTrait(pluginName,
                                    domainName,
                                    null,
                                    Olive.TraitType.AUDIO_VECTORIZER,
                                    pluginList),
                                    audioFileName, ccp.getChannel(), fileRegions.get(audioFileName).get(ccp) )) {

                                numAsyncRequest++;
                            }


                        }

                        //  Just an example:
                      /*  Olive.GetActiveRequest.Builder requestMsg = Olive.GetActiveRequest.newBuilder();
                        Server.Result<Olive.GetActiveRequest, Olive.GetActiveResult> activeResult = server.synchRequest(requestMsg.build());
                        if(activeResult.hasError()){
                            log.info("ACTIVE REQUEST Error: {}", activeResult.getError());
                        }
                        else {
                            log.info("ACTIVE REQUEST: {}", activeResult.getRep().toString());
                        }*/

                    }
                    catch (IOException | UnsupportedAudioFileException e) {
                        log.debug("Audio error detail: ",e);
                        log.error("Unable to process file '{}' because: {}", audioFileName, e.getMessage());
                        numErrors++;
                    }

                }

            }


            int numTimeouts = 0;

            if(numAsyncRequest == 0){
                System.err.println("OliveAnalyze finished without completing any analysis tasks.");
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
//                    System.out.println(String.format("Received %s result for task: %s and id: %s", sr.isError ? "unsuccessful" : "successful", sr.getType().toString(), sr.getId()));
                    numAsyncRequest--;

                    if(sr.isError){
                        List<AnalysisResult> list  = analysisFailures.get(sr.getType());
                        if(null == list){
                            list = new ArrayList<>();
                            list.add(sr);
                            analysisFailures.put(sr.getType(), list);
                        }
                        else {
                            list.add(sr);
                        }
                        numErrors++;
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
                        List<AnalysisResult> ars =  analysisFailures.get(t);
                        for (AnalysisResult ar: ars) {
                            System.err.println(String.format("%s Analysis failed for file '%s' with error: %s" , t, ar.audioFilename, ar.result.getError()));
                        }
                    }
                }
                // if
                if(analysisFailures.containsKey(TaskType.REGION_SCORE)
                        && !analysisSuccess.containsKey(TaskType.REGION_SCORE)){

                    RegionScorerReportWriter arw = new RegionScorerReportWriter(outputDirName, "");
                    arw.addError("No regions found due to errors");


                }

                System.out.println(String.format("\nOliveAnalyze finished with %d errors.  Exiting...", numErrors));

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




    /*
    Picks the first plugin/domain found for the specified task

     */

    /**
     * Picks the first plugin/domain for the specified task
     *
     *
     * @param taskName the task the plugin must support
     * @param pluginList the list of available plugins
     *
     * @return
     */
    public Pair<Olive.Plugin, Olive.Domain> findPluginDomainByTask( String taskName, List<Pair<Olive.Plugin, Olive.Domain>> pluginList ){

        Olive.Plugin plugin = null;
        Olive.Domain domain = null;

        for(Pair<Olive.Plugin, Olive.Domain> p : pluginList){
            // Use the first domain found for
            List<Olive.Trait> traits = p.getFirst().getTraitList();
            if(p.getFirst().getTask().toLowerCase().equals(taskName.toLowerCase())){
                plugin = p.getFirst();
                domain = p.getSecond();


                break;
            }
        }

        if(plugin != null){
            return new Pair<>(plugin, domain);
        }

        return null;
    }

    private String formatPluginErrorMsg(String pluginName, String domainName, Olive.TraitType type){


        return String.format("No plugin-domain found having trait %s, plugin name: '%s' and domain: '%s' ", type.toString(), null == pluginName ? "*" : pluginName, null == domainName ? "*" : domainName);
    }


    /*public Pair<Olive.Plugin, Olive.Domain> findPluginDomainByTrait(String pluginName, String domainName, String taskName, Olive.TraitType type, List<Pair<Olive.Plugin, Olive.Domain>> pluginList){

        Olive.Plugin plugin = null;
        Olive.Domain domain = null;

        // if  the plugin and domain are specified then use that one - we should check the trait type too, but this
        // allows the client to  call the wrong plugin, which might be useful for testing or demonstration.
        if(null != pluginName && !pluginName.isEmpty()){
            if(null != domainName && !domainName.isEmpty()){
                return findPluginDomain(pluginName, domainName, pluginList);
            }
        }

        for(Pair<Olive.Plugin, Olive.Domain> p : pluginList){
            // Use the first domain found for the supported trait type
            List<Olive.Trait> traits = p.getFirst().getTraitList();

            boolean traitSupported  = false;
            for(Olive.Trait t : traits){
                traitSupported = t.getType() == type;
                //  check for derived trait types (i.e. ClassEnroller->ClassModifier)
                if(!traitSupported && type == Olive.TraitType.CLASS_ENROLLER){
                    traitSupported = t.getType() == Olive.TraitType.CLASS_MODIFIER;
                }


                if(traitSupported) {
                    plugin = p.getFirst();
                    domain = p.getSecond();
                    boolean valid = true;

                    // Filter results:
                    if (null != taskName) {
                        if (!plugin.getTask().toLowerCase().equals(taskName.toLowerCase())) {
                            valid = false;
                        }
                    }
                    if(null != pluginName){
                        if (! plugin.getId().toLowerCase().equals(pluginName.toLowerCase())) {
                            valid = false;
                        }
                    }
                    if(valid) {
                        if(null != domainName){
                            if (! domain.getId().toLowerCase().equals(domainName.toLowerCase())) {
                                valid = false;
                            }
                        }

                        if (valid) return new Pair<>(plugin, domain);
                    }
                }
            }

        }

        // No plugin found
        log.error(formatPluginErrorMsg(pluginName, domainName, type));
        return null;
    }*/

  /*  public Pair<Olive.Plugin, Olive.Domain> findPluginDomain(String pluginName, String domainName, List<Pair<Olive.Plugin, Olive.Domain>> pluginList ){

        Olive.Plugin plugin = null;
        Olive.Domain domain = null;
        for(Pair<Olive.Plugin, Olive.Domain> p : pluginList){
            // Use the first domain found for
            if(p.getFirst().getId().toLowerCase().equals(pluginName.toLowerCase())){

                if(p.getSecond().getId().toLowerCase().equals(domainName.toLowerCase())){
                    plugin= p.getFirst();
                    domain = p.getSecond();
                    break;
                }

            }
        }

        if(plugin != null){
            return new Pair<>(plugin, domain);
        }

        log.error("Could not find plugin-domain: {}-{}", pluginName, domainName);
        return null;
    }*/

/*
    public Pair<Olive.Plugin, Olive.Domain> findTraitForDomain(String taskName, String domainName, List<Pair<Olive.Plugin, Olive.Domain>> pluginList ){

        Olive.Plugin plugin = null;
        Olive.Domain domain = null;
        for(Pair<Olive.Plugin, Olive.Domain> p : pluginList){
            // TODO - this should be checked against traits...
            // Use the first domain found for
            List<Olive.Trait> traits = p.getFirst().getTraitList();
            if(p.getFirst().getTask().toLowerCase().equals(taskName.toLowerCase())){

                if(p.getSecond().getId().toLowerCase().equals(domainName.toLowerCase())){
                    plugin= p.getFirst();
                    domain = p.getSecond();
                    break;
                }

            }
        }

        if(plugin != null){
            return new Pair<>(plugin, domain);
        }

        return findPluginDomainByTask(taskName, pluginList);
    }
*/


    /*public Pair<Olive.Plugin, Olive.Domain> findTraitForDomain(Olive.TraitType type, String domainName, List<Pair<Olive.Plugin, Olive.Domain>> pluginList ){

        Olive.Plugin plugin = null;
        Olive.Domain domain = null;
        for(Pair<Olive.Plugin, Olive.Domain> p : pluginList){
            // Use the first domain found for
            List<Olive.Trait> traits = p.getFirst().getTraitList();
            boolean traitSupported  = false;
            for(Olive.Trait t : traits){
                traitSupported = t.getType() == type;
                if(traitSupported){
                    if(p.getSecond().getId().toLowerCase().equals(domainName.toLowerCase())){
                        plugin= p.getFirst();
                        domain = p.getSecond();
                        return new Pair<>(plugin, domain);
                    }
                }
            }


        }

        return findPluginDomainByTrait(type, pluginList);
    }*/


    public List<Pair<Olive.Plugin, Olive.Domain>> requestPlugins(Server server, boolean printPlugins, boolean printVerbose) throws ClientException {

        List<Pair<Olive.Plugin, Olive.Domain>> pluginList = ClientUtils.requestPlugins(server);


        if (printPlugins) {

            Map<Olive.Plugin, List<Olive.Domain>> plugins = new HashMap<>();
            boolean filterPlugin = null != pluginName ;
            boolean pluginFound = false;

            // Group domains by plugin
            for (Pair<Olive.Plugin, Olive.Domain> pp : pluginList) {

                if(filterPlugin && !(pp.getFirst().getId().toLowerCase().equals(pluginName.toLowerCase()))){
                    // no match
                    continue;
                }
                else if(filterPlugin && (pp.getFirst().getId().toLowerCase().equals(pluginName.toLowerCase()))){
                    pluginFound = true;
                    //System.out.println(String.format("Found plugin: %s", pp.getFirst().getId()));
                }

                List<Olive.Domain> domainList = new ArrayList<>();
                if(plugins.containsKey(pp.getFirst())){
                    domainList = plugins.get(pp.getFirst());
                }
                else {
                    plugins.put(pp.getFirst(), domainList);
                }
                domainList.add(pp.getSecond());
            }

            // Now print plugins...


            // log.info("Found {} plugin/domainss:", pluginList.size());

            if(null == pluginName ) {
                System.out.println(String.format("Found %d plugin(s):", plugins.size()));
            }
            else if(filterPlugin && !pluginFound){
                System.err.println(String.format("Could not find plugin: '%s", pluginName));
            }
            else {
                System.out.println("");
            }

            for(Olive.Plugin p : plugins.keySet()){
                String group = "";
                if (p.hasGroup()){
                    group = p.getGroup();
                }
                System.out.println(String.format("Plugin: %s (%s,%s) v%s has %d domain(s):", p.getId(), p.getTask(),group, p.getVersion(), plugins.get(p).size()));

                List<Olive.Domain> domains = plugins.get(p);
                for(Olive.Domain d : domains) {
                    System.out.println(String.format("\tDomain: %s, Description: %s", d.getId(), d.getDesc()));

                    if (printVerbose) {
                        System.out.println("\t\tSupported classes:");
                        // Classes (languages, speakers, keywords, etc) supported by this plugin/domain:
                        for (String c : d.getClassIdList()) {
                            System.out.println(String.format("\t\t\t\t %s", c));
                        }
                    }

                }


                if (printVerbose) {
                    System.out.println(String.format("\tSupported traits for plugin: %s:", p.getId()));
                    // Print trait(s):
                    for (Olive.Trait t : p.getTraitList()) {
                        if (t.getOptionsCount() > 0) {
                            System.out.println(String.format("\t\tImplements trait: %s, with %d options:", t.getType(), t.getOptionsCount()));
                        } else {
                            System.out.println(String.format("\t\tImplements trait: %s, NO options defined", t.getType()));
                        }
                        for (Olive.OptionDefinition od : t.getOptionsList()) {
                            System.out.println(String.format("\t\t\tOption name: '%s', Label: '%s', Desc: '%s', Type: '%s', Default: '%s' ", od.getName(), od.getLabel(), od.getDesc(), od.getType(), od.getDefault()));
                        }
                    }
                }



            }

        }

        if(printPlugins) {
            // pad results
            System.out.println("");
            System.out.println("");
            System.out.println("");
        }

        return  pluginList;
    }


    public boolean requestGetUpdateStatus(Server server, Pair<Olive.Plugin, Olive.Domain> pp) throws ClientException, IOException, UnsupportedAudioFileException {


        int requstId = seq.getAndIncrement();

        // Create a callback to handle async SAD results from the server, note that we pass sadRequstId to the callback as an example of bundling metadata with the score results
        Server.ResultCallback<Olive.GetUpdateStatusRequest, Olive.GetUpdateStatusResult> rc = new Server.ResultCallback<Olive.GetUpdateStatusRequest, Olive.GetUpdateStatusResult>() {

            @Override
            public void call(Server.Result<Olive.GetUpdateStatusRequest, Olive.GetUpdateStatusResult> r) throws InvalidProtocolBufferException {
                // do something with the results:
                if(!r.hasError()){

                    DateFormat f = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");

                    Olive.DateTime dt = null;
                    if(r.getRep().hasLastUpdate()) {
                        dt = r.getRep().getLastUpdate();
                        Calendar cal = Calendar.getInstance();
                        cal.set(dt.getYear(), dt.getMonth()-1, dt.getDay(), dt.getHour(), dt.getMin(), dt.getMin());

                        System.out.println(String.format("Update %s, previous update %s", r.getRep().getUpdateReady() ?  "ready": "not ready", f.format(cal.getTime())));
                    }
                    else {
                        System.out.println(String.format("Update %s, NO previous updates", r.getRep().getUpdateReady() ?  "ready" : "not ready"));
                    }

                    for(Olive.Metadata ms : r.getRep().getParamsList()){
                        //Message m = deserializers[scenicMessage.getMessageType().getNumber()].run(bs)
                        Server.MetadataWrapper mw = server.deserializeMetadata(ms);
                        System.out.println(String.format("Update parameter: %s = %s, type: %s", ms.getName(), mw.toString(), ms.getType()));
                    }

                }
                else {
                    System.err.println(String.format("SAD request failed: %s", r.getError()));
                }

                // Let main thread know the SAD request has been received
                queue.add(new AnalysisResult(requstId, TaskType.GET_UPDATE_STATUS, r.hasError(), null, r));
            }
        };

        return ClientUtils.requestGetUpdateStatus(server, pp, rc, false);

    }


    /*public boolean requestWorkflow(Server server*//*, Pair<Olive.Plugin, Olive.Domain> pp*//*) throws ClientException, IOException, UnsupportedAudioFileException {


        int requstId = seq.getAndIncrement();

        // Create a callback to handle async SAD results from the server, note that we pass sadRequstId to the callback as an example of bundling metadata with the score results
        Server.ResultCallback<Olive.WorkflowAnalysisRequest, Olive.WorkflowAnalysisResult> rc = new Server.ResultCallback<Olive.WorkflowAnalysisRequest, Olive.WorkflowAnalysisResult>() {

            @Override
            public void call(Server.Result<Olive.WorkflowAnalysisRequest, Olive.WorkflowAnalysisResult> r) throws InvalidProtocolBufferException {
                // do something with the results:
                if(!r.hasError()){

                    if(r.getRep().hasError()) {
                        System.out.println("Workflow analysis failed with error: " + r.getRep().getError());
                    }
                    else {
                        System.out.println(String.format("Worflow returned %d task results", r.getRep().getJobResult(0).getTaskResultsCount() ));
                        for(Olive.WorkflowTaskResult wkflwResult : r.getRep().getJobResult(0).getTaskResultsList()){
                            System.out.println("Found message type:" + wkflwResult.getMessageType());
                            // todo convert to result... Server.deserializers
                        }
                    }

                }
                else {
                    System.err.println(String.format("Workflow request failed: %s", r.getError()));
                }

                // Let main thread know the SAD request has been received
                queue.add(new AnalysisResult(requstId, TaskType.WORKFLOW, r.hasError(), null, r));
            }
        };

        // hack - we should submit a Workflow Definition to the server to get a valid Workflow back from the server
        // but we just create our own Workflow for now

        // Currently we only support WorkflowDefinitions with one job...
//         Olive.WorkflowDefinition.Builder workflowDef = Olive.WorkflowDefinition.newBuilder();
//         workflowDef.setProcessingType(Olive.WorkflowJobType.MERGE);

        Olive.DataHandlerProperty.Builder props = Olive.DataHandlerProperty.newBuilder()
                .setMinNumberInputs(1)
                .setMaxNumberInputs(1)
                .setType(Olive.InputDataType.AUDIO)
                .setPreprocessingRequired(true)
                .setResampleRate(8000)
                .setMode(Olive.MultiChannelMode.MONO);

        Olive.WorkflowJob.Builder workflowJobDef = Olive.WorkflowJob.newBuilder();
        workflowJobDef.setWorkflowType(Olive.WorkflowType.ANALYSIS);
        workflowJobDef.setDataProperties(props);
        // make a fame score request
        Olive.FrameScorerRequest.Builder fsr = Olive.FrameScorerRequest.newBuilder()
//                .setAudio(Olive.Audio.newBuilder().setPath("/tmp/test.wav"))
                .setPlugin("pluginName")
                .setDomain("domainName");
        Olive.WorkflowTask.Builder wfr = Olive.WorkflowTask.newBuilder()
                .setTask(Olive.TaskType.SAD)
                .setTraitOutput(Olive.TraitType.FRAME_SCORER)
                .setMessageType(Olive.MessageType.FRAME_SCORER_REQUEST)
                .setMessageData(fsr.build().toByteString())
                .setConsumerResultLabel("SAD")
                .setConsumerDataLabel("audio")
                .setReturnResult(true);
        workflowJobDef.addTasks(wfr);

//        workflowDef.addJobs(workflowJobDef);

        // Hack
//        Olive.Workflow.Builder workflowBuildr = Olive.Workflow.newBuilder().addJobs(workflowJobDef);

//        Olive.WorkflowDataRequest.Builder dataRequest = Olive.WorkflowDataRequest.newBuilder()
//                .setDataId("java_test_audio")
//                .setDataType(Olive.InputDataType.AUDIO)
//                .setWorkflowData(Olive.Audio.newBuilder().setPath("/tmp/test.wav").build().toByteString())
//                .setDataLabel("audio");  // todo test with another label..

        Olive.WorkflowAnalysisRequest.Builder req = Olive.WorkflowAnalysisRequest.newBuilder();
        req.setWorkflowDefinition(workflowBuildr);

        // currently have nothing to specify
        server.enqueueRequest(req.build(), rc);

        return true;

    }*/


    public boolean requestApplyUpdate(Server server, Pair<Olive.Plugin, Olive.Domain> pp) throws ClientException, IOException, UnsupportedAudioFileException {


        int requstId = seq.getAndIncrement();

        // Create a callback to handle async SAD results from the server, note that we pass sadRequstId to the callback as an example of bundling metadata with the score results
        Server.ResultCallback<Olive.ApplyUpdateRequest, Olive.ApplyUpdateResult> rc = new Server.ResultCallback<Olive.ApplyUpdateRequest, Olive.ApplyUpdateResult>() {

            @Override
            public void call(Server.Result<Olive.ApplyUpdateRequest, Olive.ApplyUpdateResult> r) throws InvalidProtocolBufferException {
                if(!r.hasError()){

                    System.out.println(String.format("Plugin updated: %s", r.getRep().getSuccessful()));

                }
                else {
                    System.err.println(String.format("Plugin update request failed: %s", r.getError()));
                }

                // Let main thread know the SAD request has been received
                queue.add(new AnalysisResult(requstId, TaskType.APPLY_UPDATE, r.hasError(), null, r));
            }
        };

        return ClientUtils.requestApplyUpdate(server, pp, rc, false);

    }


    /**
     * Make a frame score request (usually SAD).
     *
     * @param server
     * @return
     * @throws ClientException
     */
    public boolean requestStatus(Server server) throws ClientException, IOException, UnsupportedAudioFileException {


        int statusRequstId = seq.getAndIncrement();

        // Create a callback to handle async SAD results from the server, note that we pass sadRequstId to the callback as an example of bundling metadata with the score results
        Server.ResultCallback<Olive.GetStatusRequest, Olive.GetStatusResult> rc = new Server.ResultCallback<Olive.GetStatusRequest, Olive.GetStatusResult>() {

            @Override
            public void call(Server.Result<Olive.GetStatusRequest, Olive.GetStatusResult> r) {

                // do something with the results:
                if(!r.hasError()){

                    Olive.GetStatusResult gsr = r.getRep();
                    System.out.println(String.format("Received Status Result.  Num pending: %d, num busy: %d num finished: %d",
                            gsr.getNumPending(), gsr.getNumBusy(), gsr.getNumFinished()) );

                }
                else {
                    log.error("Status request failed: {}", r.getError());
                }

                // Let main thread know the  request has been received
                queue.add(new AnalysisResult(statusRequstId, TaskType.GET_UPDATE_STATUS, r.hasError(), "filename", r));
            }
        };

        return ClientUtils.requestStatus(server, rc,false);
        //return true;
    }

        /**
         * Make a frame score request (usually SAD).
         *
         * @param server
         * @param pp
         * @param filename
         * @return
         * @throws ClientException
         */
    public boolean requestFrameScores(Server server, Pair<Olive.Plugin, Olive.Domain> pp, String filename, Integer channelNum, List<RegionWord> regions) throws ClientException, IOException, UnsupportedAudioFileException {

        if (null == pp){
            return false;
        }

        int sadRequstId = seq.getAndIncrement();

        // Create a callback to handle async SAD results from the server, note that we pass sadRequstId to the callback as an example of bundling metadata with the score results
        Server.ResultCallback<Olive.FrameScorerRequest, Olive.FrameScorerResult> rc = new Server.ResultCallback<Olive.FrameScorerRequest, Olive.FrameScorerResult>() {

            @Override
            public void call(Server.Result<Olive.FrameScorerRequest, Olive.FrameScorerResult> r) {


                // do something with the results:
                if(!r.hasError()){

                    try {
                        Pair<TaskType, String> key;
                        if(applyThresholdToFrameScores){
                            key  = new Pair<>(TaskType.FRAME_SCORE, Integer.toString(thresholdNumber));
                        }else {
                            key  = new Pair<>(TaskType.FRAME_SCORE, filename); // mono - no channel number
                        }

                        AbstractReportWriter writer = rptMgr.getWriter(key, () ->   new FrameScorerReportWriter(outputDirName, filename, null, applyThresholdToFrameScores, thresholdNumber) );
                        for( Olive.FrameScores fs : r.getRep().getResultList()){
                            ((FrameScorerReportWriter)(writer)).addData(fs, "");
                        }
                    } catch (ReportException e) {
                        log.error("Unable to write frame score results because: {}", e.getMessage());
                    }


                    for(Olive.FrameScores fs : r.getRep().getResultList()){
                        // Assume only "speech" results returned (some SAD plugins may recognize non-speech)
                        System.out.println(String.format("Received %d frame scores for '%s'", fs.getScoreCount(), fs.getClassId()) );

                        printSpeechRegions(fs);



                    }

                }
                else {
                    log.error("SAD request failed: {}", r.getError());
                }

                // Let main thread know the SAD request has been received
                queue.add(new AnalysisResult(sadRequstId, TaskType.FRAME_SCORE, r.hasError(), filename, r));
            }
        };

        return ClientUtils.requestFrameScore(server, pp, filename, channelNum, rc, true, transferType, regions, propertyOptions, classIDs);


        //return true;
    }

    /**
     * Request global score analysis (LID or SID) using an audio buffer
     * @param server the SCENIC server handle
     * @param plugin the plugin/domain to use
     *
     * @return scores
     *
     * @throws ClientException if there is a communication error with the server
     */
    public boolean requestGlobalScores(Server server, Pair<Olive.Plugin, Olive.Domain> plugin,
                                       String taskLabel,
                                       TaskType tt,
                                       Olive.TraitType trait,
                                       String filename,
                                       Integer channelNum,
                                       List<RegionWord> regions
                                       /*byte[] samples*/) throws ClientException, IOException, UnsupportedAudioFileException {
        if(null == plugin){
            return false;
        }

        int requestID = seq.getAndIncrement();

        // Create a callback to handle async results from server
        Server.ResultCallback<Olive.GlobalScorerRequest, Olive.GlobalScorerResult> rc = new Server.ResultCallback<Olive.GlobalScorerRequest, Olive.GlobalScorerResult>() {

            @Override
            public void call(Server.Result<Olive.GlobalScorerRequest, Olive.GlobalScorerResult> r) {

                // do something with the results:
                if(!r.hasError()){
                    System.out.println(String.format("Received %d %s scores:", r.getRep().getScoreCount(), taskLabel));
                    for(Olive.GlobalScore gs : r.getRep().getScoreList()){
                        System.out.println(String.format("\t%s = %f", gs.getClassId(), gs.getScore()));
                    }

                    try {

                        // Global scorers use the same file
                        Pair<TaskType, String> key = new Pair<>(TaskType.GLOBAL_SCORE, "");

                        AbstractReportWriter writer = rptMgr.getWriter(key, () -> new GlobalScorerReportWriter(outputDirName, null));
                        // handle a global score result - these go to the standard output file since data is treated as mono
                        ((GlobalScorerReportWriter) (writer)).addData(filename, (r.getRep()).getScoreList());



                    } catch (ReportException e) {
                        log.error("Unable to write global score results because: {}", e.getMessage());
                    }

                }
                else{
                    log.error("{} Score error: {}", taskLabel, r.getError() );
                }

                // Let main thread know the request has been received
                queue.add(new AnalysisResult(requestID, TaskType.GLOBAL_SCORE, r.hasError(), filename, r));

            }
        };

        log.info("Sending global score request for plugin: {}, domain: {}", plugin.getFirst().getId(), plugin.getSecond().getId());
        return ClientUtils.requestGlobalScore(server, plugin, trait, filename, channelNum, rc, true, transferType, regions, propertyOptions, classIDs);

    }

    /**
     * Request text transformation
     * @param server the SCENIC server handle
     * @param plugin the plugin/domain to use
     * @param filename the text/stirng  to submit for translation
     *
     * @return scores
     *
     * @throws ClientException if there is a communicaiton error with the serve r
     */
    private boolean requestBoxAnalysis(Server server, Pair<Olive.Plugin, Olive.Domain> plugin, String filename, boolean runAsync) throws ClientException, IOException, Exception {

        if (null == plugin){
            return false;
        }

        int boxRequstId = seq.getAndIncrement();

        // Create a callback to handle async KWS results from server
        Server.ResultCallback<Olive.BoundingBoxScorerRequest, Olive.BoundingBoxScorerResult> rc = new Server.ResultCallback<Olive.BoundingBoxScorerRequest, Olive.BoundingBoxScorerResult>() {

            @Override
            public void call(Server.Result<Olive.BoundingBoxScorerRequest, Olive.BoundingBoxScorerResult> r) {

                // do something with the results:
                if(!r.hasError()){
                    System.out.println(String.format("Received %d bounding box region scores:", r.getRep().getRegionCount()));
                    for(Olive.BoundingBoxScore rs : r.getRep().getRegionList()){
                        Olive.BoundingBox bbox = rs.getBbox();

                        if (rs.hasTimeRegion()) {
                            System.out.println(String.format("\t%s = %f.  Box: (%d, %d, %d, %d) From %.2f to %.2f ", rs.getClassId(), rs.getScore(), bbox.getX1(), bbox.getY1(), bbox.getX2(), bbox.getY2(), rs.getTimeRegion().getStartT(), rs.getTimeRegion().getEndT()));
                            // also debating have time regions be a list...
                            /* if (timeRegions.size() > 1) {
                                System.out.println(String.format("\t%s = %f.  Box: (%.2f, %.2f, %.2f, %.2f) From %.2f to %.2f ", rs.getClassId(), rs.getScore(), bbox.getX1(), bbox.getY1(), bbox.getX2(), bbox.getY2(), rs.getStartT(), rs.getEndT()));
                            }
                            else {
                                // timestamp only
                                System.out.println(String.format("\t%s = %f.  Box: (%.2f, %.2f, %.2f, %.2f) Timestamp %.2f ", rs.getClassId(), rs.getScore(), bbox.getX1(), bbox.getY1(), bbox.getX2(), bbox.getY2(), timeRegions.get(0).getStartT()));
                            }*/
                        }
                        else{
                            System.out.println(String.format("\t%s = %f.  Box: (%d, %d, %d, %d) ", rs.getClassId(), rs.getScore(), bbox.getX1(), bbox.getY1(), bbox.getX2(), bbox.getY2()));
                        }
                    }
                    // TODO SAVE RESULTS
/*
                    try {
                        Pair<TaskType, String> key = new Pair<>(TaskType.REGION_SCORE, "");
                        AbstractReportWriter writer = rptMgr.getWriter(key, () -> new RegionScorerReportWriter(outputDirName, ""));
                        ((RegionScorerReportWriter)(writer)).addData(filename, r.getRep().getRegionList());

                    } catch (ReportException e) {
                        System.err.println(String.format("Unable to write region score results because: %s", e.getMessage()));
                    }*/

                }
                // Error should be handled below
//                else{
//                    System.out.println(String.format("Visual scoring error: %s", r.getError()));
//                }

                // Let main thread know the request has been received
                queue.add(new AnalysisResult(boxRequstId, TaskType.BOUNDING_BOX, r.hasError(), filename, r));
            }
        };
        System.out.println(String.format("Submitting %s for analysis with plugin %s and domain %s",filename, plugin.getFirst().getId(), plugin.getSecond().getId()));

        // TODO SUPPORT REGIONS..?

        return ClientUtils.requestBoundingBoxScores(server,
                plugin,
                filename,
                rc,
                runAsync,
                transferType,
                new ArrayList<RegionWord>(),
                propertyOptions,
                classIDs);


    }

    /**
     * Request global comparison analysis  using two audio buffers
     * @param server the SCENIC server handle
     * @param plugin the plugin/domain to use
     *
     * @return scores
     *
     * @throws ClientException if there is a communication error with the server
     */
    public boolean requestGlobalComparison(Server server, Pair<Olive.Plugin, Olive.Domain> plugin,
                                           String taskLabel,
                                           String filename_one,
                                           String filename_two,
                                           Integer channelNum1,
                                           Integer channelNum2,
                                           List<RegionWord> region1,
                                           List<RegionWord> region2 ) throws ClientException, IOException, UnsupportedAudioFileException {
        if(null == plugin){
            return false;
        }

        int requestID = seq.getAndIncrement();

        // Create a callback to handle async results from server
        Server.ResultCallback<Olive.GlobalComparerRequest, Olive.GlobalComparerResult> rc = new Server.ResultCallback<Olive.GlobalComparerRequest, Olive.GlobalComparerResult>() {

            @Override
            public void call(Server.Result<Olive.GlobalComparerRequest, Olive.GlobalComparerResult> r) {

                // do something with the results:
                if(!r.hasError()){

                    System.out.println(String.format("Received %d %s scores:", r.getRep().getResultsCount(), taskLabel));
                    Olive.GlobalComparerResult result = r.getRep();

                    Map<String, Server.MetadataWrapper> results = new HashMap<>();
                    for(Olive.Metadata meta : result.getResultsList()){

                        try {
                            Server.MetadataWrapper mw = server.deserializeMetadata(meta);
                            System.out.println(String.format("Metadata score: %s = %s, type: %s", meta.getName(), mw.toString(), meta.getType()));
                            results.put(meta.getName(), mw);
                        } catch (InvalidProtocolBufferException e) {
                            System.err.println(String.format("Unsupported metadata type: %s", meta.getType()));
                        }
                    }

                    try {
                        GlobalComparerReportWriter writer = new GlobalComparerReportWriter(outputDirName, filename_one, filename_two, null);

                        // Assume there will be only one report
                        if(result.getReportCount() > 0 ) {
                            Olive.GlobalComparerReport report = result.getReport(0);
                            writer.addData(results, report);
                            writer.close();
                        }


                    } catch (Exception e) {
                        System.err.println(String.format("Unable to write global compare results because: %S", e.getMessage()));
                    }

                }
                else{
                    System.err.println(String.format("%s Comparison error: %s", taskLabel, r.getError() ));
                }

                // Let main thread know the request has been received
                queue.add(new AnalysisResult(requestID, TaskType.COMPARE_AUDIO, r.hasError(), filename_one + " & " + filename_two, r));

            }
        };

        log.info("Sending global compare request for plugin: {}, domain: {}", plugin.getFirst().getId(), plugin.getSecond().getId());
        return ClientUtils.requestGlobalCompare(server, plugin, filename_one, filename_two, channelNum1, channelNum2, rc, true, transferType, region1, region2, propertyOptions, classIDs);

    }

    /**
     * Request Audio Alignment analysis
     * @param server the OLIVE server handle
     * @param plugin the plugin/domain to use
     *
     * @return scores
     *
     * @throws ClientException if there is a communication error with the server
     */
    public boolean requestAudioAlignment(Server server, Pair<Olive.Plugin, Olive.Domain> plugin,
                                            String taskLabel,
                                         Map<String, Map<ChannelClassPair, List<RegionWord>>> fileRegions) throws ClientException, IOException, UnsupportedAudioFileException {
        if(null == plugin){
            return false;
        }

        int requestID = seq.getAndIncrement();

        // Create a callback to handle async results from server
        Server.ResultCallback<Olive.AudioAlignmentScoreRequest, Olive.AudioAlignmentScoreResult> rc = new Server.ResultCallback<Olive.AudioAlignmentScoreRequest, Olive.AudioAlignmentScoreResult>() {

            @Override
            public void call(Server.Result<Olive.AudioAlignmentScoreRequest, Olive.AudioAlignmentScoreResult> r) {

                // do something with the results:
                if(!r.hasError()){

                    System.out.println(String.format("Received %d %s scores:", r.getRep().getScoresCount(), taskLabel));
                    Olive.AudioAlignmentScoreResult result = r.getRep();

//                    List<> results = new ArrayList();
                    for(Olive.AudioAlignmentScore score : result.getScoresList()){
                        System.out.println(String.format("Audio alignment score: %s, %s, shift: %f, confidence: %f", score.getReferenceAudioLabel(),score.getOtherAudioLabel(), score.getShiftOffset(), score.getConfidence()));
//                            results.put(new AASR());
                    }

                    try {
                        // TODO WRITE OUTPUT?
                        /*GlobalComparerReportWriter writer = new GlobalComparerReportWriter(outputDirName, filename_one, filename_two, null);

                        // Assume there will be only one report
                        if(result.getReportCount() > 0 ) {
                            Olive.GlobalComparerReport report = result.getReport(0);
                            writer.addData(results, report);
                            writer.close();
                        }*/


                    } catch (Exception e) {
                        System.err.println(String.format("Unable to write audio alignment analysis results because: %S", e.getMessage()));
                    }

                }
                else{
                    System.err.println(String.format("%s Audio Alignment error: %s", taskLabel, r.getError() ));
                }

                // Let main thread know the request has been received
                StringBuilder builder = new StringBuilder();
                for (String fn : fileRegions.keySet()){
                    builder.append(fn).append(",");
                }
                if(builder.length()>0){
                    builder.deleteCharAt(builder.length()-1);
                }
                queue.add(new AnalysisResult(requestID, TaskType.AUDIO_ALIGN, r.hasError(), builder.toString(), r));

            }
        };

        log.info("Sending audio alignment request for plugin: {}, domain: {}", plugin.getFirst().getId(), plugin.getSecond().getId());
        return ClientUtils.requestAudioAlignment(server, plugin, fileRegions, rc, true, transferType, propertyOptions, classIDs);

    }


    /**
     * Request removal of a plugin/domain
     * @param server
     */
    public boolean requestPluginRemoval(Server server, Pair<Olive.Plugin, Olive.Domain> plugin ){

        if(null == plugin){
            return false;
        }

        int removeRequstId = seq.getAndIncrement();

        // Create a callback to handle async plugin remove results from server
        Server.ResultCallback<Olive.RemovePluginDomainRequest, Olive.RemovePluginDomainResult> rc = new Server.ResultCallback<Olive.RemovePluginDomainRequest, Olive.RemovePluginDomainResult>() {

            @Override
            public void call(Server.Result<Olive.RemovePluginDomainRequest, Olive.RemovePluginDomainResult> r) {

                // do something with the results:
                if(!r.hasError()){
                    System.out.println(String.format("Received %s plugin removal request", r.getRep().getSuccessful() ? "successful" : "unsuccessful"));
                }
                else{
                    System.err.println(String.format("Plugin removal error: %s", r.getError()));
                }

                // Let main thread know the request has been received
                queue.add(new AnalysisResult(removeRequstId, TaskType.REMOVE, r.hasError(), null, r));
            }
        };


        System.out.println(String.format("Remove request id: %d", removeRequstId));
        try {
            return  ClientUtils.requestUnloadPlugin(server, plugin, rc, true);
        } catch (Exception e) {
            System.err.println(String.format("Failed to unload pluging %s-%s becuase: %s", plugin.getFirst().getId(), plugin.getSecond().getId(), e.getMessage()));
            return  false;
        }
    }

    /**
     * Request an audio conversion
     * @param server
     */
    public boolean requestAudioConversion(Server server,
                                     Pair<Olive.Plugin, Olive.Domain> plugin,
                                     String filename,
                                          Integer channelNum,
                                          List<RegionWord> regions) throws IOException, UnsupportedAudioFileException {

        if (null == plugin){
            return false;
        }

        int audioRequestId = seq.getAndIncrement();

        // Create a callback to handle async plugin remove results from server
        Server.ResultCallback<Olive.AudioModificationRequest, Olive.AudioModificationResult> rc = new Server.ResultCallback<Olive.AudioModificationRequest, Olive.AudioModificationResult>() {

            @Override
            public void call(Server.Result<Olive.AudioModificationRequest, Olive.AudioModificationResult> r) {

                // do something with the results:
                if(!r.hasError()){
                    System.out.println(String.format("Received %s audio conversion request", r.getRep().getSuccessful() ? "successful" : "unsuccessful"));

                    try {
                        //short [] samples = AudioUtil.convertBytes2Shorts(r.getRep().getModificationResult(0).getAudio().getData().toByteArray());

                        // Assume only one result?
                        for (Olive.AudioModification am : r.getRep().getModificationResultList()){
                            System.out.println(String.format("Converted %d audio files. Bit depth: %s, channels: %d, rate: %d, samples: %d", r.getRep().getModificationResultCount(),
                                    am.getAudio().getBitDepth(), am.getAudio().getChannels(), am.getAudio().getRate(), am.getAudio().getSamples()));

                            byte[] data  = am.getAudio().getData().toByteArray();
                            if(data.length/2 != am.getAudio().getSamples()){
                                log.error("NUMBER OF SAMPLES DOES NOT MATCH RETURNED VALUE: {}, {} ", data.length/2, am.getAudio().getSamples());
                            }
//                                ByteArrayOutputStream out = AudioUtil.convertSamples2Wav(data, data.length/2, 16000);
                            ByteArrayOutputStream out = AudioUtil.convertSamples2Wav(data, am.getAudio().getSamples(), am.getAudio().getRate());

                            String output_filename = Paths.get(filename).getFileName().toString();
//                        AudioUtil.saveSamples2Wave(out, Paths.get("test_a2a.wav"));
                            Path outputAudioPath = Paths.get(outputDirName.isEmpty() ? "OUTPUT" : outputDirName, "audio", output_filename);
                            if (outputAudioPath.toFile().mkdirs()) {
                                AudioUtil.saveSamples2Wave(out, outputAudioPath);
                            }
                            else {
                                if (outputAudioPath.toFile().exists()) {
                                    System.out.println("Unable to save audio output, because file '" + outputAudioPath.toString() + "' already exists.");
                                } else {
                                    System.out.println("Unable to create audio output at path: " + outputAudioPath.toString());
                                }
                            }

                            // Print metadata scores:
                            for(Olive.Metadata ms : am.getScoresList()){
                                //Message m = deserializers[scenicMessage.getMessageType().getNumber()].run(bs)
                                Server.MetadataWrapper mw = server.deserializeMetadata(ms);
                                System.out.println(String.format("Metadata score: %s = %s, type: {}", ms.getName(), mw.toString(), ms.getType()));
                            }
                        }

                        Olive.AudioModification am = r.getRep().getModificationResult(0);


                        //AudioUtil.saveSamples2Wave(samples, Paths.get("test_a2a.wav"));
//                        log.info("Saved converted audio to test_a2a.wav");
                    } catch (IOException e) {
                        System.err.println(String.format("Unable to saved converted audio becuase: %s", e));
                    }
                }
                else{
                    System.err.println(String.format("Audio conversion error: %s", r.getError()));
                }

                // Let main thread know the request has been received
                queue.add(new AnalysisResult(audioRequestId, TaskType.AUDIO, r.hasError(), filename, r));
            }
        };

        System.out.println(String.format("Convert audio request id: %d", audioRequestId));
        System.out.println(String.format("Using plugin: %s-%s", plugin.getFirst().getId(), plugin.getSecond().getId()));

        return ClientUtils.requestAudioEnhancement(server, plugin, filename, channelNum, rc, true, transferType, regions, propertyOptions);

    }

    /**
     * Request an audio conversion
     * @param server
     */
    public boolean requestAudioVector(Server server,
                                     Pair<Olive.Plugin, Olive.Domain> plugin,
                                     String filename,
                                      Integer channelNum,
                                      List<RegionWord> regions) throws IOException, UnsupportedAudioFileException {

        if(null == plugin){
            return false;
        }

        int audioRequestId = seq.getAndIncrement();

        // Create a callback to handle async   results from server
        Server.ResultCallback<Olive.PluginAudioVectorRequest, Olive.PluginAudioVectorResult> rc = new Server.ResultCallback<Olive.PluginAudioVectorRequest, Olive.PluginAudioVectorResult>() {

            @Override
            public void call(Server.Result<Olive.PluginAudioVectorRequest, Olive.PluginAudioVectorResult> r) {

                // do something with the results:
                if(!r.hasError()){
                    System.out.println(String.format("Received %d audio vector results", r.getRep().getVectorResultCount() ));

                    try {
                        //short [] samples = AudioUtil.convertBytes2Shorts(r.getRep().getModificationResult(0).getAudio().getData().toByteArray());

                        // Assume only one result
                        Olive.VectorResult vr = r.getRep().getVectorResult(0);

                        if(vr.getSuccessful() ){
                            Olive.AudioVector audioVector = vr.getAudioVector();
                            System.out.println(String.format("Received a valid audio vector for plugin: %s, domain %s, and total parameters %d", audioVector.getPlugin(), audioVector.getDomain(), audioVector.getParamsCount()));
                            for(Olive.Metadata meta : audioVector.getParamsList()){
                                Server.MetadataWrapper mw = server.deserializeMetadata(meta);
                                System.out.println(String.format("Metadata score: %s = %s, type: %s", meta.getName(), mw.toString(), meta.getType()));
                            }

                            // Assume there is one and only one data result
                            InputStream buffer = new ByteArrayInputStream(audioVector.getData().toByteArray());
                            Path workpath = FileSystems.getDefault().getPath( "vector.tar.gz");
                            System.out.println(String.format("Saving audio vector data to: %s", workpath.toString()));
                            Files.copy(buffer, workpath, StandardCopyOption.REPLACE_EXISTING);

                            String avName = "audio.vector";
                            System.out.println(String.format("Saving audio vector as %s", avName));
                            FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath( avName).toFile());
                            audioVector.writeTo(fos);
                        }
                        else{
                            System.err.println(String.format("Invalid audio vectorize request: %s", vr.getMessage()));
                        }


                    } catch (IOException e) {
                        System.err.println(String.format("Unable to save vectorized audio becuase: %s", e));
                    }
                }
                else{
                    System.err.println(String.format("Audio vector error: %s", r.getError()));
                }

                // Let main thread know the request has been received
                queue.add(new AnalysisResult(audioRequestId, TaskType.AUDIO, r.hasError(), filename, r));
            }
        };

        System.out.println(String.format("Using plugin: %s-%s", plugin.getFirst().getId(), plugin.getSecond().getId()));
        System.out.println(String.format("Audio vectorize request id: %s", audioRequestId));

        return ClientUtils.requestAudioVector(server, plugin, filename, channelNum, rc, true, transferType, regions);

    }


    /**
     * Request region score analysis (KWS) using an audio buffer
     * @param server the SCENIC server handle
     * @param plugin the plugin/domain to use
     * @param filename the audio file to send
     *
     * @return scores
     *
     * @throws ClientException if there is a communicaiton error with the serve r
     */
    private boolean requestRegionScores(Server server, Pair<Olive.Plugin, Olive.Domain> plugin, String filename, Integer channelNum,
                                        List<RegionWord> regions) throws ClientException, IOException, UnsupportedAudioFileException {

        if (null == plugin){
            return false;
        }

        int kwsRequstId = seq.getAndIncrement();

        // Create a callback to handle async KWS results from server
        Server.ResultCallback<Olive.RegionScorerRequest, Olive.RegionScorerResult> rc = new Server.ResultCallback<Olive.RegionScorerRequest, Olive.RegionScorerResult>() {

            @Override
            public void call(Server.Result<Olive.RegionScorerRequest, Olive.RegionScorerResult> r) {

                // do something with the results:
                if(!r.hasError()){
                    System.out.println(String.format("Received %d region scores for '%s':", r.getRep().getRegionCount(), r.getReq().getAudio().getLabel()));
                    for(Olive.RegionScore rs : r.getRep().getRegionList()){
                        System.out.println(String.format("\t%s = %f.  From %.2f to %.2f ", rs.getClassId(), rs.getScore(), rs.getStartT(), rs.getEndT()));
                    }

                    try {
                        Pair<TaskType, String> key = new Pair<>(TaskType.REGION_SCORE, "");
                        AbstractReportWriter writer = rptMgr.getWriter(key, () -> new RegionScorerReportWriter(outputDirName, ""));
                        ((RegionScorerReportWriter)(writer)).addData(filename, r.getRep().getRegionList());

                    } catch (ReportException e) {
                        System.err.println(String.format("Unable to write region score results because: %s", e.getMessage()));
                    }

                }
//                else{
//                    System.out.println(String.format("Region scoring error: %s", r.getError()));
//                }

                // Let main thread know the request has been received
                queue.add(new AnalysisResult(kwsRequstId, TaskType.REGION_SCORE, r.hasError(), filename, r));
            }
        };

        if(regions.size() > 0){
            System.out.println(String.format("Region scoring analysis of file '%s' for channel %d having %d regions:",filename, channelNum, regions.size() ));
            for(RegionWord rw : regions){
                System.out.println(String.format("\tFrom %f to %f ", rw.getStartTimeSeconds(), rw.getEndTimeSeconds()));
            }
        }
        else{
            System.out.println(String.format("Region scoring analysis of file '%s' for channel %d having NO regions",filename, channelNum ));
        }
        return ClientUtils.requestRegionScores(server, plugin, filename, channelNum, rc, true, transferType, regions, propertyOptions, classIDs);


    }

    private boolean preloadPlugin(Server server, Pair<Olive.Plugin, Olive.Domain> pd ){

        if(null == pd){
            return false;
        }

        Olive.LoadPluginDomainRequest.Builder req = Olive.LoadPluginDomainRequest.newBuilder().setPlugin(pd.getFirst().getId()).setDomain(pd.getSecond().getId());

        Server.Result<Olive.LoadPluginDomainRequest, Olive.LoadPluginDomainResult> result = server.synchRequest(req.build());

        if(result.hasError()){
            log.error("Preload Plugin {} request failed:  {}", pd.getFirst().getId() + "-" + pd.getSecond().getId(), result.getError());
            return false;
        }

        return result.getRep().getSuccessful();

    }


    private void printSpeechRegions(Olive.FrameScores fs ){

        // Print something like this:
        // "Speech regions: 1.004 to 2.00, 3.45 to 44

        List<SADWord> words = ClientUtils.thresholdFrames(fs, thresholdNumber);
        StringBuilder builder = new StringBuilder("Speech regions: ");
        int wordCount = 0;
        for (SADWord word : words){
            builder.append(String.format(" %.2f to %.2f,", word.start/100.0, word.end/100.0));
            wordCount++;
            //builder.append(String.format(" %d to %d,", word.start, word.end));
        }

        if (wordCount == 0){
            builder.append("None");
        }
        else if(builder.length() > 0) {
            // get rid of that last comma:
            builder.deleteCharAt(builder.length() - 1);
        }

        System.out.println(builder.toString());

    }


//    private Olive.Audio.Builder createAudioFromFile(String filename) throws IOException, UnsupportedAudioFileException {
//        AudioInputStream ais = AudioUtil.convertWave2Stream(Paths.get(filename).toFile());
//
//        byte[] samples =  AudioUtil.convertWav2ByteArray(ais);
//        Olive.AudioBuffer.Builder abuff = Olive.AudioBuffer.newBuilder()
//                .setChannels(1)
//                .setRate((int)ais.getFormat().getSampleRate())
//                .setSamples(samples.length)
//                .setBitDepth(ClientUtils.getAudioDepth(ais.getFormat()));
//        abuff.setData(ByteString.copyFrom(samples));
//        return Olive.Audio.newBuilder().setAudioSamples(abuff.build());
//    }



}
