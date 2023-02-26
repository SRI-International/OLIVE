package com.sri.speech.olive.api.client;


import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Server;
import com.sri.speech.olive.api.utils.ClientUtils;
import com.sri.speech.olive.api.utils.Pair;
import com.sri.speech.olive.api.utils.RegionWord;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example of using the Scenic API to submit sting data to the OLIVE server for translation.
 * Both asynchronous and synchronous callbacks from the SCENIC are demonstrated.
 *
 */
public class OliveAnalyzeVisual {

    private static Logger log = LoggerFactory.getLogger(OliveAnalyzeVisual.class);
    private static final int        TIMEOUT                = 10000;
    private static final String     DEFAUL_SERVERNAME   = "localhost";
    private static final int        DEFAULT_PORT           = 5588;

    private final static AtomicInteger seq = new AtomicInteger();

    // Command line options
    private static final String helpOptionStr = "h";
    private static final String imgOptionStr = "i";
    private static final String videoOptionStr = "v";
    private static final String domainOptionStr     = "domain";
    private static final String pluginOptionStr     = "plugin";
    private static final String outputOptionStr     = "output"; // the directory to write any output
    private static final String pathOptionStr       = "path";  // send the file path, not as a serialzied buffer

    // Standard options
    private static final String serverOptionStr     = "s";
    private static final String portOptionStr       = "p";
    private static final String timeoutOptionStr    = "timeout";
    // Option/properties file:
    private static final String optionOptionStr       = "options";


    private static String oliveSeverName;
    private static int scenicPort;
    private static String domainName;
    private static String pluginName;
    private static String outputDirName;
    private static int timeout;

    private static int thresholdNumber = 0;
    private static String imgInput ;
    private static String videoInput  ;
    // options
    private static String optionPropFilename;
    private static List<Pair<String, String>> propertyOptions = new ArrayList<>();
    private static List<String> classIDs = new ArrayList<>();

    private static ClientUtils.AudioTransferType transferType;

    // Stores requested tasks
    static final List<TaskType> taskList = new ArrayList<>();

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
        server.connect("OliveAnalyzeVisual", oliveSeverName,
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
        OliveAnalyzeVisual ota = new OliveAnalyzeVisual();
        ota.handleRequests(server);
    }

    private static String getShortProgramName() {
        return "OliveAnalyzeVisual";
    }

