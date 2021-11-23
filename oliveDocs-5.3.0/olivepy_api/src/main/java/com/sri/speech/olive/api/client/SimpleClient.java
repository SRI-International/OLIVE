package com.sri.speech.olive.api.client;


import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Server;
import com.sri.speech.olive.api.utils.*;
import com.sri.speech.olive.api.utils.parser.RegionParser;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
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
 * Simple example of using the Scenic API to make SAD, SID, LID, and enrollment requests.
 * Both asynchronous and synchronous callbacks from the SCENIC are demonstrated.
 *
 * No longer so simple...
 *
 * @deprecated  Use OliveAnalyze or OliveEnroll
 */
public class SimpleClient {

    private static Logger log = LoggerFactory.getLogger(SimpleClient.class);
    private static final int TIMEOUT                = 10000;
    private static final String DEFAUL_SERVERNAME   = "localhost";
    private static final int DEFAULT_PORT           = 5588;

    final static AtomicInteger seq = new AtomicInteger();

    // Command line options
    static final String helpOptionStr = "h";

    // consider using global, region, etc... instead of option?  Is that even necessary?
    static final String sadOptionStr = "sad";
    static final String sidOptionStr = "sid";
    static final String lidOptionStr = "lid";
    static final String kwsOptionStr = "kws";
    static final String qbeOptionStr = "qbe";

    static final String frameOptionStr  = "frame";
    static final String globalOptionStr = "global";
    static final String regionOptionStr = "region";

    static final String wavOptionStr = "w";
    static final String audioVectorOptionStr = "v";  // Score from an audio vector
//    static final String enrollOptionStr     = "enroll";
//    static final String unenrollOptionStr   = "unenroll";
    static final String domainOptionStr     = "domain";
    static final String printOptionStr      = "print";
    static final String pluginOptionStr     = "plugin";
    static final String classesOptionStr    = "classes";
    static final String removeOptionStr     = "r";
    static final String audioOptionStr      = "audio"; // audio conversion
    static final String vectorOptionStr     = "vector"; // vector conversion
    static final String channelOptionStr    = "channel"; // vector conversion
    static final String thresholdOptionStr    = "threshold"; // scoring threshold
    static final String annotationOptionStr    = "annotation"; // a file containing regions
    static final String listOptionStr    = "list"; // a file containing regions

    static final String scenicOptionStr     = "s";
    static final String portOptionStr       = "p";
    static final String timeoutOptionStr    = "t";
    static final String loadOptionStr       = "l";



    // Common options

    static final String serializeOptionStr    = "serialized";  // send the file not the decoded audio

    // todo we might want to move this to a new utility
    static final String getUpdateStatusOptionStr    = "update_status";  // get the update status
    static final String applyUpdateOptionStr        = "apply_update";  // request a plugin update


    // Not yet supported, but provide an alternate speaker/class id when importing an enrollment model:
    //static final String enrollmentOptionStr     = "enrollment"; // enrollment class name (for importing)

    private static String scenicSeverName;
    private static int scenicPort;
//    private static String audioFileName;
    private static String audioVectorFileName;
//    private static String enrollmentModelFileName;
    private static String removeSpeakerName;
    private static String domainName;
    private static String pluginName;
    private static int timeout;
    private static boolean printPlugins = false;
    private static boolean printClasses = false;

    private static int channelNumber = -1;  // by default, assume non-stereo file(s) so a channel number is not specified
    private static int thresholdNumber = 0;
    private static RegionParser regionParser = new RegionParser();
    private static List<String> audioFiles = new ArrayList<>();

    static ClientUtils.AudioTransferType transferType;


    //public Olive.TraitType[] learningTraits =  {Olive.TraitType.SUPERVISED_ADAPTER, Olive.TraitType.SUPERVISED_TRAINER, Olive.TraitType.UNSUPERVISED_ADAPTER };

    // Recognition task types
    public enum TaskType {
        SAD,
        SID,
        LID,
        KWS,
        ENROLL,
        UNENROLL,
        PRELOAD,
        REMOVE,
        AUDIO,
        VECTOR,
        IMPORT,
        EXPORT,
        QBE,
        REGION_SCORE,
        FRAME_SCORE,
        GLOBAL_SCORE,
        APPLY_UPDATE,
        GET_UPDATE_STATUS
    }


    // Stores requested tasks
    static final List<TaskType> taskList = new ArrayList<>();


    // Helper class to track score request/results
    public class ScenicResult{

        private int id;
        boolean isError;
        TaskType type;

        public ScenicResult(int id, TaskType type, boolean error){
            this.id = id;
            this.type = type;
            this.isError = error;
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
    }

    // Async request add result to this queue
    public BlockingQueue<ScenicResult> queue = new ArrayBlockingQueue<>(4);

    /**
     * Main execution point
     *
     * @throws Exception if there was an error
     */
    public static void main(String[] args) throws Exception {

        parseCommandLineArgs(args);

        // Setup the connection to the (scenic) server
        Server server = new Server();
        server.connect("scenic-ui", scenicSeverName,
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
            log.error("Unable to connect to the SCENIC server: {}", scenicSeverName);
            throw new Exception("Unable to connect to server");
        }

        // Perform task(s)
        SimpleClient sc = new SimpleClient();
        sc.handleRequests(server);

    }

    private static String getShortProgramName() {
        return "SimpleClient";
    }

