package com.sri.speech.olive.api.client;


import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.StreamingServer;
import com.sri.speech.olive.api.stream.Stream;
import com.sri.speech.olive.api.Server;
import com.sri.speech.olive.api.utils.*;
import com.sri.speech.olive.api.utils.parser.ClassParser;
import com.sri.speech.olive.api.utils.reports.*;
import com.sri.speech.olive.api.workflow.OliveWorkflowDefinition;
import com.sri.speech.olive.api.workflow.wrapper.JobResult;
import com.sri.speech.olive.api.workflow.wrapper.TaskResult;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.file.*;
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
public class OliveStream {

    private static Logger log = LoggerFactory.getLogger(OliveStream.class);
    private static final int        TIMEOUT                = 10000;
    private static final String     DEFAUL_SERVERNAME   = "localhost";
//    private static final int        DEFAULT_PORT           = 5688;
    private static final int        DEFAULT_PORT           = 5588;

    private final static AtomicInteger seq = new AtomicInteger();

    // Command line options
    private static final String helpOptionStr = "h";


    private static final String inputOptionStr = "i";
    private static final String channelOptionStr    = "channel"; // vector conversion
    private static final String thresholdOptionStr  = "threshold"; // scoring threshold
    private static final String workflowptionStr    = "workflow"; // the workflow definition file
    private static final String outputOptionStr     = "output"; // the directory to write any output
    private static final String classesOptionStr    = "class_ids"; // the subset of classes to use when scoring

    private static final String serverHostOptionStr = "s";
    private static final String portOptionStr       = "p";
    private static final String timeoutOptionStr    = "t";
    // Option/properties file:
    private static final String optionOptionStr         = "options";
    // Other
    private static final String shutdownOptionStr         = "shutdown";
    private static final String stopOptionStr             = "stop";
    private static final String flushOptionStr             = "flush";

    // Common options
//    private static final String decodedOptionStr = "decoded";  // send the raw file as a buffer not the decoded audio
//    private static final String pathOptionStr         = "path";  // send the file path, not an audio buffer

    private static String oliveSeverName;
    private static int olivePort;
    private static String audioVectorFileName;
    private static String removeSpeakerName;
    private static String outputDirName;
    private static int timeout;

//    private static ClientUtils.AudioTransferType transferType;
    private static Integer channelNumber = -1;  // by default, assume non-stereo file(s) so a channel number is not specified
    private static int thresholdNumber = 0;
    private static boolean applyThresholdToFrameScores = false;

    // options
    private static boolean shutdownServer;
    private static boolean stopStreaming;
    private static boolean flushStreaming;
    private static String flushStreamingID;

    private static String optionPropFilename;
    private static List<Pair<String, String>> propertyOptions = new ArrayList<>();
    private static List<String> classIDs = new ArrayList<>();
    private String sessionID;

    private  boolean running = false;
    public BlockingQueue<Integer> audioRequestQueue = new ArrayBlockingQueue<>(100);

    // When sending audio in chunks:
    private boolean sendDataInChunks = true;  // todo make configurable
    private int lastSampleIndex = 0;
    private float lastSampleTime = 0.0F;

    // Stores requested tasks
    static final List<TaskType> taskList = new ArrayList<>();

    private static String fileName;
    private static String workflowName;

    public class StreamResultListener implements StreamingServer.StreamingMessageListener {

        private Collection<JobResult> completedResults = new ArrayList<>();
        private Collection<JobResult> failedResults = new ArrayList<>();

        public StreamResultListener(){

        }

        @Override
        public void receivedError(String error) {
            System.err.println(String.format("Received stream error: %s", error));
        }


        @Override
        public void receivedMessage(Map<String, JobResult> streamResult) {

            // We should only have one job...
            Collection<JobResult> jobResults = streamResult.values();
//            log.info("Received {} job results", jobResults.size());
            for (JobResult jr : jobResults){
                if (jr.isError()){
                    System.err.println(String.format("Job '%s' failed with error: '%s'  ",jr.getJobName(), jr.getErrMsg()));
                    failedResults.add(jr);
                }
                else {
                    completedResults.add(jr);
                }
                for(Collection<TaskResult> taskList : jr.getTasks().values()){
                    for (TaskResult tr: taskList){
                        if(tr.isError()){
                            System.err.println(String.format("Task '%s 'failed with error: '%s'", tr.getTaskName(), tr.getErrMsg()));
                        }
                        else {
                            System.out.println(String.format("Task '%s' = %s", tr.getTaskName(), tr.getTaskMessage().toString()));
                        }
                    }

                }
            }

        }