    private static CommandLine parseCommandLineArgs(String args[]) {

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        Options options = new Options();

        options.addOption(helpOptionStr,    false, "Print this help message");
        options.addOption(serverOptionStr,  "server",   true, "OLIVE cserver hostname. Default is " + DEFAUL_SERVERNAME);
        options.addOption(portOptionStr,    "port",     true, "OLIVE server port number. Defauls is " + DEFAULT_PORT);
        options.addOption(Option.builder().longOpt(outputOptionStr).desc("Write any output to DIR, default is ./").hasArg().build());
        options.addOption(Option.builder().longOpt(optionOptionStr).desc("Options from FILE ").hasArg().build());
        // data input options
        options.addOption(imgOptionStr,     "image",      true, "The Image submitted to the plugin for analysis");
        options.addOption(videoOptionStr,     "video",      true, "The Video submitted to the plugin for analysis");
        options.addOption(Option.builder().longOpt(pathOptionStr).desc("Send input file path instead of a serialized buffer.  Server and client must share a filesystem to use this option").build());

        // Long only options:
        options.addOption(Option.builder().longOpt(domainOptionStr).desc("Use Domain NAME").hasArg().build());
        options.addOption(Option.builder().longOpt(pluginOptionStr).desc("Use Plugin NAME").hasArg().build());
        options.addOption(Option.builder().longOpt(timeoutOptionStr).desc("timeout (in seconds) when waiting for server response.  Default is 10 seconds").hasArg().build());

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

        if (cmd.hasOption(imgOptionStr) ) {
            imgInput = cmd.getOptionValue(imgOptionStr);
        }

        if (cmd.hasOption(videoOptionStr) ) {
            if (null == imgInput)
            videoInput = cmd.getOptionValue(videoOptionStr);
        }

        if (imgInput == null && videoInput == null){
            System.err.println(String.format("Missing required argument: One of '%s' or '%s' must be specified", imgOptionStr, videoOptionStr));
            printUsageAndExit(options);
        }

        // Either send as a path or serialized buffer
        if (cmd.hasOption(pathOptionStr)) {
            transferType = ClientUtils.AudioTransferType.SEND_AS_PATH;
        }
        else{
            transferType = ClientUtils.AudioTransferType.SEND_SERIALIZED_BUFFER;
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

//        if (cmd.hasOption(classesOptionStr)) {
//            String[] classIDStrs = cmd.getOptionValue(classesOptionStr).split(",");
//            classIDs.addAll(Arrays.asList(classIDStrs));
//        }

        if (cmd.hasOption(pluginOptionStr)) {
            pluginName = cmd.getOptionValue(pluginOptionStr);

        }

        if (cmd.hasOption(domainOptionStr)) {
            domainName = cmd.getOptionValue(domainOptionStr);
        }

        // check if we are starting scenicserver
        if (cmd.hasOption(serverOptionStr)){
            oliveSeverName = cmd.getOptionValue(serverOptionStr);
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

            // There is only one request supported (for now?)
            // First request current plugins (using a synchronous/blocking request)
            List<Pair<Olive.Plugin, Olive.Domain>> pluginList =  requestPlugins(server, false, false);

            int numAsyncRequest = 0;


            Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, "TXT", Olive.TraitType.TEXT_TRANSFORMER, pluginList);

            String filename;
            boolean isVideo = false;
            if (null != imgInput)
                filename = imgInput;
            else{
                filename = videoInput;
                isVideo = true;
            }

            if (requestBoxAnalysis(server, pd, filename, true)){
                numAsyncRequest++;
            }


            int numErrors = 0;
            int numTimeouts = 0;

            if(numAsyncRequest == 0){
                System.err.println("OliveTextAnalyze finished without completing any analysis tasks.");
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


            System.out.println("");
            if(numErrors>0 || analysisFailures.size() > 0 ){

/*                if(analysisFailures.containsKey(TaskType.REGION_SCORE)
                        && !analysisSuccess.containsKey(TaskType.REGION_SCORE)){

                    RegionScorerReportWriter arw = new RegionScorerReportWriter(outputDirName, "");
                    arw.addError("No text transformations results received due to error(s_");


                }

                //assume they won't do both stereo and  mono scoring
                if(analysisFailures.containsKey(TaskType.REGION_SCORE_STEREO)
                        && !analysisSuccess.containsKey(TaskType.REGION_SCORE_STEREO)){

                    RegionScorerReportWriter arw = new RegionScorerReportWriter(outputDirName, "");
                    arw.addError("No regions found due to errors");

                }*/

                System.out.println(String.format("OliveTextAnalyze finished with %d errors.  Exiting...", numErrors));

            }
            else {

                // Often a region scorer will return no results
//                int rsCount = 0;
/*                if (analysisSuccessStatus.containsKey(TaskType.REGION_SCORE)) {
                  for (AnalysisResult ar : analysisSuccessStatus.get(TaskType.REGION_SCORE)) {
                    rsCount += ((Olive.RegionScorerResult) ar.result.getRep()).getRegionCount();
                  }
                  if (0 == rsCount) {
                    RegionScorerReportWriter arw = new RegionScorerReportWriter(outputDirName, "");
                    arw.addError("No regions found");
                    arw.close();
                  }
                }*/

                System.out.println("OliveTextAnalyze finished.  Exiting...");
            }


            System.exit(0);

        } catch (Exception e) {
            log.error("\nFatal Error:", e);
            System.out.println("OliveTextAnalyze fatal error.  Exiting...");
            System.exit(1);
        }
    }


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

                System.out.println(String.format("Plugin: %s (%s) has %d domain(s):", p.getId(), p.getTask(), plugins.get(p).size()));

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

    private String formatPluginErrorMsg(String pluginName, String domainName, Olive.TraitType type){;
        return String.format("No plugin-domain found having trait %s, plugin name: '%s' and domain: '%s' ", type.toString(), null == pluginName ? "*" : pluginName, null == domainName ? "*" : domainName);
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
                else{
                    System.out.println(String.format("Visual scoring error: %s", r.getError()));
                }

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

}