    private static CommandLine parseCommandLineArgs(String args[]) {

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        Options options = new Options();

        options.addOption(helpOptionStr,    false, "Print this help message");
        options.addOption(scenicOptionStr,  "server",   true, "Scenicserver hostname. Default is " + DEFAUL_SERVERNAME);
        options.addOption(portOptionStr,    "port",     true, "Scenicserver port number. Defauls is " + DEFAULT_PORT);


        options.addOption(timeoutOptionStr, "timeout",  true, "timeout (in seconds) when waiting for server response.  Default is 10 seconds");
        options.addOption(loadOptionStr,    "load",     false,"load a plugin now, must use --plugin and --domain to specify the plugin/domain to preload");
        options.addOption(removeOptionStr,  "remove",   false,"remove a loaded plugin now, must use --plugin and --domain to specify the plugin/domain to unload" );

        // Audio input options
        options.addOption(wavOptionStr,     "wav",      true, "NAME of the wav file (some pluigns may let you specify a audio vector instead)");
        options.addOption(audioVectorOptionStr,     "vec",      true, "PATH to a serialized AudioVector, for plugins that support audio vectors in addition to wav files");
        options.addOption(Option.builder().longOpt(listOptionStr).desc("Use an input list FILE having multiple filenames/regions").hasArg().build());

        // Long only options:
        options.addOption(Option.builder().longOpt(sadOptionStr).desc("Perform SAD analysis").build());
        options.addOption(Option.builder().longOpt(sidOptionStr).desc("Perform SID analysis").build());
        options.addOption(Option.builder().longOpt(lidOptionStr).desc("Perform LID analysis").build());
        options.addOption(Option.builder().longOpt(kwsOptionStr).desc("Perform KWS analysis").build());
        options.addOption(Option.builder().longOpt(qbeOptionStr).desc("Perform QbE analysis").build());
        options.addOption(Option.builder().longOpt(frameOptionStr).desc("Perform frame scoring analysis").build());
        options.addOption(Option.builder().longOpt(globalOptionStr).desc("Perform global scoring analysis").build());
        options.addOption(Option.builder().longOpt(regionOptionStr).desc("Perform region scoring analysis").build());


        options.addOption(Option.builder().longOpt(audioOptionStr).desc("Perform audio conversion").build());
        options.addOption(Option.builder().longOpt(vectorOptionStr).desc("Perform audio vectorization").build());
        options.addOption(Option.builder().longOpt(domainOptionStr).desc("Use Domain NAME").hasArg().build());
        options.addOption(Option.builder().longOpt(pluginOptionStr).desc("Use Plugin NAME").hasArg().build());
        options.addOption(Option.builder().longOpt(printOptionStr).desc("Print all available plugins and domains").build());
        options.addOption(Option.builder().longOpt(classesOptionStr).desc("Print class names if also printing plugin/domain names.  Must use with --print option.  Default is to not print class IDs").build());

        //  audio processing
        options.addOption(Option.builder().longOpt(serializeOptionStr).desc("Serialize audio file and send it to the server instead of the decoded samples").build());
        options.addOption(Option.builder().longOpt(channelOptionStr).desc("Process stereo files using channel NUMBER").hasArg().build());
        options.addOption(Option.builder().longOpt(thresholdOptionStr).desc("Apply threshold NUMBER when scoring").hasArg().build());

        // Update options
        options.addOption(Option.builder().longOpt(applyUpdateOptionStr).desc("Request hte plugin is update (if supported)").build());
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

        // check if we should print plugin names
        if (cmd.hasOption(printOptionStr)) {
            printPlugins = true;

        }
        // check if we should print plugin names
        if (printPlugins && cmd.hasOption(classesOptionStr)) {
            printClasses = true;

        }

        // check for a threshold
        if (cmd.hasOption(thresholdOptionStr)) {
            try {
                thresholdNumber = Integer.parseInt(cmd.getOptionValue(thresholdOptionStr));
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
            scenicSeverName = cmd.getOptionValue(scenicOptionStr);
        }
        else {
            scenicSeverName = DEFAUL_SERVERNAME;
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
        if (cmd.hasOption(sadOptionStr)) {
            taskList.add(TaskType.SAD);
            requireAudioInput = true;
        }
        if (cmd.hasOption(sidOptionStr)) {
            taskList.add(TaskType.SID);
            requireAudioInput = true;
        }
        if (cmd.hasOption(lidOptionStr)) {
            taskList.add(TaskType.LID);
            requireAudioInput = true;
        }
        if (cmd.hasOption(kwsOptionStr)) {
            taskList.add(TaskType.KWS);
            requireAudioInput = true;
        }
        if (cmd.hasOption(qbeOptionStr)) {
            taskList.add(TaskType.QBE);
            requireAudioInput = true;
        }
        if (cmd.hasOption(frameOptionStr)) {
            taskList.add(TaskType.FRAME_SCORE);
            requireAudioInput = true;
        }
        if (cmd.hasOption(globalOptionStr)) {
            taskList.add(TaskType.GLOBAL_SCORE);
            requireAudioInput = true;
        }
        if (cmd.hasOption(regionOptionStr)) {
            taskList.add(TaskType.REGION_SCORE);
            requireAudioInput = true;
        }

        if (cmd.hasOption(loadOptionStr)) {
            if(null == pluginName || null == domainName){
                System.err.println("Must specify both a plugin (--plugin) and domain (--domain) name to preload an pluign");
                printUsageAndExit(options);
            }
            taskList.add(TaskType.PRELOAD);
        }
        if (cmd.hasOption(removeOptionStr)) {
            if(null == pluginName || null == domainName){
                System.err.println("Must specify both a plugin (--plugin) and domain (--domain) name to remove/unload a pluign");
                printUsageAndExit(options);
            }
            taskList.add(TaskType.REMOVE);
        }
        if (cmd.hasOption(audioOptionStr)) {
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

        if (cmd.hasOption(serializeOptionStr)) {
            transferType = ClientUtils.AudioTransferType.SEND_SERIALIZED_BUFFER;
        }
        else{
            transferType = ClientUtils.AudioTransferType.SEND_SAMPLES_BUFFER;
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
            regionParser.parse(listInputFilename);
            if (!regionParser.isValid()){
                System.err.println("Invalid list input file: " + listInputFilename);
                printUsageAndExit(options);
            }
            audioFiles.addAll(regionParser.getFilenames());   // note - this might be empty but will have regions for wavOptionStr

        }
        // In addition, accept the wave filename from the comand line
        if (cmd.hasOption(wavOptionStr) ) {
            String audioFileName = cmd.getOptionValue(wavOptionStr);
            // Make sure file exists
            if (!Files.exists(Paths.get(audioFileName).toAbsolutePath())){
                    System.err.println("ERROR: Wave file '" + audioFileName + "' does not exist");
                    printUsageAndExit(options);
            }
            audioFiles.add(audioFileName);
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
        /*else if (cmd.hasOption(enrollmentOptionStr)) {
            // Use an enrollment model
            enrollmentModelFileName = cmd.getOptionValue(enrollmentOptionStr);
            // Make sure file exists
            if (!Files.exists(Paths.get(enrollmentModelFileName))){
                System.err.println("Audio vector file '" + enrollmentModelFileName + "' does not exist");
                printUsageAndExit(options);
            }
        }*/
        else
        {
            if(!printPlugins && taskList.size() > 0) {
                if(taskList.contains(TaskType.EXPORT) && taskList.size() > 1) {
                    System.err.println("Missing required argument: wave file name (or audio vector name)");
                    printUsageAndExit(options);
                }
            }
        }



        // Check that tasks require audio/vector input  have such an input
        if(requireAudioInput /*taskList.contains(TaskType.SAD) || taskList.contains(TaskType.LID) || taskList.contains(TaskType.SID) || taskList.contains(TaskType.KWS)*/ ){
            if(audioFiles.size() == 0  && null == audioVectorFileName){

                System.err.println(String.format("Requested task(s) '%s' required an audio input.  Add a wave file name (or audio vector name - if supported)", taskList.toString()));
                printUsageAndExit(options);
            }
            // Make sure a wav or vector file is specified

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

            // First request current plugins (using a synchronous/blocking request)
            List<Pair<Olive.Plugin, Olive.Domain>> pluginList =  requestPlugins(server, printPlugins, printClasses);

            // Next see if we need to preload a plugin
            if(taskList.contains(TaskType.PRELOAD)){

                Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomain(pluginName, domainName, pluginList);
                if(null == pd){
                    log.error("Could not preload plugin: '{}',  domain '{}' because no plugin/domain having that name is available to the server", pluginName, domainName);
                }

                if(preloadPlugin(server, pd)){
                    log.info("Successfully requested plugin: '{}' and domain '{}' for preload", pluginName, domainName);
                }
                else {
                    log.error("Unable to complete preload request for plugin: '{}' and domain '{}'", pluginName, domainName);
                }
            }

            int numAsyncRequest = 0;

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



            /*if (taskList.contains(TaskType.SID)) {

                Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, "SID", Olive.TraitType.GLOBAL_SCORER, pluginList);
                if (requestGlobalScores(server, pd, "SID", TaskType.SID, Olive.TraitType.AUDIO_VECTORIZER, audioVectorFileName)) {
                    numAsyncRequest++;
                }
            }*/

            // Now perform the SAD, SID, and/or LID request(s) - these request are  asynchronous.


            for(String audioFileName : audioFiles) {


                if (taskList.contains(TaskType.REMOVE)) {

                    Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomain(pluginName, domainName, pluginList);
                    if (null == pd) {
                        log.error("Could not remove (unload) plugin: '{}', domain '{}' because no plugin/domain matching that name is available to the server", pluginName, domainName);
                    }

                    if (requestPluginRemoval(server, pd)) {
                        numAsyncRequest++;
                        log.info("Successfully requested plugin: '{}' and domain '{}' for preload", pluginName, domainName);
                    } else {
                        log.error("Unable to complete preload request for plugin: '{}' and domain '{}'", pluginName, domainName);
                    }
                }




                if (taskList.contains(TaskType.SAD) || taskList.contains(TaskType.FRAME_SCORE)) {

                    String taskLabel = taskList.contains(TaskType.SAD) ? "SAD" : null;
                    Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, taskLabel, Olive.TraitType.FRAME_SCORER, pluginList);

                    // SAD Request
                    if (requestFrameScores(server, pd, audioFileName)) {
                        numAsyncRequest++;
                    }
                }

                if (taskList.contains(TaskType.SID)) {

                    Olive.TraitType tt = Olive.TraitType.GLOBAL_SCORER;

                    Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, "SID", Olive.TraitType.GLOBAL_SCORER, pluginList);
                    if (requestGlobalScores(server, pd, "SID", TaskType.SID, tt, audioFileName)) {
                        numAsyncRequest++;
                    }

                }

                if (taskList.contains(TaskType.LID) | taskList.contains(TaskType.GLOBAL_SCORE)) {

                    String label = taskList.contains(TaskType.LID) ? "LID" : null;

                    Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, label, Olive.TraitType.GLOBAL_SCORER, pluginList);

                    if (requestGlobalScores(server, pd, "Global Score", TaskType.GLOBAL_SCORE, Olive.TraitType.GLOBAL_SCORER, audioFileName)) {
                        numAsyncRequest++;
                    }

                }
                if (taskList.contains(TaskType.KWS) | taskList.contains((TaskType.QBE)) | taskList.contains(TaskType.REGION_SCORE)) {
                    log.info("Using region scoring domain: {}", domainName);

                    String label = taskList.contains(TaskType.KWS) ? "KWS" : taskList.contains(TaskType.QBE) ? "QBE" : null;

                    Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, label, Olive.TraitType.REGION_SCORER, pluginList);
                    if (requestRegionScores(server, pd, audioFileName)) {
                        numAsyncRequest++;
                    }
                }


                if (taskList.contains(TaskType.AUDIO)) {
                    log.info("Using AUDIO domain: {}", domainName);
                    if (requestAudioConversion(server, ClientUtils.findPluginDomainByTrait(pluginName, domainName, "AUDIO", Olive.TraitType.AUDIO_CONVERTER, pluginList), audioFileName)) {
                        numAsyncRequest++;
                    }
                }
                if (taskList.contains(TaskType.VECTOR)) {

                    log.info("Using AUDIO domain: {}", domainName);
                    if (requestAudioVector(server, ClientUtils.findPluginDomainByTrait(pluginName,
                            domainName,
                            null,
                            Olive.TraitType.AUDIO_VECTORIZER,
                            pluginList),
                            audioFileName)) {

                        numAsyncRequest++;
                    }


                }
            }


            int numTimeouts = 0;
            while(numAsyncRequest > 0){
                ScenicResult sr = queue.poll(timeout, TimeUnit.MILLISECONDS);

                if(null == sr) {
                    if (numTimeouts++ > 3) {
                        log.error("Timeout exceeded waiting for response.");
                        break;
                    }
                }
                else{
                    log.info("Received {} result for task: {} and id: {}", sr.isError ? "unsuccessful" : "successful", sr.getType().toString(), sr.getId());
                    /*if(sr.isError){
                        log.error("Request error message: {}", sr.);
                    }*/
                    numAsyncRequest--;
                }
            }

            log.info("");
            log.info("Scenic Simple Client finished.  Exiting...");
            System.exit(0);

        } catch (Exception e) {
            log.error("\nError creating the Scenic Simple Client", e);
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


    public List<Pair<Olive.Plugin, Olive.Domain>> requestPlugins(Server server, boolean printPlugins, boolean printClasses) throws ClientException {

        List<Pair<Olive.Plugin, Olive.Domain>> pluginList = ClientUtils.requestPlugins(server);
        if (printPlugins) {
            log.info("Found {} plugin/domainss:", pluginList.size());
            for (Pair<Olive.Plugin, Olive.Domain> pp : pluginList) {


                // Print trait(s):
                //for(Olive.Trait t : pp.getFirst().getTraitList()){
                //  for(Olive.OptionDefinition od: t.getOptionsList()){
                //  log.debug("Trait option name: '{}', Label: {}, Desc: {}, Type: {}, Default: {} ", od.getName(), od.getLabel(), od.getDesc(), od.getType(), od.getDefault());
                //  }
                Olive.Plugin p = pp.getFirst();
                Olive.Domain d = pp.getSecond();
                log.info("\t{}-{}: {}", p.getId(), d.getId(), d.getDesc());
                if (printClasses) {
                    log.info("\t\tSupported classes:");
                    // Classes (languages, speakers, keywords, etc) supported by this plugin/domain:
                    for (String c : d.getClassIdList()) {
                        log.info("\t\t\t {}", c);
                    }
                }

            }
        }


        if(printPlugins) {
            log.info("");
            log.info("");
            log.info("");
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

                        log.info("Update {}, previous update {}", r.getRep().getUpdateReady() ?  "ready": "not ready", f.format(cal.getTime()));
                    }
                    else {
                        log.info("Update {}, NO previous updates", r.getRep().getUpdateReady() ?  "ready" : "not ready");
                    }

                    for(Olive.Metadata ms : r.getRep().getParamsList()){
                        //Message m = deserializers[scenicMessage.getMessageType().getNumber()].run(bs)
                        Server.MetadataWrapper mw = server.deserializeMetadata(ms);
                        log.info("Update parameter: {} = {}, type: {}", ms.getName(), mw.toString(), ms.getType());
                    }

                }
                else {
                    log.error("SAD request failed: {}", r.getError());
                }

                // Let main thread know the SAD request has been received
                queue.add(new ScenicResult(requstId, TaskType.GET_UPDATE_STATUS, r.hasError()));
            }
        };