        public String writeResults(String path){

            try {
                StreamReportWriter writer = new StreamReportWriter(path);
                // Group results by type/task, then print all
                Collection<TaskResult> failedTasks = new ArrayList<>();
                // Index results by task name
                Map<String, List<String>> failedResults = new HashMap<>();
                Map<String, List<String>> processedResults = new HashMap<>();
                Map<String, String> resultHeaders = new HashMap<>();

                // First separate errors, and results, then results by task
                for (JobResult jr : completedResults){
                    // None of these jobs will be errors, but tasks could be..
                    for(Collection<TaskResult> taskList : jr.getTasks().values()){
                        for (TaskResult tr: taskList){
                            if(tr.isError()){
                                failedTasks.add(tr);
                                if (!failedResults.containsKey(tr.getTaskName() )) {
                                    failedResults.put(tr.getTaskName(), new ArrayList<>());
                                }
                                failedResults.get(tr.getTaskName()).add(tr.getErrMsg());
                            }
                            else {
                                // For all results, except text, I'm using comma separated values.  Text
                                // (esp if JSON text string) may contain commas, so a TAB is used as a separator
                                if (!processedResults.containsKey(tr.getTaskName() )){
                                    processedResults.put(tr.getTaskName(), new ArrayList<>());
                                    if (tr.getMessageType() == Olive.MessageType.REGION_SCORER_STREAMING_RESULT) {
                                        resultHeaders.put(tr.getTaskName(), "Region Start, Region End, Class ID, Score, Stream Start, Stream End, Group_Label, Offset");
                                    }
                                    else if (tr.getMessageType() == Olive.MessageType.GLOBAL_SCORER_STREAMING_RESULT) {
                                        resultHeaders.put(tr.getTaskName(), "ID, Score, Stream Start, Stream End, Offset");
                                    }
                                    else if (tr.getMessageType() == Olive.MessageType.TEXT_TRANSFORM_RESULT) {
                                        resultHeaders.put(tr.getTaskName(), "ID\t Text");
                                    }
                                }

                                if (tr.getMessageType() == Olive.MessageType.REGION_SCORER_STREAMING_RESULT){
                                    Stream.RegionScorerStreamingResult result = (Stream.RegionScorerStreamingResult)tr.getTaskMessage();
                                    for(Stream.RegionStreamingScore rss : result.getStreamRegionList()){
                                        for(Olive.RegionScore rs : rss.getRegionList()){
                                            float offset = 0;
                                            if (rss.hasOffsetT()){
                                                offset = rss.getOffsetT();
                                            }
                                            processedResults.get(tr.getTaskName()).add(String.format("%.2f, %.2f, %s, %.10f, %.2f, %.2f, %s, %.2f", rs.getStartT(), rs.getEndT(), rs.getClassId(), rs.getScore(), rss.getStartT(), rss.getEndT(), rss.getLabel(), offset));
                                        }
                                    }
                                }
                                else if (tr.getMessageType() == Olive.MessageType.GLOBAL_SCORER_STREAMING_RESULT){
                                    Stream.GlobalScorerStreamingResult result = (Stream.GlobalScorerStreamingResult)tr.getTaskMessage();
                                    for(Olive.GlobalScore gs : result.getScoreList()){
                                            float offset = 0;
                                            if (result.hasOffsetT()){
                                                offset = result.getOffsetT();
                                            }
                                            processedResults.get(tr.getTaskName()).add(String.format("%s %.10f %.2f %.2f %.2f", gs.getClassId(), gs.getScore(),  result.getStartT(), result.getEndT(), offset));
                                    }
                                }
                                else if (tr.getMessageType() == Olive.MessageType.TEXT_TRANSFORM_RESULT){
                                    Olive.TextTransformationResult result = (Olive.TextTransformationResult)tr.getTaskMessage();
                                    for(Olive.TextTransformation ts : result.getTransformationList()){
                                            processedResults.get(tr.getTaskName()).add(String.format("%s\t %s", ts.getClassId(), ts.getTransformedText()));
                                    }
                                }
                                else{
                                    log.warn("Unsupported result message type: {}", tr.getMessageType().name());
                                }

                            }
                        }

                    }
                }
                completedResults.clear();
                if (failedResults.size() > 0){
                    for (String errTask : failedResults.keySet()){
                        String errHeader = String.format("%s ERROR(s)",errTask);
                        System.out.println(errHeader);
                        writer.write(errHeader);
                        for (String errMsg : failedResults.get(errTask)){
                            System.out.println(errMsg);
                            writer.write(errMsg);
                        }
                    }
                    System.out.println("\n");
                    writer.write("\n");
                }
                for (String taskName : processedResults.keySet()){
                    System.out.println(taskName);
                    writer.write(taskName);
                    System.out.println(resultHeaders.get(taskName));
                    writer.write(resultHeaders.get(taskName));
                    for (String rec : processedResults.get(taskName)){
                        System.out.println(rec);
                        writer.write(rec);
                    }
                    System.out.println("\n");
                    writer.write("\n");
                }
                writer.close();
                return writer.getFilename();
            } catch (ReportException e) {
                e.printStackTrace();
            }

            return "";
        }
    }