        return ClientUtils.requestGetUpdateStatus(server, pp, rc, false);

    }


    public boolean requestApplyUpdate(Server server, Pair<Olive.Plugin, Olive.Domain> pp) throws ClientException, IOException, UnsupportedAudioFileException {


        int requstId = seq.getAndIncrement();

        // Create a callback to handle async SAD results from the server, note that we pass sadRequstId to the callback as an example of bundling metadata with the score results
        Server.ResultCallback<Olive.ApplyUpdateRequest, Olive.ApplyUpdateResult> rc = new Server.ResultCallback<Olive.ApplyUpdateRequest, Olive.ApplyUpdateResult>() {

            @Override
            public void call(Server.Result<Olive.ApplyUpdateRequest, Olive.ApplyUpdateResult> r) throws InvalidProtocolBufferException {
                if(!r.hasError()){

                    log.info("Plugin updated: {}", r.getRep().getSuccessful());

                }
                else {
                    log.error("Plugin update request failed: {}", r.getError());
                }

                // Let main thread know the SAD request has been received
                queue.add(new ScenicResult(requstId, TaskType.APPLY_UPDATE, r.hasError()));
            }
        };

        return ClientUtils.requestApplyUpdate(server, pp, rc, false);

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
    public boolean requestFrameScores(Server server, Pair<Olive.Plugin, Olive.Domain> pp, String filename) throws ClientException, IOException, UnsupportedAudioFileException {

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
                    for(Olive.FrameScores fs : r.getRep().getResultList()){
                        // Assume only "speech" results returned (some SAD plugins may recognize non-speech)
                        log.info("Received {} frame scores for '{}'", fs.getScoreCount(), fs.getClassId());

                        printSpeechRegions(fs);
                    }
                }
                else {
                    log.error("SAD request failed: {}", r.getError());
                }

                // Let main thread know the SAD request has been received
                queue.add(new ScenicResult(sadRequstId, TaskType.SAD, r.hasError()));
            }
        };

        return ClientUtils.requestFrameScore(server, pp, filename, channelNumber, rc, true, transferType, regionParser.getRegions(filename), new ArrayList<>(), new ArrayList<>());


        //return true;
    }

    public List<SADWord> thresholdFrames(Olive.FrameScores fs, double thresh) {

        List<SADWord> segments = new ArrayList<>();
        int rate = fs.getFrameRate();
        boolean inSegment = false;
        int start = 0;

        Double[] scores = fs.getScoreList().toArray(new Double[fs.getScoreList().size()]);

        for (int i = 0; i < scores.length; i++) {
            if (!inSegment && scores[i] >= thresh) {
                inSegment = true;
                start = i;
            } else if (inSegment && (scores[i] < thresh || i == scores.length - 1)) {
                inSegment = false;
                int startT = (int) (100 * start / (double) rate);
                int endT = (int) (100 * i / (double) rate);
                SADWord word = new SADWord(startT, endT);
                segments.add(word);

            }
        }

        return segments;
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
                                       String filename
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
                    log.info("Received {} {} scores:", r.getRep().getScoreCount(), taskLabel);
                    for(Olive.GlobalScore gs : r.getRep().getScoreList()){
                        log.info("\t{} = {}", gs.getClassId(), gs.getScore());
                    }
                }
                else{
                    log.error("{} error: {}", r.getError(), taskLabel);
                }

                // Let main thread know the request has been received
                queue.add(new ScenicResult(requestID, tt, r.hasError()));

            }
        };

        log.info("Sending global score request for plugin: {}, domain: {}", plugin.getFirst().getId(), plugin.getSecond().getId());
        return ClientUtils.requestGlobalScore(server, plugin, trait, filename, channelNumber, rc, true, transferType, regionParser.getRegions(filename), new ArrayList<>(), new ArrayList<>());

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
                    log.info("Received {} plugin removal request", r.getRep().getSuccessful() ? "successful" : "unsuccessful");
                }
                else{
                    log.error("Plugin removal error: {}", r.getError());
                }

                // Let main thread know the request has been received
                queue.add(new ScenicResult(removeRequstId, TaskType.REMOVE, r.hasError()));
            }
        };


        log.info("Remove request id: {}", removeRequstId);
        try {
            return  ClientUtils.requestUnloadPlugin(server, plugin, rc, true);
        } catch (Exception e) {
            log.error("Failed to unload pluging {}-{} becuase: {}", plugin.getFirst().getId(), plugin.getSecond().getId(), e.getMessage());
            return  false;
        }
    }

    /**
     * Request an audio conversion
     * @param server
     */
    public boolean requestAudioConversion(Server server,
                                     Pair<Olive.Plugin, Olive.Domain> plugin,
                                     String filename) throws IOException, UnsupportedAudioFileException {

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
                    log.info("Received {} audio conversion request", r.getRep().getSuccessful() ? "successful" : "unsuccessful");

                    try {
                        //short [] samples = AudioUtil.convertBytes2Shorts(r.getRep().getModificationResult(0).getAudio().getData().toByteArray());

                        // Assume only one result
                        Olive.AudioModification am = r.getRep().getModificationResult(0);
                        log.info("Converted {} audio files. Bit depth: {}, channels: {}, rate: {}, samples: {}", r.getRep().getModificationResultCount(), am.getAudio().getBitDepth(), am.getAudio().getChannels(), am.getAudio().getRate(), am.getAudio().getSamples());

                        byte[] data  = am.getAudio().getData().toByteArray();
                        if(data.length/2 != am.getAudio().getSamples()){
                            log.error("NUMBER OF SAMPLES DOES NOT MATCH RETURNED VALUE: {}, {} ", data.length/2, am.getAudio().getSamples());
                        }
//                                ByteArrayOutputStream out = AudioUtil.convertSamples2Wav(data, data.length/2, 16000);
                        ByteArrayOutputStream out = AudioUtil.convertSamples2Wav(data, am.getAudio().getSamples(), am.getAudio().getRate());

                        AudioUtil.saveSamples2Wave(out, Paths.get("test_a2a.wav"));

                        // Print metadata scores:
                        for(Olive.Metadata ms : am.getScoresList()){
                            //Message m = deserializers[scenicMessage.getMessageType().getNumber()].run(bs)
                            Server.MetadataWrapper mw = server.deserializeMetadata(ms);
                            log.info("Metadata score: {} = {}, type: {}", ms.getName(), mw.toString(), ms.getType());
                        }

                        //AudioUtil.saveSamples2Wave(samples, Paths.get("test_a2a.wav"));
                        log.info("Saved converted audio to test_a2a.wav");
                    } catch (IOException e) {
                        log.error("Unable to saved converted audio becuase: ", e);
                    }
                }
                else{
                    log.error("Audio conversion error: {}", r.getError());
                }

                // Let main thread know the request has been received
                queue.add(new ScenicResult(audioRequestId, TaskType.AUDIO, r.hasError()));
            }
        };

        log.info("Convert audio request id: {}", audioRequestId);
        log.info("Using plugin: {}-{}", plugin.getFirst().getId(), plugin.getSecond().getId());

        return ClientUtils.requestAudioEnhancement(server, plugin, filename, channelNumber, rc, true, transferType, regionParser.getRegions(filename), new ArrayList<>());

    }

    /**
     * Request an audio conversion
     * @param server
     */
    public boolean requestAudioVector(Server server,
                                     Pair<Olive.Plugin, Olive.Domain> plugin,
                                     String filename) throws IOException, UnsupportedAudioFileException {

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
                    log.info("Received {} audio vector results", r.getRep().getVectorResultCount() );

                    try {
                        //short [] samples = AudioUtil.convertBytes2Shorts(r.getRep().getModificationResult(0).getAudio().getData().toByteArray());

                        // Assume only one result
                        Olive.VectorResult vr = r.getRep().getVectorResult(0);

                        if(vr.getSuccessful() ){
                            Olive.AudioVector audioVector = vr.getAudioVector();
                            log.info("Received a valid audio vector for plugin: {}, domain {}, and total parameters {}", audioVector.getPlugin(), audioVector.getDomain(), audioVector.getParamsCount());
                            for(Olive.Metadata meta : audioVector.getParamsList()){
                                Server.MetadataWrapper mw = server.deserializeMetadata(meta);
                                log.info("Metadata score: {} = {}, type: {}", meta.getName(), mw.toString(), meta.getType());
                            }

                            // Assume there is one and only one data result
                            InputStream buffer = new ByteArrayInputStream(audioVector.getData().toByteArray());
                            Path workpath = FileSystems.getDefault().getPath( "vector.tar.gz");
                            log.info("Saving audio vector data to: {}", workpath.toString());
                            Files.copy(buffer, workpath, StandardCopyOption.REPLACE_EXISTING);

                            String avName = "audio.vector";
                            log.info("Saving audio vector as {}", avName);
                            FileOutputStream fos = new FileOutputStream( FileSystems.getDefault().getPath( avName).toFile());
                            audioVector.writeTo(fos);
                        }
                        else{
                            log.error("Invalid audio vectorize request: {}", vr.getMessage());
                        }


                    } catch (IOException e) {
                        log.error("Unable to save vectorized audio becuase: ", e);
                    }
                }
                else{
                    log.error("Audio vector error: {}", r.getError());
                }

                // Let main thread know the request has been received
                queue.add(new ScenicResult(audioRequestId, TaskType.AUDIO, r.hasError()));
            }
        };

        log.info("Using plugin: {}-{}", plugin.getFirst().getId(), plugin.getSecond().getId());
        log.info("Audio vectorize request id: {}", audioRequestId);

        return ClientUtils.requestAudioVector(server, plugin, filename, channelNumber, rc, true, transferType, regionParser.getRegions(filename));

    }


    /**
     * Request region score analysis (KWS) using an audio buffer
     * @param server the SCENIC server handle
     * @param plugin the plugin/domain to use
     * @param filename the audio file to send
     *
     * @return scores
     *
     * @throws ClientException if there is a communicaiton error with the server
     */
    private boolean requestRegionScores(Server server, Pair<Olive.Plugin, Olive.Domain> plugin, String filename) throws ClientException, IOException, UnsupportedAudioFileException {

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
                    log.info("Received {} region scores:", r.getRep().getRegionCount());
                    for(Olive.RegionScore rs : r.getRep().getRegionList()){

                        log.info("\t{} = {}.  From {} to {} ", rs.getClassId(), rs.getScore(), rs.getStartT(), rs.getEndT());
                    }
                }
                else{
                    log.error("Region scoring error: {}", r.getError());
                }

                // Let main thread know the request has been received
                queue.add(new ScenicResult(kwsRequstId, TaskType.KWS, r.hasError()));
            }
        };

        return ClientUtils.requestRegionScores(server, plugin, filename, channelNumber, rc, true, transferType, regionParser.getRegions(filename), new ArrayList<>(), new ArrayList<>());



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

        List<SADWord> words = thresholdFrames(fs, thresholdNumber);
        StringBuilder builder = new StringBuilder("Speech regions: ");
        int wordCount = 0;
        for (SADWord word : words){
            builder.append(String.format(" %.2f to %.2f,", word.start/100.0, word.end/100.0));
            wordCount++;
            //builder.append(String.format(" %d to %d,", word.start, word.end));
        }

        // get rid of that last comma:
        if(builder.length() > 0) {
            builder.deleteCharAt(builder.length() - 1);
        }

        log.info(builder.toString());


    }



    /*************************************************************************/

    /***********/
    /***********/
    /***********/
    /***********/
    /***********/
    /***********/
    /***********/
    /***********/
    /***********/


    /* EXAMPLES USED IN API DOC */


    /***********/
    /***********/
    /***********/
    /***********/
    /***********/
    /***********/
    /***********/
    /***********/

    /*************************************************************************/



    public Olive.Audio packageAudioAsPath(String wavFileName,  int channelNumber, List<RegionWord> regions){

        Olive.Audio.Builder audioBuilder = Olive.Audio.newBuilder().setPath(wavFileName);

        // For multi-channel (stereo) audio we have the option of specifying the channel to process, otherwise the file is treated as mono (if stereo)
        if (channelNumber > 0) {
            audioBuilder.setSelectedChannel(channelNumber);
        }


        // Add optional region annotations
        if (null != regions) {
            for (RegionWord word : regions) {
                audioBuilder.addRegions(Olive.AnnotationRegion.newBuilder().setStartT(word.getStartTimeSeconds()).setEndT(word.getEndTimeSeconds()));
            }
        }


        return audioBuilder.build();

    }

    public Olive.Audio packageAudioAsSerializedBuffer(String wavFileName, int channelNumber, List<RegionWord> regions) throws IOException {


        // NOTE: the audio format is set to zeros or some default value when serializing an
        // audio file since these fields are required (but ignored by the server for serializing audio)
        byte[] serialized = Files.readAllBytes(Paths.get(wavFileName));
        Olive.AudioBuffer.Builder abuff = Olive.AudioBuffer.newBuilder()
                .setSerializedFile(true)
                //.setEncoding()  // Ignored for now - this is a future feature
                .setChannels(0)
                .setRate(0)
                .setSamples(0)
                .setBitDepth(Olive.AudioBitDepth.BIT_DEPTH_16);  // Again an
        abuff.setData(ByteString.copyFrom(serialized));

        Olive.Audio.Builder audioBuilder = Olive.Audio.newBuilder();

        // For multi-channel (stereo) audio we have the option of specifying the channel
        // to process, otherwise the file is treated as mono (if stereo)
        if (channelNumber > 0) {
            audioBuilder.setAudioSamples(abuff.build()).setSelectedChannel(channelNumber);
        }
        else {
            // process as mono
            audioBuilder.setAudioSamples(abuff.build());
        }

        // Add optional region annotations
        if (null != regions) {
            for (RegionWord word : regions) {
                audioBuilder.addRegions(Olive.AnnotationRegion.newBuilder()
                        .setStartT(word.getStartTimeSeconds()).setEndT(word.getEndTimeSeconds()));
            }
        }

        return audioBuilder.build();


    }

    public Olive.Audio packageAudioAsRawBuffer(String wavFileName, int channelNumber, List<RegionWord> regions) throws IOException, UnsupportedAudioFileException {


        AudioInputStream ais = AudioSystem.getAudioInputStream(Paths.get(wavFileName).toFile());

        byte[] samples = convertWav2Buffer(ais);

        Olive.AudioBuffer.Builder  abuff = Olive.AudioBuffer.newBuilder()
                //.setEncoding(Olive.AudioEncodingType.PCM16)  Does not need to be set - for future use
                .setChannels(ais.getFormat().getChannels())
                .setRate((int) ais.getFormat().getSampleRate())
                .setSamples(samples.length)
                .setBitDepth(Olive.AudioBitDepth.BIT_DEPTH_16);

        abuff.setData(ByteString.copyFrom(samples));

        Olive.Audio.Builder audioBuilder = Olive.Audio.newBuilder();

        // For multi-channel (stereo) audio we have the option of specifying the channel to process, otherwise the file is treated as mono (if stereo)
        if (channelNumber > 0) {
            audioBuilder.setAudioSamples(abuff.build()).setSelectedChannel(channelNumber);
        }
        else {
            // process as mono
            audioBuilder.setAudioSamples(abuff.build());
        }

        // Add optional region annotations
        if (null != regions) {
            for (RegionWord word : regions) {
                audioBuilder.addRegions(Olive.AnnotationRegion.newBuilder().setStartT(word.getStartTimeSeconds()).setEndT(word.getEndTimeSeconds()));
            }
        }

        return audioBuilder.build();


    }

    private static byte[] convertWav2Buffer(AudioInputStream is) throws IOException, UnsupportedAudioFileException {

        int bytesPerFrame = is.getFormat().getFrameSize();
        if (bytesPerFrame == AudioSystem.NOT_SPECIFIED) {
            bytesPerFrame = 1;
        }

        // Set an arbitrary buffer size of 1024 frames.
        int numBytes = 1024 * bytesPerFrame;
        byte[] inputBytes = new byte[numBytes];
        int numRead;
        int totalRead = 0;

        // Not sure how large the wave file is, so read into a temp buffer
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        while( (numRead = is.read(inputBytes, 0, inputBytes.length)) != -1){
            bout.write(inputBytes, 0, numRead);
            totalRead += numRead;
        }

        bout.close();
        return bout.toByteArray();

    }


    public static boolean requestFrameScores(Server server,
                                             Olive.Plugin  plugin,
                                             Olive.Domain domain,
                                             Olive.Audio audio,
                                             Server.ResultCallback<Olive.FrameScorerRequest, Olive.FrameScorerResult> rc,
                                             boolean async,
                                             List<Pair<String, String>> options,
                                             List<String> classIDs) throws ClientException, IOException, UnsupportedAudioFileException {


        Olive.FrameScorerRequest.Builder req = Olive.FrameScorerRequest.newBuilder()
                .setAudio(audio)
                .setPlugin(plugin.getId())
                .setDomain(domain.getId());

        // Process plugin options (if any)
        if(null != options) {
            for (Pair<String, String> p : options) {
                req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
            }
        }

        // And add any class IDS  - if any
        if(null != classIDs && classIDs.size() >0)
            req.addAllClassId(classIDs);

        //
        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else {

            Server.Result<Olive.FrameScorerRequest, Olive.FrameScorerResult> result = server.synchRequest(req.build());
            rc.call(result);
        }


        return true;
    }


    /**
     * Make a global score request
     *
     * @param server the OLIVE server
     * @param plugin the requested plugin to provide the score
     * @param domain the requested domain to provide the score
     * @param audio the audio submission
     * @param async true if this function should not block, waiting for a response from the server
     * @param rc the callback
     * @param options zero or more options to pass to the plugin
     * @param classIDs zero or more class IDs to pass to the plugin to filter the scores
     * @return
     * @throws ClientException
     * @throws IOException
     * @throws UnsupportedAudioFileException
     */
    public static boolean requestGlobalScore(Server server,
                                             Olive.Plugin  plugin,
                                             Olive.Domain domain,
                                             Olive.Audio audio,
                                             Olive.AudioVector vector,
                                             boolean async,
                                             Server.ResultCallback<Olive.GlobalScorerRequest, Olive.GlobalScorerResult> rc,
                                             List<Pair<String, String>> options,
                                             List<String> classIDs) throws ClientException, IOException, UnsupportedAudioFileException {



        Olive.GlobalScorerRequest.Builder req = Olive.GlobalScorerRequest.newBuilder()
                .setPlugin(plugin.getId())
                .setDomain(domain.getId());

        // Add any options, if there are any
        if(null != options) {
            for (Pair<String, String> p : options){
                req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
            }
        }

        // And add any class IDS  - if any
        if(null != classIDs && classIDs.size() >0)
            req.addAllClassId(classIDs);

        if(null != vector){
            req.setVector(vector);
        }
        else {
            req.setAudio(audio);
        }


        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else {

            Server.Result<Olive.GlobalScorerRequest, Olive.GlobalScorerResult> result = server.synchRequest(req.build());
            rc.call(result);
        }


        return true;

    }

    /**
     * Used to make a region score request.
     *
     * @param server the connection to the OLIVE server
     *
     * @param plugin the plugin to use for scoring
     * @param domain the domain to use  for scoring
     * @param audio the audio to submit for scoring
     * @param rc a callback to be invoked when the score result is returned from the server.
     * @param async true, the  want the server to call back
     * @param options zero or more optional name/value properties to send to the plugin
     * @param classIDs zero or more class IDs (languages or speakers) to send to the plugin to limit results
     * @return
     * @throws ClientException
     * @throws IOException
     * @throws UnsupportedAudioFileException
     */
    public static boolean requestRegionScores(Server server,
                                              Olive.Plugin  plugin,
                                              Olive.Domain domain,
                                              Olive.Audio audio,
                                              Server.ResultCallback<Olive.RegionScorerRequest, Olive.RegionScorerResult> rc,
                                              boolean async,
                                              List<Pair<String, String>> options,
                                              List<String> classIDs) throws ClientException, IOException, UnsupportedAudioFileException {


        Olive.RegionScorerRequest.Builder req = Olive.RegionScorerRequest.newBuilder()
                .setAudio(audio)
                .setPlugin(plugin.getId())
                .setDomain(domain.getId());

        // Add any options, if there are any
        if(null != options) {
            for (Pair<String, String> p : options){
                req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
            }
        }

        // And add any class IDS  - if any
        if(null != classIDs && classIDs.size() >0)
            req.addAllClassId(classIDs);


        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else {

            Server.Result<Olive.RegionScorerRequest, Olive.RegionScorerResult> result = server.synchRequest(req.build());
            rc.call(result);
        }

        return true;

    }


    /**
     * Make a class enrollment request
     * @param server the server object
     * @param plugin the plugin
     * @param  domain the domain to use for enrollment
     * @param id the class id (usually a speaker name, but will vary for non SID plugins) to enroll
     * @param rc the call back function
     * @param async true if this call should be asynchronous
     * @param options zero or more optional parameters for the plugin
     * @return true if submitted
     *
     * @throws ClientException
     */
    public static boolean requestEnrollClass(Server server,
                                             Olive.Plugin plugin,
                                             Olive.Domain domain,
                                             String id,
                                             Olive.Audio audio,
                                             Server.ResultCallback<Olive.ClassModificationRequest, Olive.ClassModificationResult> rc,
                                             boolean async,
                                             List<Pair<String, String>> options) throws ClientException {


        try {
            // NOTE: not shown here, but one could submit multiple audio submission for the same class id:
            Olive.ClassModificationRequest.Builder req = Olive.ClassModificationRequest.newBuilder()
                    .setClassId(id)
                    .addAddition(audio)
                    .setPlugin(plugin.getId())
                    .setDomain(domain.getId());

            // Add options, if there are options.  Supported options vary by plugin
            if (null != options) {
                for (Pair<String, String> p : options) {
                    req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
                }
            }

            // Either make a sync or async request
            if(async){
                server.enqueueRequest(req.build(), rc);
            }
            else {
                Server.Result<Olive.ClassModificationRequest, Olive.ClassModificationResult> result = server.synchRequest(req.build());
                rc.call(result);
            }

        } catch (Exception e) {
            log.error("Enrollment failed because: {}", e);
            return  false;
        }

        return true;

    }


    public static boolean requestApplyUpdate(Server server,
                                             Olive.Plugin plugin,
                                             Olive.Domain domain,
                                             Server.ResultCallback<Olive.ApplyUpdateRequest, Olive.ApplyUpdateResult> rc,
                                             boolean async) throws ClientException, IOException, UnsupportedAudioFileException {



        Olive.ApplyUpdateRequest.Builder req = Olive.ApplyUpdateRequest.newBuilder()
                .setPlugin(plugin.getId())
                .setDomain(domain.getId());


        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else {
            Server.Result<Olive.ApplyUpdateRequest, Olive.ApplyUpdateResult> result = server.synchRequest(req.build());
            rc.call(result);
        }


        return true;
    }

    public static boolean requestUpdateStatus(Server server,
                                             Olive.Plugin plugin,
                                             Olive.Domain domain,
                                             Server.ResultCallback<Olive.GetUpdateStatusRequest, Olive.GetUpdateStatusResult> rc,
                                             boolean async) throws ClientException, IOException {




        Olive.GetUpdateStatusRequest.Builder req = Olive.GetUpdateStatusRequest.newBuilder()
                .setPlugin(plugin.getId())
                .setDomain(domain.getId());


        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else {
            Server.Result<Olive.GetUpdateStatusRequest, Olive.GetUpdateStatusResult> result = server.synchRequest(req.build());
            // Manually invoke the callback
            rc.call(result);
        }


        return true;
    }


    public void requestSAD(Server server, Olive.Plugin  plugin, Olive.Domain domain, String wavFilename)
            throws IOException, UnsupportedAudioFileException, ClientException {


        // There are three options for sending audio to the server for frame scoring:

        // 1. Send the path to the audio file to the server (assumes
        // the server and client share a common filesystem)
        //Olive.Audio audio = packageAudioAsPath(wavFilename, 0, null);

        // 1. Send the raw audio to the server (default behavior)
        Olive.Audio audio = packageAudioAsRawBuffer(wavFilename, 0, null);

        // 2/ Sned the serialized file to the server for processing
        //Olive.Audio audio = packageAudioAsSerializedBuffer(wavFilename, 0, null);


        // SAD is (usually) a frame scorer, so make a frame score request to perform SAD.
        // For simplicity this is made without any options or classID

        // But first create a callback that handles the frame score result from the server:
        Server.ResultCallback<Olive.FrameScorerRequest, Olive.FrameScorerResult> rc
                = new Server.ResultCallback<Olive.FrameScorerRequest, Olive.FrameScorerResult>() {

            @Override
            public void call(Server.Result<Olive.FrameScorerRequest, Olive.FrameScorerResult> r) {

                // do something with the results:
                if(!r.hasError()){
                    for(Olive.FrameScores fs : r.getRep().getResultList()){
                        // Assume only "speech" results returned (some SAD plugins may recognize
                        // non-speech but that is non-standard)
                        log.info("Received {} frame scores for '{}'", fs.getScoreCount(), fs.getClassId());

                        // Do something else with frame scores...
                    }
                }
                else {
                    log.error("Frame score request failed: {}", r.getError());
                }

            }
        };

        requestFrameScores(server, plugin, domain, audio, rc, false, null, null);



    }

    public void requestSID(Server server, Olive.Plugin  plugin, Olive.Domain domain, String enrollWavFilename, String scoreWaveFilename, String speakerName)
            throws IOException, UnsupportedAudioFileException, ClientException {


        // There are three options for sending audio to the server for enrollmet and scoring:

        // 1. Send the path to the audio file to the server (assumes
        // the server and client share a common filesystem)
        //Olive.Audio audio = packageAudioAsPath(wavFilename, 0, null);

        // 1. Send the raw audio to the server (default behavior)
        Olive.Audio audio = packageAudioAsRawBuffer(enrollWavFilename, 0, null);

        // 2/ Sned the serialized file to the server for processing
        //Olive.Audio audio = packageAudioAsSerializedBuffer(wavFilename, 0, null);


        // SID must have at least one enrolled speaker (class) before an enrollment can be made, so create an new enrollment first:

        // But first create a callback that handles the enrollment result from the server:
        Server.ResultCallback<Olive.ClassModificationRequest, Olive.ClassModificationResult> enrollmentCallback
                = new Server.ResultCallback<Olive.ClassModificationRequest, Olive.ClassModificationResult>() {

            @Override
            public void call(Server.Result<Olive.ClassModificationRequest, Olive.ClassModificationResult> r) {

                // do something with the results:
                if(!r.hasError()){
                    // todo handle error
                }
                else {
                    log.error("request failed: {}", r.getError());
                }

            }
        };

        // We make this a sync call, so that we know the speaker is enrolled before we make the score request below
        boolean enrolled = requestEnrollClass(server, plugin, domain, speakerName, audio, enrollmentCallback, false, null);

        if(enrolled){

            // Create a new Audio object from the score wave file.
            Olive.Audio scoreAudio = packageAudioAsRawBuffer(scoreWaveFilename, 0, null);
            // Also valid, but not used:
            //Olive.Audio scoreAudio = packageAudioAsPath(scoreWaveFilename, 0, null);
            //Olive.Audio scoreAudio = packageAudioAsSerializedBuffer(scoreWaveFilename, 0, null);

            // Create a call back to handle the SID
            // Create a callback to handle async results from server
            Server.ResultCallback<Olive.GlobalScorerRequest, Olive.GlobalScorerResult> scoreCallback = new Server.ResultCallback<Olive.GlobalScorerRequest, Olive.GlobalScorerResult>() {

                @Override
                public void call(Server.Result<Olive.GlobalScorerRequest, Olive.GlobalScorerResult> r) {

                    // do something with the results:
                    if(!r.hasError()){
                        log.info("Received {} scores:", r.getRep().getScoreCount());
                        for(Olive.GlobalScore gs : r.getRep().getScoreList()){
                            log.info("\t{} = {}", gs.getClassId(), gs.getScore());
                        }
                    }
                    else{
                        log.error("Global scorer error: {}", r.getError());
                    }

                }
            };

            // SID is a global scorer, so make a global score reqeust:
            requestGlobalScore(server, plugin, domain, scoreAudio, null, true, scoreCallback, null, null);

        }



    }

    public void requestGlobalCompare(Server server, Olive.Plugin  plugin, Olive.Domain domain, String audio_file_one, String audio_file_two)
            throws IOException, UnsupportedAudioFileException, ClientException {


        // Create new Audio objects from the two input files to comapre
        Olive.Audio audio_one = packageAudioAsRawBuffer(audio_file_one, 0, null);
        Olive.Audio audio_two = packageAudioAsRawBuffer(audio_file_two, 0, null);

        // Also valid, but not used:
        //Olive.Audio audio = packageAudioAsPath(audio_file_one, 0, null);
        //Olive.Audio audio = packageAudioAsSerializedBuffer(audio_file_two, 0, null);

        // Create a call back to handle the results of the audio comparison
        Server.ResultCallback<Olive.GlobalComparerRequest, Olive.GlobalComparerResult> rc = new Server.ResultCallback<Olive.GlobalComparerRequest, Olive.GlobalComparerResult>() {

            @Override
            public void call(Server.Result<Olive.GlobalComparerRequest, Olive.GlobalComparerResult> r) {

                // do something with the results:
                if(!r.hasError()){

                    // The real output of the compare result is the PDF report, but there is is the dictionary of results
                    // that can be evaluated if desired:
                    log.info("Comparision dictionary contains {} results", r.getRep().getResultsCount());


                    // The results can be extracted like this:
                    Map<String, Server.MetadataWrapper> results = new HashMap<>();
                    Olive.GlobalComparerResult result = r.getRep();
                    for(Olive.Metadata meta : result.getResultsList()){

                        try {
                            Server.MetadataWrapper mw = server.deserializeMetadata(meta);
                            log.info("Comapre metadata result: {} = {}", meta.getName(), mw.toString());
                            results.put(meta.getName(), mw);
                        } catch (InvalidProtocolBufferException e) {
                            log.error("Unsupported metadata type: {}", meta.getType());
                        }
                    }


                    // Assume there will be only one report
                    if(result.getReportCount() > 0 ) {

                        Olive.GlobalComparerReport report = result.getReport(0);

                        // Confirm the report is a PDF (so far only PDF reports are generated)
                        if (report.getType() == Olive.ReportType.PDF) {
                            String rptName = String.format("%s-%s.pdf", audio_file_one, audio_file_two);
                            // Save this file in the working directory for this example:
                            Path path = Paths.get("./", rptName);

                            try {
                                // Save the buffer as a PDF:
                                InputStream buffer = new ByteArrayInputStream(report.getReportData().toByteArray());
                                Files.copy(buffer, path, StandardCopyOption.REPLACE_EXISTING);

                            } catch (Exception e) {
                                log.error("Failed to save comparison report because: {}", e.getMessage());
                            }

                        }
                    }


                }
                else{
                    log.error("Global comparison failed with error: {}", r.getError());
                }

            }
        };

        Olive.GlobalComparerRequest.Builder req = Olive.GlobalComparerRequest.newBuilder()
                .setPlugin(plugin.getId())
                .setDomain(domain.getId())
                .setAudioOne(audio_one)
                .setAudioTwo(audio_two);


        // If you have options to pass to the plugin

        List<Pair<String, String>> option = new ArrayList<>();
        // for example: option.add(new Pair<>("foo", "bar"));
        for (Pair<String, String> p : option){
            req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
        }


        // Send the request to the server (callback above handles the result)
        server.enqueueRequest(req.build(), rc);

    }




    public void requestTID(Server server, Olive.Plugin  plugin, Olive.Domain domain, String enrollWavFilename, String scoreWaveFilename, String topicName)
            throws IOException, UnsupportedAudioFileException, ClientException {



        // First, create submit a topic as an enrollment


        // 1. Send the raw audio to the server (default behavior) - NOTE that we specify a region

        List<RegionWord> regions = new ArrayList<>();
        regions.add(new RegionWord(500, 2500));  // NOTE to add regions in milliseconds

        // Include regions when the audio is packaged in a protobuf message:
        Olive.Audio audio = packageAudioAsRawBuffer(enrollWavFilename, 0, regions);


        // Valid but not used here:
        //Olive.Audio audio = packageAudioAsSerializedBuffer(wavFilename, 0, regions);
        //Olive.Audio audio = packageAudioAsPath(wavFilename, 0, regions);

        // TID must have at least one enrolled speaker (class) before an enrollment can be made, so create an new enrollment first:

        // But first create a callback that handles the enrollment result from the server:
        Server.ResultCallback<Olive.ClassModificationRequest, Olive.ClassModificationResult> enrollmentCallback
                = new Server.ResultCallback<Olive.ClassModificationRequest, Olive.ClassModificationResult>() {

            @Override
            public void call(Server.Result<Olive.ClassModificationRequest, Olive.ClassModificationResult> r) {

                // do something with the results:
                if(!r.hasError()){
                    // todo handle error
                }
                else {
                    log.error("request failed: {}", r.getError());
                }

            }
        };

        // We make this a sync call, so that we know the topic is enrolled before we make the score request below
        boolean enrolled = requestEnrollClass(server, plugin, domain, topicName, audio, enrollmentCallback, false, null);
        if(!enrolled){
            // need to handle this error...
            log.error("Enrollment failed");
        }

        // BUT there are other enrollment options, for example you may want to enroll a submission as a "negative" example
        Olive.Audio negAudio = packageAudioAsRawBuffer(enrollWavFilename, 0, null);  // not specifying regions, since the target topic is not in the file
        // We reuse the above callback for to enroll this "negative" topic:

        List<Pair<String, String>> options = new ArrayList<>();
        options.add(new Pair<>("isNegative", "True"));

        enrolled = requestEnrollClass(server, plugin, domain, topicName, negAudio, enrollmentCallback, false, options);
        // if true, a negative example was enrolled


        if(enrolled){

            // Create a new Audio object from the score wave file.
            Olive.Audio scoreAudio = packageAudioAsRawBuffer(scoreWaveFilename, 0, null);
            // Also valid, but not used:
            //Olive.Audio scoreAudio = packageAudioAsPath(scoreWaveFilename, 0, null);
            //Olive.Audio scoreAudio = packageAudioAsSerializedBuffer(scoreWaveFilename, 0, null);

            // Create a callback to handle async results from server
            Server.ResultCallback<Olive.RegionScorerRequest, Olive.RegionScorerResult> scoreCallback
                    = new Server.ResultCallback<Olive.RegionScorerRequest, Olive.RegionScorerResult>() {

                @Override
                public void call(Server.Result<Olive.RegionScorerRequest, Olive.RegionScorerResult> r) {

                    // do something with the results:
                    if(!r.hasError()){
                        log.info("Received {} regions:", r.getRep().getRegionCount());
                        for(Olive.RegionScore rs : r.getRep().getRegionList()){
                            log.info("\t{} = {}, From {} to {}", rs.getClassId(), rs.getScore(), rs.getStartT(), rs.getEndT());
                        }
                    }
                    else{
                        log.error("Region scorer error: {}", r.getError());
                    }

                }
            };


            // Options that can be added (commented out) to the request - consult the Plugin reference guide for options supported by your plugin
            options.clear();
            /*options.add(new Pair<>("max_segment_length_sec", "0.4"));
            options.add(new Pair<>("sad_filter_length", "40"));
            options.add(new Pair<>("sad_interpolate", "2"));
            options.add(new Pair<>("sad_minimum_duration", "0.4"));
            options.add(new Pair<>("sad_speech_llr_threshold", "1.5"));
            options.add(new Pair<>("sad_speech_padding", "0.5"));
            options.add(new Pair<>("use_sad", "True"));*/
            // These are region scoring only parameters
            /*options.add(new Pair<>("padding", "5"));
            options.add(new Pair<>("threshold", "-2"));
            options.add(new Pair<>("td_thr_shift", "-0.5"));*/

            // TPD is a region scorer, so make a region score request:
            requestRegionScores(server, plugin, domain, scoreAudio, scoreCallback, true, options, null);

        }



    }


    public void requestLIDEnrollment(Server server, Olive.Plugin  plugin, Olive.Domain domain, String enrollWavFilename, String scoreWaveFilename, String className)
            throws IOException, UnsupportedAudioFileException, ClientException {


        // First prepare the audio for enrollment
        Olive.Audio audio = packageAudioAsRawBuffer(enrollWavFilename, 0, null);
        // Other ways to package audio:
        // Olive.Audio audio = packageAudioAsPath(wavFilename, 0, null);
        //Olive.Audio audio = packageAudioAsSerializedBuffer(wavFilename, 0, null);


        // But first create a callback that handles the enrollment result from the server:
        Server.ResultCallback<Olive.ClassModificationRequest, Olive.ClassModificationResult> enrollmentCallback
                = new Server.ResultCallback<Olive.ClassModificationRequest, Olive.ClassModificationResult>() {

            @Override
            public void call(Server.Result<Olive.ClassModificationRequest, Olive.ClassModificationResult> r) {

                // do something with the results:
                if(!r.hasError()){
                    // handle result
                }
                else {
                    log.error("enrollment request failed: {}", r.getError());
                }

            }
        };

        // We make this a sync call, so that we know the lanuage  is enrolled before we make the score request below
        boolean enrolled = requestEnrollClass(server, plugin, domain, className, audio, enrollmentCallback, false, null);




    }



    public void requestSIDorLIDUpdate(Server server, Olive.Plugin  plugin, Olive.Domain domain) throws UnsupportedAudioFileException, ClientException, IOException {


        // First, create two callback handlers.  One to handle the results of the update status request, and the second to handle the results of an update
        Server.ResultCallback<Olive.ApplyUpdateRequest, Olive.ApplyUpdateResult> updateCB
                = new Server.ResultCallback<Olive.ApplyUpdateRequest, Olive.ApplyUpdateResult>() {

            @Override
            public void call(Server.Result<Olive.ApplyUpdateRequest, Olive.ApplyUpdateResult> r) throws InvalidProtocolBufferException {
                if(!r.hasError()){

                    log.info("Plugin updated: {}", r.getRep().getSuccessful());

                }
                else {
                    log.error("Plugin update request failed: {}", r.getError());
                }

            }
        };


        // This callback will will request an update if the plugin reports that an update is possible
        Server.ResultCallback<Olive.GetUpdateStatusRequest, Olive.GetUpdateStatusResult> rc
                = new Server.ResultCallback<Olive.GetUpdateStatusRequest, Olive.GetUpdateStatusResult>() {

            @Override
            public void call(Server.Result<Olive.GetUpdateStatusRequest, Olive.GetUpdateStatusResult> r)
                    throws InvalidProtocolBufferException {

                // Make sure there was not an error
                if(!r.hasError()){

                    // Pretty print the date - if there is one
                    DateFormat f = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");

                    Olive.DateTime dt = null;
                    if(r.getRep().hasLastUpdate()) {
                        dt = r.getRep().getLastUpdate();
                        Calendar cal = Calendar.getInstance();
                        cal.set(dt.getYear(), dt.getMonth()-1, dt.getDay(), dt.getHour(), dt.getMin(), dt.getMin());
                        log.info("Update {}, previous update {}", r.getRep().getUpdateReady() ?  "ready": "not ready", f.format(cal.getTime()));
                    }
                    else {
                        log.info("Update {}, NO previous updates", r.getRep().getUpdateReady() ?  "ready" : "not ready");
                    }

                    for(Olive.Metadata ms : r.getRep().getParamsList()){
                        Server.MetadataWrapper mw = server.deserializeMetadata(ms);
                        log.info("Update: {} = {}, type: {}", ms.getName(), mw.toString(), ms.getType());
                    }

                    if(r.getRep().getUpdateReady()){
                        try {
                            requestApplyUpdate(server, plugin, domain, updateCB, true);
                        } catch (ClientException | IOException | UnsupportedAudioFileException e) {
                            log.error("Could not request ");
                        }
                    }

                }
                else {
                    log.error("SAD request failed: {}", r.getError());
                }


            }
        };


        // Now request the update status, and if an update is available then the callback, rc, will make the request
        requestUpdateStatus(server, plugin, domain, rc, true);


        }


    /**
     *
     * @param server
     * @param plugin
     * @param domain
     * @param newDomainName
     */
    public void requestAdaptation(Server server,
                                  Olive.Plugin  plugin,
                                  Olive.Domain domain,
                                 // Map<String, Olive.Audio> adaptList,
                                  String newDomainName){



        // Not shown here, but will need to add code that will create one or
        // more audio files plus a class ID to the adaptList like this:
        Map<String, Olive.Audio> adaptList = new HashMap<>();
        adaptList.put("English", packageAudioAsPath("enlish_train.wav", 0, null));


        Map<String, List<String>> annotations  = new HashMap<>();
        String adaptID = UUID.randomUUID().toString();

        int numPreprocessed = 0;
        for (String classID : adaptList.keySet()){

            //Server.ResultCallback<Olive.PreprocessAudioAdaptRequest, Olive.PreprocessAudioAdaptResult> cb = r -> log.info("Audio preprocessing done");

            // Submit audio for preprocessing, each audio successfully preprocessed is used to build up the annotations that are submited below when we finalize
            if (preprocessAudioForAdaptation(server, plugin, domain, classID, adaptID, adaptList.get(classID), annotations )){
                numPreprocessed++;
            }
        }

        // OR you could try this:
        /*int numSuccess = 0;
        String classIDName = "S";
        Olive.Audio audio = packageAudioAsPath("sad_speech.wav", 0, null);
        ///preprocessAudioForAdaptation(server, plugin, domain, classid, adaptID, submitAudio, annotations );

        // Prepare the request
        Olive.PreprocessAudioAdaptRequest.Builder req = Olive.PreprocessAudioAdaptRequest.newBuilder()
                .setPlugin(plugin.getId())
                .setDomain(domain.getId())
                .setAdaptSpace(adaptID)
                .setClassId(classIDName)
                // optionally specify a region in milliseconds:
                .setStartT(0)
                .setEndT(500)
                .setAudio(audio);

        Server.Result<Olive.PreprocessAudioAdaptRequest, Olive.PreprocessAudioAdaptResult> result = server.synchRequest(req.build());

        if (annotations.containsKey(classIDName)) {
            annotations.get(classIDName).add(result.getRep().getAudioId());
        } else {
            List<String> audioFiles = new ArrayList<>();
            audioFiles.add(result.getRep().getAudioId());
            annotations.put(classIDName, audioFiles);
        }

        if (!result.hasError()){
            numSuccess++;
        }


        classIDName = "NS";
        audio = packageAudioAsPath("sad_speech.wav", 0, null);
        ///preprocessAudioForAdaptation(server, plugin, domain, classid, adaptID, submitAudio, annotations );

        // Prepare the request
        Olive.PreprocessAudioAdaptRequest.Builder req = Olive.PreprocessAudioAdaptRequest.newBuilder()
                .setPlugin(plugin.getId())
                .setDomain(domain.getId())
                .setAdaptSpace(adaptID)
                .setClassId(classIDName)
                // optionally specify a region in milliseconds:
                .setStartT(2500)
                .setEndT(4500)
                .setAudio(audio);

        result = server.synchRequest(req.build());

        if (annotations.containsKey(classIDName)) {
            annotations.get(classIDName).add(result.getRep().getAudioId());
        } else {
            List<String> audioFiles = new ArrayList<>();
            audioFiles.add(result.getRep().getAudioId());
            annotations.put(classIDName, audioFiles);
        }

        if (!result.hasError()){
            numSuccess++;
        }

        // finalize adaptation ...  if done submitting adapt request and numSuccess is > 0*/

        ////

        // For this example, we go ahead and finalize if some of our audio was preprocessed,
        // but one may want to skip this step if one or more files could not be processed
        if (numPreprocessed>0){

            Server.ResultCallback<Olive.SupervisedAdaptationRequest, Olive.SupervisedAdaptationResult> rc = r -> log.info("New Domain Adapted");

            finalizeSupervisedAdaptation(server, plugin, domain, adaptID, newDomainName, rc, annotations);


        }

    }


    public boolean preprocessAudioForAdaptation(Server server,
                                             Olive.Plugin  plugin,
                                             Olive.Domain domain,
                                             String classIDName,
                                             String adaptID,
                                             Olive.Audio audio,
                                                Map<String, List<String>> annotations  ){

        // Prepare the request
        Olive.PreprocessAudioAdaptRequest.Builder req = Olive.PreprocessAudioAdaptRequest.newBuilder()
                .setPlugin(plugin.getId())
                .setDomain(domain.getId())
                .setAdaptSpace(adaptID)
                .setClassId(classIDName)
                .setAudio(audio);

        Server.Result<Olive.PreprocessAudioAdaptRequest, Olive.PreprocessAudioAdaptResult> result = server.synchRequest(req.build());

        if (annotations.containsKey(classIDName)) {
            annotations.get(classIDName).add(result.getRep().getAudioId());
        } else {
            List<String> audioFiles = new ArrayList<>();
            audioFiles.add(result.getRep().getAudioId());
            annotations.put(classIDName, audioFiles);
        }

        return !result.hasError();

    }

    public void finalizeSupervisedAdaptation(Server server,
                                              Olive.Plugin  plugin,
                                              Olive.Domain domain,
                                              String adaptID,
                                              String newDomainName,
                                             Server.ResultCallback<Olive.SupervisedAdaptationRequest, Olive.SupervisedAdaptationResult> cb,
                                             Map<String, List<String>> annotations){

        List<Olive.ClassAnnotation> classAnnotations = buildAnnotations(annotations);

        // Prepare the request
        Olive.SupervisedAdaptationRequest.Builder req = Olive.SupervisedAdaptationRequest.newBuilder()
                .setPlugin(plugin.getId())
                .setDomain(domain.getId())
                .setAdaptSpace(adaptID)
                .setNewDomain(newDomainName)
                .addAllClassAnnotations(classAnnotations);


        // Now send the finalize request
        server.enqueueRequest(req.build(), cb);
    }

    public List<Olive.ClassAnnotation> buildAnnotations(Map<String, List<String>> annotations){

        List<Olive.ClassAnnotation> classAnnotations = new ArrayList<>();
        for(String id: annotations.keySet()){

            List<Olive.AudioAnnotation> aaList = new ArrayList<>();
            for(String fileid : annotations.get(id)){

                // Build  the list of preprocessed audio IDs (there will be no regions)
                Olive.AudioAnnotation.Builder aaBuilder = Olive.AudioAnnotation.newBuilder().setAudioId(fileid);
                aaList.add(aaBuilder.build());
            }
            Olive.ClassAnnotation.Builder caBuilder = Olive.ClassAnnotation.newBuilder().setClassId(id).addAllAnnotations(aaList);
            classAnnotations.add(caBuilder.build());
        }

        return  classAnnotations;
    }

}