    /**
     * Main execution point
     *
     */
    public static void main(String[] args)  {

        parseCommandLineArgs(args);

        // Setup the connection to the (scenic) server
        log.info("OliveStream using a server timeout of {} seconds", timeout/1000);
        Server server = new Server();
        server.connect("OliveStream", oliveSeverName,
                olivePort,
                olivePort +1,
                timeout/1000);    // may need to adjust timeout
        // wait for the connection
        long start_t = System.currentTimeMillis();
        while (!server.getConnected().get() && System.currentTimeMillis() - start_t < timeout) {
            try {
                synchronized (server.getConnected()) {
                    server.getConnected().wait(timeout);
                }
            } catch (InterruptedException e) {
                // Keep waiting
            }
        }

        if (!server.getConnected().get()) {
            log.error("Unable to connect to the OLIVE Streaming server: {}", oliveSeverName);
             System.err.println("Unable to connect to server");
        }

        // Perform task(s)
        OliveStream sc = new OliveStream();
        sc.handleRequests(server);

    }

    private static String getShortProgramName() {
        return "OliveStream";
    }

    private static CommandLine parseCommandLineArgs(String args[]) {

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        Options options = new Options();

        options.addOption(helpOptionStr,    false, "Print this help message");
        options.addOption(serverHostOptionStr,  "server",   true, "OLIVE Stream hostname. Default is " + DEFAUL_SERVERNAME);
        options.addOption(portOptionStr,    "port",     true, "OLIVE Stream port number. Default is " + DEFAULT_PORT);
        options.addOption(Option.builder().longOpt(outputOptionStr).desc("Write any output to DIR, default is ./").hasArg().build());
        options.addOption(Option.builder().longOpt(workflowptionStr).desc("NAME of the workflow definition file. Mutually exclusive option").hasArg().build());
        options.addOption(Option.builder().longOpt(shutdownOptionStr).desc("Request a clean shutdown of the (streaming) server. Mutually exclusive option").build());
        options.addOption(Option.builder().longOpt(stopOptionStr).desc("Request the server stop an active streaming session. Mutually exclusive option").build());
        options.addOption(Option.builder().longOpt(flushOptionStr).hasArg(true).desc("Request the server flush an active streaming SESSION_ID. Mutually exclusive option").build());

        options.addOption(timeoutOptionStr, "timeout",  true, "timeout (in seconds) when waiting for server response.  Default is 10 seconds");
        // Audio input options
        options.addOption(inputOptionStr,     "input",      true, "NAME of the input file (audio/video/image as required by the plugin");

        // Long only options:
//        options.addOption(Option.builder().longOpt(classesOptionStr).desc("Use Class(s) from FILE for scoring.  Each line in the file contains a single class, including any white space").hasArg().build());
        // audio processing
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

        if (cmd.hasOption(shutdownOptionStr)) {
            shutdownServer = true;
        }

        if (cmd.hasOption(stopOptionStr)) {
            stopStreaming = true;
        }

        if (cmd.hasOption(flushOptionStr)) {
            flushStreaming = true;
            flushStreamingID = cmd.getOptionValue(flushOptionStr);
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

        // check if we are starting scenicserver
        if (cmd.hasOption(serverHostOptionStr)){
            oliveSeverName = cmd.getOptionValue(serverHostOptionStr);
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

        if (cmd.hasOption(channelOptionStr)) {
            try {
                channelNumber = Integer.parseInt(cmd.getOptionValue(channelOptionStr));
            } catch (NumberFormatException e) {
                System.err.println("Ignoring non integer channel number: " + cmd.getOptionValue(channelOptionStr));
            }
        }

        if (cmd.hasOption(workflowptionStr) ) {
            String workflowFileName = cmd.getOptionValue(workflowptionStr);
            // Make sure file exists
            if (!Files.exists(Paths.get(workflowFileName).toAbsolutePath())){
                System.err.println("ERROR: Workflow file '" + workflowFileName + "' does not exist");
                printUsageAndExit(options);
            }
            workflowName = workflowFileName;
        }
        else {
            if(!(flushStreaming) && !shutdownServer && !stopStreaming) {
                System.err.println("Missing required argument: workflow file name");
                printUsageAndExit(options);
            }
        }



        if (cmd.hasOption(inputOptionStr) ) {
            String audioFileName = cmd.getOptionValue(inputOptionStr);
            // Make sure file exists
            if (!Files.exists(Paths.get(audioFileName).toAbsolutePath())){
                System.err.println("ERROR: Wave file '" + audioFileName + "' does not exist");
                printUsageAndExit(options);
            }
            // File only, not channel/class/regions?
            /*ChannelClassPair ccp = new ChannelClassPair(channelNumber);
            Map<ChannelClassPair, List<RegionWord>> r = new LinkedHashMap<>();
            r.put(ccp, new ArrayList<>());
            fileRegions.put(audioFileName, r);*/

            fileName = audioFileName;
        }
        else
        {
            if(!flushStreaming && !shutdownServer && !stopStreaming) {
                System.err.println(String.format("Missing one of the required arguments: %s, %s, %s, or %s", workflowptionStr, flushOptionStr, shutdownOptionStr, stopOptionStr));
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

            if(stopStreaming){
                // Send a request to stop an active streaming session... should let us recover from session that wasn't
                // properly closed
                // hack there is no session or client ID available, which is okay for now since the server doesn't
                // verify these values since we want to make it wasy to stop a streaming session while in development
                this.sessionID = "";
                requestStopStreaming(server, null);
                System.out.println("Sending a request to stop all streaming sessions.  Exiting...");
                System.exit(0);

            }

            if(shutdownServer){
                // Send a shutdown request to server, then exit
                server.shutdownServer();
                System.out.println("Sending a shutdown request to the server.  Exiting...");
                System.exit(0);

            }

            if(flushStreaming){
                // Send a shutdown request to server, then exit
                requestFlushStreaming(server, flushStreamingID);
                System.out.println(String.format("Sent flush request.  Exiting..."));
                System.exit(0);

            }

//            System.out.println("CLG making sure std out is also working");

            String clientID = "OliveStream Java Client";

            if(null != fileName){

                // Stream file contents  - we want to emulate sending in real-time, otherwise we are just sending a file

                audioRequestQueue.add(1);
                AudioInputStream ais = AudioUtil.convertWave2Stream(Paths.get(fileName).toFile());
                byte[] samples = AudioUtil.convertWav2ByteArray(ais);
                int sampleRate = (int)ais.getFormat().getSampleRate();
                double duration =  (double)samples.length/ais.getFormat().getFrameSize()/ais.getFormat().getSampleRate();
                Olive.AudioBitDepth bitDepth = ClientUtils.getAudioDepth(ais.getFormat());
                int numberChannels = ais.getFormat().getChannels();

                OliveWorkflowDefinition owd = new OliveWorkflowDefinition(workflowName);

                int streaming_port_number = requestStreaming(server, clientID, owd, sampleRate, bitDepth);
//                String restoreSessionID = this.sessionID;
                if (streaming_port_number > 0) {
                    System.out.println(String.format("Start streaming... using port: %d", streaming_port_number));
                }
                else{
                    //  Unable to start streaming
                    System.err.println("Unable to connect to streaming server");
                    System.exit(1);
                }

                // Request two sessions
//                int streaming_port_number2 = requestStreaming(server, clientID, owd, sampleRate, bitDepth);
//                if (streaming_port_number2 > 0) {
//                    System.out.println(String.format("Start streaming 2 (TWO)... using port: %d", streaming_port_number));
//                }
//                else{
//                    //  Unable to start streaming
//                    System.err.println("Unable to connect to streaming server");
//                    System.exit(1);
//                }
//                String secondSessionID = this.sessionID;
//                this.sessionID = restoreSessionID;

                log.info("OliveStream connecting to {}:{} using timeout of {} seconds", oliveSeverName, streaming_port_number, timeout/1000);
                StreamingServer stream = new StreamingServer();
                stream.connect(clientID, oliveSeverName, streaming_port_number, timeout/1000);


                StreamResultListener srListener = new StreamResultListener();
                stream.addMessageListener(srListener);


                // Run until stopped by the caller...
                running = true;
                boolean sendAudio = true;

                // Parameters for sending as chunks
                int bufferSizeMsecs = 100;
                final int chunkSizeSamples = (int)(sampleRate * bufferSizeMsecs / 1000.0);
                final int chunkSizeBytes = 2 * chunkSizeSamples;
                System.out.println("BufferSizeMsecs="+bufferSizeMsecs+", ChunkSizeBytes="+chunkSizeBytes);


                // Audio streaming thread
                Thread t = new Thread(() -> {
//                    System.out.println("Sending chunk");
                    while (running) {

                        try {
                            Integer request = audioRequestQueue.poll(timeout, TimeUnit.MILLISECONDS);
                            if (null != request) {
                                System.out.println(String.format("Sending %d requests", request));
                                if (!sendDataInChunks) {

                                    // send entire audio at once
                                    Olive.Audio.Builder audio = null;
                                    // Do we need to resend the sample rate, bit depth, etc if we include it in the streaming request?
                                    audio = ClientUtils.createAudioFromDecodedBytes(fileName, samples, numberChannels, sampleRate, channelNumber, bitDepth, new ArrayList<>());
                                    Thread.sleep((int) (duration * 1000));
                                    System.out.println("Sending entire audio now...");
                                    stream.enqueueAudio(audio.build());
                                    lastSampleIndex = samples.length / 2;
                                    lastSampleTime = (float) lastSampleIndex / (float) sampleRate;

                                } else {
                                    // Send data as chunks:
                                    int numBytesLeft = samples.length;
                                    int startByteIndex = 0;
                                    int endByteIndex = chunkSizeBytes;

                                    while (numBytesLeft > 0) {
                                        if (endByteIndex > samples.length) {
                                            endByteIndex = samples.length;
                                        }
                                        byte[] bytesToSend = Arrays.copyOfRange(samples, startByteIndex, endByteIndex);
                                        Olive.Audio.Builder audioChunk = null;
                                        audioChunk = ClientUtils.createAudioFromDecodedBytes(fileName, bytesToSend, numberChannels, sampleRate, channelNumber, bitDepth, new ArrayList<>());

                                        // Sleep appropriate time
                                        try {
                                            int sleep_msec = bufferSizeMsecs;
                                            Thread.sleep(sleep_msec);
                                            // System.out.println("Sleeping " + sleep_msec + " msec...");
                                        } catch (InterruptedException ei) {
                                            System.out.println("Got InterruptedException: " + ei);
                                        }

                                        // notify in samples
                                        int startSampleIndex = startByteIndex / 2;
                                        int endSampleIndex = endByteIndex / 2;
                                        double startByteMsecs = 1000.0 * ((double) startSampleIndex) / ((double) sampleRate);
                                        double endByteMsecs = 1000.0 * ((double) endSampleIndex) / ((double) sampleRate);
                                        System.out.println("Sending audio[" + chunkSizeSamples + "]=(" + startSampleIndex + "," + endSampleIndex + ") / (" + startByteMsecs + "," + endByteMsecs + ")");
                                        stream.enqueueAudio(audioChunk.build());

                                        lastSampleIndex = endSampleIndex;
                                        lastSampleTime = (float) lastSampleIndex / (float) sampleRate;


                                        startByteIndex += chunkSizeBytes;
                                        endByteIndex += chunkSizeBytes;
                                        numBytesLeft -= chunkSizeBytes;
                                        if (endByteIndex > samples.length) {
                                            endByteIndex = samples.length;
                                            numBytesLeft = endByteIndex - startByteIndex;
                                        }
                                        if (startByteIndex >= samples.length) {
                                            numBytesLeft = 0;
                                        }
                                    }
                                }
                                printPrompt();
                            }

                        } catch (InterruptedException e) {
                            // Done
                        } catch (IOException | UnsupportedAudioFileException  e) {
                            System.err.println(String.format("Unable to stream audio '%s' because: %s", fileName, e.getMessage()));
                        }
                        }

                });
                t.setName("Input");
                t.start();

                // this should be better, but just a quick hack for now
                Scanner scan = new Scanner(System.in);
                while (running){
                    printPrompt();
                    //System.out.println("******* Press 'q' to quit streaming, 'f' to flush stream, 'd' to drain stream, 'w' to write recent results, 'r' to resend the audio, then <ENTER> *****");
                    String s = scan.next();
                    if (s.toLowerCase().equals("q")) {
                        // Stop streaming..
                        System.out.println("Attempting to stop streaming session...");
                        running = false;
                        boolean stopped = requestStopStreaming(server, sessionID);
//                        boolean stopped2 = requestStopStreaming(server, secondSessionID);
                        if (!stopped){
                            System.err.println("Server failed to stop streaming");
                        }
                        else{
                            System.out.println(String.format("Streaming session '%s' closed.  Exiting...", sessionID));
                        }
//                        if (!stopped2){
//                            System.err.println("Server failed to stop SECOND streaming session");
//                        }
//                        else{
//                            System.out.println(String.format("Streaming session 2 '%s' closed.  Exiting...", sessionID));
//                        }
                    }
                    if (s.toLowerCase().equals("f")) {
                        // flush stream
                        System.out.println("********** Flushing streaming session... **********");
                        requestFlushStreaming(server, sessionID);
                    }
                    if (s.toLowerCase().equals("d")) {
                        // drain stream
                        System.out.println("********** Draining streaming session... **********");
                        requestDrainStreaming(server, sessionID);
                    }
                    if (s.toLowerCase().equals("r")) {
                        // Re-send the audio
                        System.out.println("Resending audio ...");
//                        stream.enqueueAudio(audio.build());
                        if (audioRequestQueue.remainingCapacity() > 0) {
                            audioRequestQueue.add(1);
                        }
                        else{
                            System.err.println("Too many pending audio requests.  Wait for processing to catch up.");
                        }
                    }
                    if (s.toLowerCase().equals("w")) {
                        String outputFilename = srListener.writeResults(outputDirName);
                        System.out.println("Wrote output report to: " + outputFilename);
                    }

                    // Check for results from the server or check if the user has requested
                    // our test file is ~10.5 seconds long
//                    Thread.sleep((int)(duration*1000));
//                    System.out.println("Resending audio...");
//                    stream.enqueueAudio(audio.build());
                    // fixme: check for results from OLIVE
                }

//                System.out.println("Wait 15 seconds for reply...");
//                Thread.sleep(15000);

            }
            else {
                System.out.println("No file specified for streaming");
            }


            System.out.println("Done...");


            System.exit(0);

        } catch (Exception e) {
            log.error("\nFatal Error:", e);
            System.out.println("\nFatal Error: " + e.getMessage());
            System.out.println("OliveStream fatal error.  Exiting...");
            System.exit(1);
        }
    }

    public void printPrompt(){
        System.out.println("******* Press 'q' to quit streaming, 'f' to flush stream, 'd' to drain stream, 'w' to write recent results, 'r' to resend the audio, then <ENTER> *****");
    }
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

    /**
     * Make a frame score request (usually SAD).
     *
     * @param server
     * @return
     * @throws ClientException
     */
    public int requestStreaming(Server server, String client_id, OliveWorkflowDefinition owd, int sampleRate, Olive.AudioBitDepth bitDepth) throws ClientException, IOException, UnsupportedAudioFileException {


        // TODO SET WORKFLOW
        Stream.StartStreamingRequest.Builder req = Stream.StartStreamingRequest.newBuilder()
                .setClientStreamId(client_id)
                .setWorkflowDefinition(owd.getWorkflowDefinition())
                .setSampleRate(sampleRate)
                .setBitDepth(bitDepth);


        // This should be sync request
        Server.Result<Stream.StartStreamingRequest, Stream.StartStreamingResult> result = server.synchRequest(req.build());
        if (result.hasError()){
            System.err.println("Unable to start a new OLIVE Streaming session: "+ result.getError());
            return -1;
        }

        if (result.getRep().getSuccessful()) {
            this.sessionID = result.getRep().getSessionId();
            System.out.println("Streaming session: "+ result.getRep().getSessionId());
            return result.getRep().getDataPort();
        }
        else{
            System.err.println("Unable to start a new OLIVE Streaming session: "+ result.getRep().getInfo());
            return -1;
        }
    }

    public boolean requestStopStreaming(Server server, String sessionID)   {

        Stream.StopStreamingRequest.Builder req = Stream.StopStreamingRequest.newBuilder();
        if (null != sessionID) {
            req.setSessionId(sessionID);
        }

        // This should be sync request
        Server.Result<Stream.StopStreamingRequest, Stream.StopStreamingResult> result = server.synchRequest(req.build());
        if (result.hasError()){
            System.err.println("Unable to stop a new OLIVE Streaming session: "+ result.getError());
            return false;
        }

        return true;
    }

    public boolean requestFlushStreaming(Server server, String sessionId)   {
        // todo addd support for options and specify the tasks to flush
        Stream.FlushStreamingRequest.Builder req = Stream.FlushStreamingRequest.newBuilder()
                .setSessionId(sessionId);


        // This should be sync request
        Server.Result<Stream.FlushStreamingRequest, Stream.FlushStreamingResult> result = server.synchRequest(req.build());
        if (result.hasError()){
            System.err.println("Unable to flush a new OLIVE Streaming session: "+ result.getError());
            return false;
        }
        Stream.FlushStreamingResult fsr = result.getRep();
        if (fsr.getSuccessful()){
            System.out.println(String.format("Successfully flushed OLIVE stream for session %s.", sessionID));
        }
        else{
            System.out.println(String.format("Failed to flush OLIVE stream for session %s, error message: %s", sessionID, fsr.getMessage()));
            return false;
        }
//        if (result.getRep().)

        return true;
    }

    public boolean requestDrainStreaming(Server server, String sessionId)   {
        // todo addd support for options and specify the tasks to drain

        System.out.println("Seinding drain request...");
        Stream.DrainStreamingRequest.Builder req = Stream.DrainStreamingRequest.newBuilder()
                .setSessionId(sessionId);


        // This should be sync request
        Server.Result<Stream.DrainStreamingRequest, Stream.DrainStreamingResult> result = server.synchRequest(req.build());
        if (result.hasError()){
            System.err.println("Unable to drain a new OLIVE Streaming session: "+ result.getError());
            return false;
        }
        Stream.DrainStreamingResult fsr = result.getRep();
        if (fsr.getSuccessful()){
            System.out.println(String.format("Successfully drained OLIVE stream for session %s.", sessionID));
        }
        else{
            System.out.println(String.format("Failed to drain OLIVE stream for session %s, error message: %s", sessionID, fsr.getMessage()));
            return false;
        }
//        if (result.getRep().)

        return true;
    }














}
