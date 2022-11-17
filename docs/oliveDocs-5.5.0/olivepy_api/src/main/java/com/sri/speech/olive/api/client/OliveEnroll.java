package com.sri.speech.olive.api.client;

import com.google.protobuf.InvalidProtocolBufferException;
import com.sri.speech.olive.api.Server;
import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.utils.parser.ClassRegionParser;
import com.sri.speech.olive.api.utils.ClientUtils;
import com.sri.speech.olive.api.utils.Pair;
import com.sri.speech.olive.api.utils.RegionWord;
import com.sri.speech.olive.api.utils.parser.pem.PemParser;
import com.sri.speech.olive.api.utils.parser.pem.PemRecord;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple example of using the Scenic API to make SAD, SID, LID, and enrollment requests. Both
 * asynchronous and synchronous callbacks from the SCENIC are demonstrated.
 *
 * <p>No longer so simple...
 */
public class OliveEnroll {

  static final AtomicInteger seq = new AtomicInteger();
  // Command line options
  static final String helpOptionStr = "h";
  static final String inputOptionStr = "i";

  static final String audioVectorOptionStr = "v"; // Score from an audio vector
  static final String enrollOptionStr = "enroll";
  static final String unenrollOptionStr = "unenroll";
  static final String removeOptionStr = "remove";
  // (speaker) enrollment model import/export
  static final String importOptionStr = "import";
  static final String exportOptionStr = "export";
  // Option
  static final String optionOptionStr = "options";
  static final String domainOptionStr = "domain";
  static final String pluginOptionStr = "plugin";
  static final String channelOptionStr = "channel"; // vector conversion
  static final String batchOptionStr =
      "nobatch"; // if using pem or list input files then if set process serially, not in batches by
                 // class ID
  static final String listOptionStr = "input_list"; // a file containing regions

  static final String printOptionStr = "print";
  static final String classesOptionStr = "classes";
  static final String scenicOptionStr = "s";
  static final String portOptionStr = "p";
  static final String timeoutOptionStr = "t";
  static final String outputOptionStr = "output"; // the directory to write any output

//  // Audio/Image
//  static final String imageOptionStr = "image";
//  static final String videoOptionStr = "video";

  // Audio handling - new default is serialized, adding options for 'path' and 'decoded'
  static final String decodedOptionStr        = "decoded"; // send the file as a decoded sample buffer
  private static final String pathOptionStr   = "path";  // send the file path, not an audio buffer

  // Stores requested tasks
  static final List<TaskType> taskList = new ArrayList<>();
  private static final int TIMEOUT = 10000;
  private static final String DEFAUL_SERVERNAME = "localhost";
  private static final int DEFAULT_PORT = 5588;

  // Not yet supported, but provide an alternate speaker/class id when importing an enrollment
  // model:
  // static final String enrollmentOptionStr     = "enrollment"; // enrollment class name (for
  // importing)
  private static Logger log = LoggerFactory.getLogger(OliveEnroll.class);
  private static String scenicSeverName;
  private static int scenicPort;
  private static String outputDirName;
  //    private static String audioFileName;
  private static String audioVectorFileName;
  //    private static String enrollmentModelFileName;
  private static String speakerName;
  private static String removeSpeakerName;
  private static String domainName;
  private static String pluginName;
  private static String enrollmentExportName;
  private static String enrollmentImportName;
  private static int timeout;
  // By defualt we assume we have audio data:
  private static ClientUtils.DataType dataType = ClientUtils.DataType.AUDIO_DATA;

  //    private static boolean stereoAudioProcessing = false;

  private static int channelNumber =
      -1; // by default, assume non-stereo file(s) so a channel number is not specified
  private static ClassRegionParser regionParser = new ClassRegionParser();
  private static List<String> audioFiles = new ArrayList<>();
  private static Collection<PemRecord> pemRegions = new ArrayList<>();
  // enrollment options
  private static String optionsFilename;
  private static List<Pair<String, String>> enrollmentOptions = new ArrayList<>();
  private static boolean printPlugins = false;
  private static boolean batchMode = true;

  private static ClientUtils.AudioTransferType transferType;

  // public Olive.TraitType[] learningTraits =  {Olive.TraitType.SUPERVISED_ADAPTER,
  // Olive.TraitType.SUPERVISED_TRAINER, Olive.TraitType.UNSUPERVISED_ADAPTER };
  private static boolean printClasses = false;
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
    server.connect(
        "scenic-ui",
        scenicSeverName,
        scenicPort,
        scenicPort + 1,
        TIMEOUT / 100); // may need to adjust timeout
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
    OliveEnroll sc = new OliveEnroll();
    sc.handleEnrollmentRequests(server);
  }

  private static String getShortProgramName() {
    return "OliveEnroll";
  }

  private static CommandLine parseCommandLineArgs(String args[]) {

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = null;

    Options options = new Options();

    // Common options - consider moving to base/utility class
    options.addOption(helpOptionStr, false, "Print this help message");
    options.addOption(scenicOptionStr, "server", true, "Scenicserver hostname. Default is " + DEFAUL_SERVERNAME);
    options.addOption(portOptionStr, "port", true, "Scenicserver port number. Defauls is " + DEFAULT_PORT);
    options.addOption(timeoutOptionStr, "timeout", true, "timeout (in seconds) when waiting for server response.  Default is 10 seconds");


    options.addOption(
        Option.builder()
            .longOpt(outputOptionStr)
            .desc("Write any output to DIR, default is ./")
                .hasArg()
            .build());

    // Audio input options
    options.addOption(
            inputOptionStr,
        "input",
        true,
        "NAME of the input file (input varies by plugin: audio, image, or video)");
    options.addOption(
        Option.builder().longOpt(listOptionStr).desc(
                "Batch enroll using this input list FILE having multiple filenames/class IDs or PEM formmated file ")
            .hasArg().build());
    options.addOption(Option.builder().longOpt(batchOptionStr).desc(
                "Disable batch enrollment when using pem or list input files, so that files are processed serially").build());
    options.addOption( audioVectorOptionStr,
        "vec",
        true,
        "PATH to a serialized AudioVector, for plugins that support audio vectors in addition to wav files");

    // plugin/domain options
    options.addOption( Option.builder().longOpt(domainOptionStr).desc("Use Domain NAME").hasArg().build());
    options.addOption(Option.builder().longOpt(pluginOptionStr).desc("Use Plugin NAME").hasArg().build());
    options.addOption(Option.builder().longOpt(printOptionStr).desc(
                "Print all plugins and domains that suport enrollment and/or class import and export").build());
    options.addOption(Option.builder()
            .longOpt(classesOptionStr)
            .desc(
                "Print class names if also printing plugin/domain names.  Must use with --print option.  Default is to not print class IDs")
            .build());
    // Stereo audio processing
    //        options.addOption(Option.builder().longOpt(stereoOptionStr).desc("Process audio as
    // stereo, with results for each channel").build());
    options.addOption(
        Option.builder()
            .longOpt(decodedOptionStr)
            .desc("Sennd audio file as a decoded PCM16 sample buffer instead of a serialized buffer. The file must be a WAV file")
            .build());options.addOption(
        Option.builder()
            .longOpt(pathOptionStr)
            .desc("Send the path to the audio file instead of a (serialized) buffer.  The server must have access to this path.")
            .build());
    options.addOption(
        Option.builder()
            .longOpt(channelOptionStr)
            .desc("Process stereo files using channel NUMBER")
            .hasArg()
            .build());

    // enrollment options:
    options.addOption(
        Option.builder()
            .longOpt(enrollOptionStr)
            .desc(
                "Enroll speaker NAME. If no name specified then, the pem or list option must specify an input file")
            .optionalArg(true)
            .hasArg()
            .build());
    options.addOption(
        Option.builder()
            .longOpt(unenrollOptionStr)
            .desc("Un-enroll all enrollments for speaker NAME")
            .hasArg()
            .build());
    options.addOption(
        Option.builder()
            .longOpt(removeOptionStr)
            .desc("Remove audio enrollment for NAME")
            .hasArg()
            .build());
    // Misc
    options.addOption(
        Option.builder()
            .longOpt(optionOptionStr)
            .desc("Enrollment options from FILE ")
            .hasArg()
            .build());
    // Class export/import
    options.addOption(
        Option.builder()
            .longOpt(exportOptionStr)
            .desc("Export speaker NAME to an EnrollmentModel (enrollment.tar.gz)")
            .hasArg()
            .build());
    options.addOption(
        Option.builder()
            .longOpt(importOptionStr)
            .desc("Import speaker from EnrollmentModel FILE")
            .hasArg()
            .build());

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

    if (cmd.hasOption(outputOptionStr)) {

      outputDirName = cmd.getOptionValue(outputOptionStr);

      if (!Files.isDirectory(Paths.get(outputDirName))) {
        // Create the output dir
        try {
          Files.createDirectory(Paths.get(outputDirName));
        } catch (IOException e) {
          System.err.println(
              "ERROR: Output directory '" + outputDirName + "' could not be created because: " + e.getMessage());
        }
      }
    } else {
      outputDirName = "./";
    }

    if (cmd.hasOption(pluginOptionStr)) {
      pluginName = cmd.getOptionValue(pluginOptionStr);
    }

    if (cmd.hasOption(domainOptionStr)) {
      domainName = cmd.getOptionValue(domainOptionStr);
    }

    // check if we are using a remote server address
    if (cmd.hasOption(scenicOptionStr)) {
      scenicSeverName = cmd.getOptionValue(scenicOptionStr);
    } else {
      scenicSeverName = DEFAUL_SERVERNAME;
    }

    if (cmd.hasOption(portOptionStr)) {
      try {
        scenicPort = Integer.parseInt(cmd.getOptionValue(portOptionStr));
      } catch (NumberFormatException e) {
        System.err.println("Invalid port number: '" + cmd.getOptionValue(portOptionStr) + "' ");
        printUsageAndExit(options);
      }
    } else {
      scenicPort = DEFAULT_PORT;
    }

    if (cmd.hasOption(timeoutOptionStr)) {
      try {
        // get timeout and convert to MS
        timeout = Integer.parseInt(cmd.getOptionValue(timeoutOptionStr)) * 1000;
      } catch (NumberFormatException e) {
        System.err.println("Invalid timeout: '" + cmd.getOptionValue(timeoutOptionStr) + "' ");
        printUsageAndExit(options);
      }
    } else {
      timeout = TIMEOUT;
    }

    boolean requireAudioInput = false;

    // Enrollment options

    // First check if we have any special options
    if (cmd.hasOption(optionOptionStr)) {
      optionsFilename = cmd.getOptionValue(optionOptionStr);

      if (!Files.exists(Paths.get(optionsFilename).toAbsolutePath())) {
        System.err.println("ERROR: Options file '" + optionsFilename + "' does not exist");
        printUsageAndExit(options);
      }

      try {
        Properties properties = new Properties();
        properties.load(new FileInputStream(optionsFilename));

        for (String name : properties.stringPropertyNames()) {
          enrollmentOptions.add(new Pair<>(name, properties.getProperty(name)));
        }
      } catch (IOException e) {
        System.err.println(
            "ERROR: failed to open enrollment options file file '" + optionsFilename + "'");
        printUsageAndExit(options);
      }
    }

    if (cmd.hasOption(enrollOptionStr)) {
      // If a PEM or list file is specfied then we will batch enroll from those lists
      if (!cmd.hasOption(listOptionStr)) {
        speakerName = cmd.getOptionValue(enrollOptionStr);
        taskList.add(TaskType.ENROLL);
        requireAudioInput = true;
        if(null == speakerName){
          System.err.println("Must specify an enrollment name when using the  '" + enrollOptionStr + "' option without an input file!");
          printUsageAndExit(options);
        }
      }
    } else if (cmd.hasOption(unenrollOptionStr)) {
      removeSpeakerName = cmd.getOptionValue(unenrollOptionStr);
      taskList.add(TaskType.UNENROLL);
    }
    // Remove a audio enrollment for the specified speaker
    else if (cmd.hasOption(removeOptionStr)) {
      removeSpeakerName = cmd.getOptionValue(unenrollOptionStr);
      taskList.add(TaskType.REMOVE);
      requireAudioInput = true;
    } else if (cmd.hasOption(exportOptionStr)) {
      taskList.add(TaskType.EXPORT);
      // name of the speaker/class to export
      enrollmentExportName = cmd.getOptionValue(exportOptionStr);
      // must specify a plugin and/or domain name too....
    } else if (cmd.hasOption(importOptionStr)) {
      if (taskList.size() > 0) {
        System.err.println(
            "Import option is mutually exclusive, all other requests will be ignored");
      }

      taskList.clear();
      taskList.add(TaskType.IMPORT);
      enrollmentImportName = cmd.getOptionValue(importOptionStr);
      if (!Files.exists(Paths.get(enrollmentImportName).toAbsolutePath())) {
        System.err.println(
            "ERROR: Can not import model file '" + enrollmentImportName + "', this file does not exist");
        printUsageAndExit(options);
      }
    }
    if (cmd.hasOption(batchOptionStr)) {
      batchMode = false;
    }

    // common...

    // check if we should print plugin names
    if (cmd.hasOption(printOptionStr)) {
      printPlugins = true;
    }
    // check if we should print plugin classes (if any)
    if (printPlugins && cmd.hasOption(classesOptionStr)) {
      printClasses = true;
    }

    if (cmd.hasOption(channelOptionStr)) {
      try {
        channelNumber = Integer.parseInt(cmd.getOptionValue(channelOptionStr));
      } catch (NumberFormatException e) {
        System.err.println(
            "Ignoring non integer channel number: " + cmd.getOptionValue(channelOptionStr));
      }
    }

    // check for the audio transfer type
    if (cmd.hasOption(decodedOptionStr) && cmd.hasOption(pathOptionStr)){
      System.err.println(String.format("Options '%s' and '%s' are mutually exclusive options.  Please only specify one of these options and run again ", decodedOptionStr, pathOptionStr));
      printUsageAndExit(options);
    }

    if (cmd.hasOption(decodedOptionStr)) {
      transferType = ClientUtils.AudioTransferType.SEND_SAMPLES_BUFFER;
    }
    else if (cmd.hasOption(pathOptionStr)){
      transferType = ClientUtils.AudioTransferType.SEND_AS_PATH;
    }
    else {
      // Default
      transferType = ClientUtils.AudioTransferType.SEND_SERIALIZED_BUFFER;
    }



    // Check for a wave, audio vector, or enrollment vector input
    if (cmd.hasOption(listOptionStr)) {
      String listInputFilename = cmd.getOptionValue(listOptionStr);
      if (!Files.exists(Paths.get(listInputFilename).toAbsolutePath())) {
        System.err.println("ERROR: input list file '" + listInputFilename + "' does not exist");
        printUsageAndExit(options);
      }

      // First try to parse this as a PEM file
      PemParser pp = new PemParser();
      if (pp.parse(listInputFilename)) {
        pemRegions = pp.getRegions();
        System.out.println("Received PEM Input");
        // We assume a PEM file means we will enroll only
        if (pemRegions.size() > 0) {
          taskList.add(TaskType.ENROLL);
        }
      }
      else {
        regionParser.parse(listInputFilename);
        if (!regionParser.isValid()) {
          System.err.println("Invalid list input file: " + listInputFilename);
          printUsageAndExit(options);
        }

        if (regionParser.isValid()) {
          taskList.add(TaskType.ENROLL);
        }
      }

      if (cmd.hasOption(inputOptionStr)) {
        System.err.println(String.format("Option '%s' ignored.  Using input list", inputOptionStr));
      }
      if (cmd.hasOption(audioVectorOptionStr)) {
        System.err.println(String.format("Option '%s' ignored.  Using input list", inputOptionStr));
      }

    }

    // In addition, accept the wave filename from the comand line
    else if (cmd.hasOption(inputOptionStr)) {
      String audioFileName = cmd.getOptionValue(inputOptionStr);
      // Make sure file exists
      if (!Files.exists(Paths.get(audioFileName).toAbsolutePath())) {
        System.err.println("ERROR: Wave file '" + audioFileName + "' does not exist");
//        printUsageAndExit(options);
      }
      audioFiles.add(audioFileName);

      if (cmd.hasOption(audioVectorOptionStr)) {
        System.err.println(String.format("Option '%s' ignored.  Using audio list", inputOptionStr));
      }
    } else if (cmd.hasOption(audioVectorOptionStr)) {

      // Use the audio from a serialized audio vector
      audioVectorFileName = cmd.getOptionValue(audioVectorOptionStr);
      // Make sure file exists
      if (!Files.exists(Paths.get(audioVectorFileName))) {
        System.err.println("Audio vector file '" + audioVectorFileName + "' does not exist");
        printUsageAndExit(options);
      }
    }

    // Check that tasks require audio/vector input  have such an input
    if (requireAudioInput /*taskList.contains(TaskType.SAD) || taskList.contains(TaskType.LID) || taskList.contains(TaskType.SID) || taskList.contains(TaskType.KWS)*/) {
      if (audioFiles.size() == 0 && null == audioVectorFileName) {

        System.err.println(
            String.format(
                "Requested task(s) '%s' required an audio input.  Add a wave file name (or audio vector name - if supported)",
                taskList.toString()));
        printUsageAndExit(options);
      }
      // Make sure a wav or vector file is specified
    }

    if (taskList.size() == 0){
      System.err.println("No task specified. One or more tasks required");
      printUsageAndExit(options);
    }

    return cmd;
  }

  private static void printUsageAndExit(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp(getShortProgramName(), options);

    System.exit(1);
  }

  public void handleEnrollmentRequests(Server server) {

    try {

      // First request current plugins (using a synchronous/blocking request)
      List<Pair<Olive.Plugin, Olive.Domain>> pluginList =
          requestPlugins(server, printPlugins, printClasses);

      int numAsyncRequest = 0;

      // Next process any request that don't require audio input

      boolean unenrolled = false;
      if (taskList.contains(TaskType.UNENROLL)) {
        // sync call
        unenrolled = unEnrollClass(
            server,
            ClientUtils.findPluginDomainByTrait(
                pluginName, domainName, null, Olive.TraitType.CLASS_ENROLLER, pluginList),
            removeSpeakerName);
      }

      if (taskList.contains(TaskType.EXPORT)) {
        if (requestExport(server, pluginList)) numAsyncRequest++;
      }

      if (taskList.contains(TaskType.IMPORT)) {
        if (requestImport(server, pluginList)) numAsyncRequest++;
      }

      // Now process audio requests - first handling audio vectors
      if (taskList.contains(TaskType.ENROLL) && null != audioVectorFileName) {
        enrollAudioVector(server, pluginList, speakerName, audioVectorFileName);
        // do we still want to do enrollments from an audio file???
      }

      // Either use audio files or the PemRegions for enrollment - but not both
      if (pemRegions.size() > 0) {
        // Then, check if we need to enroll the audio (also using a synchronous request

        if (taskList.contains(TaskType.ENROLL) && !batchMode) {
          RegionWord word;
          Map<Pair<String, String>, Collection<RegionWord>> enrollments = new HashMap<>();

          // we are going to ignore channel...
          for (PemRecord pem : pemRegions) {
            Pair<String, String> enrollKey = new Pair<>(pem.getSourceID(), pem.getClassLabel());
            if (enrollments.containsKey(enrollKey)) {
              enrollments
                  .get(enrollKey)
                  .add(new RegionWord(pem.getStartTimeMS(), pem.getEndTimeMS()));
            } else {
              List<RegionWord> words = new ArrayList<>();
              words.add(new RegionWord(pem.getStartTimeMS(), pem.getEndTimeMS()));
              enrollments.put(enrollKey, words);
            }
          }

          for (Pair<String, String> enrollKey : enrollments.keySet()) {
            // Enroll regions for each file/class:
            if (enrollClass(
                server,
                pluginList,
                enrollKey.getSecond(),  // classID
                enrollKey.getFirst(),   // filename
                (List<RegionWord>) enrollments.get(enrollKey))) {
              numAsyncRequest++;
            }
          }
        } else if (taskList.contains(TaskType.ENROLL) && batchMode) {
          // Batch enrollment using pem file input
          // ClassID ->* <filenmae, regions>
          //Map<String, List<Pair<String, Collection<RegionWord>>>> enrollments = new HashMap<>();
          Map<String, Map<String, Collection<RegionWord>>> enrollments = new HashMap<>();

          for (PemRecord pem : pemRegions) {

            Map<String, Collection<RegionWord>> fileRegions;
            if (enrollments.containsKey(pem.getClassLabel())) {
              fileRegions = enrollments.get(pem.getClassLabel());
            } else {
              fileRegions = new HashMap<>();
              enrollments.put(pem.getClassLabel(), fileRegions);
            }

            // Now check for unique file
           Collection<RegionWord> regions;
            if(fileRegions.containsKey(pem.getSourceID())){
              regions = fileRegions.get(pem.getSourceID());
            }
            else {
              regions = new ArrayList<>();
              fileRegions.put(pem.getSourceID(), regions);
            }
            regions.add(new RegionWord(pem.getStartTimeMS(), pem.getEndTimeMS()));

          }

         /* for(String classID : enrollments.keySet()){
            for(String fn : enrollments.get(classID).keySet()){

            }
          }*/

          // We need to make a request for each filename/class - one request should not contain multiple files
          numAsyncRequest += enrollClassBatch(server, pluginList, enrollments);


        }

      } else if (regionParser.isValid()) {
        // Enroll from the list input file

        // Verify enrollemtn
        if (taskList.contains(TaskType.ENROLL)) {

          // Map<String, List<String>> classEnrollments = new HashMap<>();
          Map<String, Map<String, Collection<RegionWord>>> classEnrollments =
              new HashMap<>();
          for (Pair<String, String> enrollKey : regionParser.getEnrollments()) {

            if (!batchMode) {
              if (enrollClass(
                  server,
                  pluginList,
                  enrollKey.getSecond(),
                  enrollKey.getFirst(),
                  new ArrayList<>())) {
                numAsyncRequest++;
              }
            } else {
              // Otherwise collect files to process by class/speaker name
              String id = enrollKey.getSecond();
              String fn = enrollKey.getFirst();
              //              List<String> filenames;
              Map<String, Collection<RegionWord>> filenames;
              if (classEnrollments.containsKey(id)) {
                filenames = classEnrollments.get(id);
              } else {
                filenames = new HashMap<>();
                classEnrollments.put(id, filenames);
              }
              // no regions, just file names
              filenames.put(fn, new ArrayList<>());
            }
          }

          if (batchMode) {
            // now batch enroll the class enrollments
            numAsyncRequest += enrollClassBatch(server, pluginList, classEnrollments);
          }
        }

      } else {
        for (String audioFileName : audioFiles) {

          // Then, check if we need to enroll the audio (also using a synchronous request)
          if (taskList.contains(TaskType.ENROLL)) {
            // IF YOU NEED REGIONS THEN USE A PEM FILE...
            if (enrollClass(server, pluginList, speakerName, audioFileName, new ArrayList<>())) {
              numAsyncRequest++;
            }
          }

          // TODO SUPPORT REMOVING AN AUDIO ENROLLMENT

        }
      }

      int numTimeouts = 0;
      boolean jobTimeout = false;
      int numAsyncFailures = 0;
      int numAsyncSuccess = 0;
      int totalAsycRequests = numAsyncRequest;
      while (numAsyncRequest > 0) {
        ScenicResult sr = queue.poll(timeout, TimeUnit.MILLISECONDS);

        if (null == sr) {
          if (numTimeouts++ > 3) {
            System.err.println("ERROR: Timeout waiting for response.");
            numAsyncFailures=1;
            jobTimeout = true;
            break;
          }
        } else {
          // System.out.println(String.format("Received %s result for task: %s and id: %S",
          // sr.isError ? "unsuccessful" : "successful", sr.getType().toString(), sr.getId()));
          numAsyncRequest--;
          if(sr.isError){
            numAsyncFailures++;
          }
          else {
            numAsyncSuccess++;
          }
        }
      }

      if(jobTimeout){
        System.err.println("");
        System.err.println("Enrollment failed because job(s) are taking too long to finish. Consider increasing timout and trying again.");
        System.exit(2);

      }

      // Check for sync results
      if (taskList.contains(TaskType.UNENROLL)) {
        if (unenrolled){
          System.err.println(("Un-enrollment successful"));
        }
        else{
          System.err.println(("Unenrollment failed"));
        }
      }

      if (numAsyncFailures > 0 && numAsyncSuccess > 0 ){
        System.err.println("");
        System.err.println(String.format("Enrollment finished with errors.  %d out of %d enrollments failed", numAsyncFailures, numAsyncSuccess));
        System.exit(1);
      }

      if (numAsyncSuccess == 0 & totalAsycRequests >0 ){
        System.err.println("");
        System.err.println("All enrollment requests failed");
        System.exit(1);
      }

      System.out.println("");
      System.out.println("Enrollment finished.  Exiting...");
      System.exit(0);

    } catch (Exception e) {
      log.error("\nError creating OliveEnroll", e);
    }
  }

  private boolean requestExport(
      Server server, List<Pair<Olive.Plugin, Olive.Domain>> pluginList) {

    int exportRequestId = seq.getAndIncrement();

    Pair<Olive.Plugin, Olive.Domain> plugin =
        ClientUtils.findPluginDomainByTrait(
            pluginName, domainName, null, Olive.TraitType.CLASS_EXPORTER, pluginList);

    if (null == plugin) {
      return false;
    }

    log.info("Using plugin: '{}-{}'", plugin.getFirst().getId(), plugin.getSecond().getId());

    // Create a callback to handle async results from server
    Server.ResultCallback<Olive.ClassExportRequest, Olive.ClassExportResult> rc =
        new Server.ResultCallback<Olive.ClassExportRequest, Olive.ClassExportResult>() {

          @Override
          public void call(Server.Result<Olive.ClassExportRequest, Olive.ClassExportResult> r) {

            // do something with the results:
            if (!r.hasError()) {
              log.info("Successfully exported an enrollment for class '{}'", enrollmentExportName);

              try {
                // Get the enrollment result, check for an error
                Olive.ClassExportResult cer = r.getRep();

                if (cer.getSuccessful()) {
                  // Get the actual enrollment model
                  Olive.EnrollmentModel model = r.getRep().getEnrollment();

                  // Save the model
                  String enrollmentModelName =
                      String.format(
                          "%s.%s-%s.enrollment.model",
                          enrollmentExportName,
                          plugin.getFirst().getId(),
                          plugin.getSecond().getId()); // jose.plugin-domain.enrollment.model
                  log.info("Saving audio vector as {}", enrollmentModelName);
                  FileOutputStream fos =
                      new FileOutputStream(
                          FileSystems.getDefault().getPath(enrollmentModelName).toFile());
                  model.writeTo(fos);

                  // Hack save data portion as a tar file
                  // InputStream buffer = new ByteArrayInputStream(model.getData().toByteArray());
                  // Path workpath = FileSystems.getDefault().getPath("enrollment.tar.gz");
                  // log.info("Saving enrollment model to: {}", workpath.toString());
                  // Files.copy(buffer, workpath, StandardCopyOption.REPLACE_EXISTING);

                  for (Olive.Metadata meta : model.getParamsList()) {
                    Server.MetadataWrapper mw = server.deserializeMetadata(meta);
                    log.info(
                        "Metadata score: {} = {}, type: {}",
                        meta.getName(),
                        mw.toString(),
                        meta.getType());
                  }

                } else {
                  log.error("Invalid audio vectorize request: {}", cer.getMessage());
                }

              } catch (IOException e) {
                log.error("Unable to save vectorized audio becuase: ", e);
              }
            } else {
              log.error("Audio vector error: {}", r.getError());
            }

            // Let main thread know the request has been received
            queue.add(new ScenicResult(exportRequestId, TaskType.EXPORT, r.hasError()));
          }
        };

    try {
      return ClientUtils.requestExportClassModel(server, plugin, enrollmentExportName, rc, true);
    } catch (InvalidProtocolBufferException e) {
      log.error("Export error: ", e);
      return false;
    }
  }

  private boolean requestImport(Server server, List<Pair<Olive.Plugin, Olive.Domain>> pluginList)
      throws IOException {

    int importRequestId = seq.getAndIncrement();

    // Create a callback to handle async   results from server
    Server.ResultCallback<Olive.ClassImportRequest, Olive.ClassImportResult> rc =
        new Server.ResultCallback<Olive.ClassImportRequest, Olive.ClassImportResult>() {

          @Override
          public void call(Server.Result<Olive.ClassImportRequest, Olive.ClassImportResult> r) {

            // do something with the results:
            if (!r.hasError()) {

              try {
                // Assume only one result
                Olive.ClassImportResult cir = r.getRep();
                if (cir.getSuccessful()) {

                  log.info("Successfully imported enrollment from: {}", enrollmentImportName);

                } else {
                  log.error("Invalid class import request: {}", cir.getMessage());
                }

              } catch (Exception e) {
                log.error("Unable to import enrollment because: ", e);
              }
            } else {
              log.error("Enrollment import error: {}", r.getError());
            }

            // Let main thread know the request has been received
            queue.add(new ScenicResult(importRequestId, TaskType.IMPORT, r.hasError()));
          }
        };

    Pair<Olive.Plugin, Olive.Domain> plugin =
        ClientUtils.findPluginDomainByTrait(
            pluginName, domainName, null, Olive.TraitType.CLASS_EXPORTER, pluginList);

    if (null == plugin) {
      return false;
    }

    log.info("Using plugin: {}-{}", plugin.getFirst().getId(), plugin.getSecond().getId());

    return ClientUtils.requestImportClassModel(
        server, plugin, enrollmentImportName, null, rc, true);
  }

  private String formatPluginErrorMsg(String pluginName, String domainName, Olive.TraitType type) {

    return String.format(
        "No plugin-domain found having trait %s, plugin name: '%s' and domain: '%s' ",
        type.toString(),
        null == pluginName ? "*" : pluginName,
        null == domainName ? "*" : domainName);
  }

  public double getThreshold(Olive.Plugin plugin, Olive.TraitType type) {

    for (Olive.Trait trait : plugin.getTraitList()) {
      if (trait.getType() == type) {
        for (Olive.OptionDefinition option : trait.getOptionsList()) {
          if (option.getName().equals("threshold")) {
            return Double.parseDouble(option.getDefault());
          }
        }
      }
    }

    return 0; // log error if threshold not available?
  }

  public List<Pair<Olive.Plugin, Olive.Domain>> requestPlugins(
      Server server, boolean printPlugins, boolean printClasses) throws ClientException {

    List<Pair<Olive.Plugin, Olive.Domain>> pluginList = ClientUtils.requestPlugins(server);
    if (printPlugins) {
      log.info("Found {} plugins that support enrollment and/or export:", pluginList.size());
      for (Pair<Olive.Plugin, Olive.Domain> pp : pluginList) {

        // We only want to print plugins that support enrollment or export/import
        Olive.Plugin p = pp.getFirst();

        // Only print plugins that support
        List<Olive.TraitType> supportedTraits = new ArrayList<>();
        supportedTraits.add(Olive.TraitType.CLASS_ENROLLER);
        supportedTraits.add(Olive.TraitType.CLASS_EXPORTER);

        for (Olive.Trait t : p.getTraitList()) {
          if (ClientUtils.isTraitSupported(p.getTraitList(), supportedTraits)) {
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
      }
    }

    if (printPlugins) {
      log.info("");
      log.info("");
      log.info("");
    }
    return pluginList;
  }

  private boolean enrollClass(
      Server server,
      List<Pair<Olive.Plugin, Olive.Domain>> pluginList,
      String speaker,
      String wavePath,
      List<RegionWord> regions)
      throws ClientException {

    try {

      Pair<Olive.Plugin, Olive.Domain> pp =
          ClientUtils.findPluginDomainByTrait(
              pluginName, domainName, null, Olive.TraitType.CLASS_MODIFIER, pluginList);

      if (null == pp) {
        log.error(formatPluginErrorMsg(pluginName, domainName, Olive.TraitType.CLASS_ENROLLER));
        return false;
      }

      int enrollRequstId = seq.getAndIncrement();

      Server.ResultCallback<Olive.ClassModificationRequest, Olive.ClassModificationResult> rc =
          new Server.ResultCallback<
              Olive.ClassModificationRequest, Olive.ClassModificationResult>() {
            @Override
            public void call(
                Server.Result<Olive.ClassModificationRequest, Olive.ClassModificationResult> r) {

              if (r.hasError()) {
                System.err.println(String.format("Enrollment failed because: %s", r.getError()));
              } else {
                Olive.ClassModificationResult cmr = r.getRep();
                boolean allEnrolled = true;
                for (Olive.AudioResult ar : cmr.getAdditionResultList()) {
                  if (!ar.getSuccessful()) {
                    allEnrolled = false;
                    System.err.println(
                        String.format("Enrollment failed because: %s", ar.getMessage()));
                  }
                }
                if (allEnrolled) {
                  System.out.println(
                      String.format(
                          "Successfully enrolled: '%s' from file: %s", speaker, wavePath));
                }
              }

              queue.add(
                  new ScenicResult(enrollRequstId, TaskType.ENROLL, r.hasError())); // assume SID
            }
          };

      // Check if we need to send binary (non-audio) data for enrollment
      for(Olive.Trait ot : pp.getFirst().getTraitList()){
        if (ot.getType() == Olive.TraitType.BOUNDING_BOX_SCORER){
          dataType = ClientUtils.DataType.BINARY_DATA;
          break;
        }
      }

      ClientUtils.requestEnrollClass(
          server,
          pp,
          speaker,
          wavePath,
          channelNumber,
          rc,
          true,
          dataType,
          transferType,
          regions,
          enrollmentOptions);
      return true;

    } catch (Exception e) {
      log.error("Enrollment failed because: {}", e);
    }

    return false;
  }

  /**
   * Enrolls all audio files for the same class Id in one call.
   *
   * @param server
   * @param pluginList
   * @param enrollments
   * @return
   * @throws ClientException
   */
  private int enrollClassBatch(
      Server server,
      List<Pair<Olive.Plugin, Olive.Domain>> pluginList,
      Map<String, Map<String, Collection<RegionWord>>> enrollments)
      throws ClientException {

    int numEnrollments = 0;
    int finishedEnrollments = 0;
    try {

      Pair<Olive.Plugin, Olive.Domain> pp =
          ClientUtils.findPluginDomainByTrait(
              pluginName, domainName, null, Olive.TraitType.CLASS_MODIFIER, pluginList);

      if (null == pp) {
        log.error(formatPluginErrorMsg(pluginName, domainName, Olive.TraitType.CLASS_ENROLLER));
        return 0;
      }

      int enrollRequstId = seq.getAndIncrement();




      // Make an enrollment request for each class...
      for (String classID : enrollments.keySet()) {
        // ClientUtils.requestEnrollClass(server, pp, speaker, wavePath, channelNumber, rc, true,
        // serializedAudio, regions, enrollmentOptions);
        try {

          Olive.ClassModificationRequest.Builder req =
              Olive.ClassModificationRequest.newBuilder()
                  .setClassId(classID)
                  .setPlugin(pp.getFirst().getId())
                  .setDomain(pp.getSecond().getId());

          // Add enrollments for the class/speaker
//          for (Pair<String, Collection<RegionWord>> fn : enrollments.get(id)) {
          for (String fn : enrollments.get(classID).keySet()) {

            System.out.println("Enrolling file '" + fn + "' for class '" + classID + "' with " + enrollments.get(classID).get(fn).size() + " regions");

            req.addAddition(ClientUtils.createAudioFromFile(
                    fn,
                    channelNumber,
                    transferType,
                    (List<RegionWord>) enrollments.get(classID).get(fn)));
          }



          // test sending option
          //
          // req.addOption(Olive.OptionValue.newBuilder().setName("region").setValue("0.5, 1.75,
          // 2.5, 4,6.25, 9"));
          //
          // req.addOption(Olive.OptionValue.newBuilder().setName("isNegative").setValue("true"));

          for (Pair<String, String> p : enrollmentOptions) {
            req.addOption(
                Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
          }

          Server.ResultCallback<Olive.ClassModificationRequest, Olive.ClassModificationResult> rc =
                  new Server.ResultCallback<
                          Olive.ClassModificationRequest, Olive.ClassModificationResult>() {
                    @Override
                    public void call(
                            Server.Result<Olive.ClassModificationRequest, Olive.ClassModificationResult> r) {

                      if (r.hasError()) {
                        System.err.println(String.format("Enrollment failed because: %s", r.getError()));
                      } else {
                        Olive.ClassModificationResult cmr = r.getRep();
                        boolean allEnrolled = true;
                        for (Olive.AudioResult ar : cmr.getAdditionResultList()) {
                          if (!ar.getSuccessful()) {
                            allEnrolled = false;
                            System.err.println(
                                    String.format("Batch enrollment failed because: %s", ar.getMessage()));
                          }
                        }
                        if (allEnrolled) {
                          System.out.println(
                                  String.format("Successfully enrolled class '%s'", classID));
                        }
                      }

                      queue.add(
                              new ScenicResult(enrollRequstId, TaskType.ENROLL, r.hasError())); // assume SID
                    }
                  };

          if (true) {
            server.enqueueRequest(req.build(), rc);
            numEnrollments++;
          } else {
            Server.Result<Olive.ClassModificationRequest, Olive.ClassModificationResult> result =
                server.synchRequest(req.build());
            rc.call(result);
          }

        } catch (Exception e) {
          log.error("Batch enrollment failed because: {}", e);
          return 0;
        }
      }

      return numEnrollments;

    } catch (Exception e) {
      log.error("Enrollment failed because: {}", e);
    }

    return 0;
  }

  private void enrollAudioVector(
      Server server,
      List<Pair<Olive.Plugin, Olive.Domain>> pluginList,
      String speaker,
      String avPath)
      throws ClientException {

    try {
      Pair<Olive.Plugin, Olive.Domain> pp =
          ClientUtils.findPluginDomainByTrait(
              pluginName, domainName, null, Olive.TraitType.CLASS_ENROLLER, pluginList);

      if (null == pp) {
        log.error(formatPluginErrorMsg(pluginName, domainName, Olive.TraitType.CLASS_ENROLLER));
        return;
      }

      // Load the audio vector from disk:
      Olive.AudioVector vector =
          Olive.AudioVector.parseFrom(new FileInputStream(Paths.get(avPath).toFile()));
      Olive.ClassModificationRequest.Builder req =
          Olive.ClassModificationRequest.newBuilder()
              .setClassId(speaker)
              .addAdditionVector(vector)
              .setPlugin(pp.getFirst().getId())
              .setDomain(pp.getSecond().getId());

      log.info(
          "Requesting SID ({}-{}) enrollment using an audio vector for speaker: '{}'",
          pp.getFirst().getId(),
          pp.getSecond().getId(),
          speaker);

      // This request could be asynchronous, but a synchronous example shown here
      Server.Result<Olive.ClassModificationRequest, Olive.ClassModificationResult> result =
          server.synchRequest(req.build());

      if (result.hasError()) {
        log.error("SID Audio Vector Enrollment failed because: {}", result.getError());
      } else {
        Olive.ClassModificationResult cmr = result.getRep();
        for (Olive.AudioResult ar : cmr.getVectorAdditionResultList()) {
          if (ar.getSuccessful()) {
            log.info("Successfully enrolled: {} from an audio vector", speaker);
          } else {
            log.error(
                "Failed to enroll '{}' using an audio vector because: {}",
                speaker,
                ar.getMessage());
          }
        }
      }
    } catch (Exception e) {
      log.error("SID Audio Vector Enrollment failed because: {}", e);
    }
  }

  private boolean unEnrollClass(
      Server server, Pair<Olive.Plugin, Olive.Domain> pp, String speaker) {

    try {

      if (null == pp) {
        return false;
      }

      int unenrollRequstId = seq.getAndIncrement();

      Server.ResultCallback<Olive.ClassRemovalRequest, Olive.ClassRemovalResult> rc =
          new Server.ResultCallback<Olive.ClassRemovalRequest, Olive.ClassRemovalResult>() {

            @Override
            public void call(
                Server.Result<Olive.ClassRemovalRequest, Olive.ClassRemovalResult> r) {

              // do something with the results:
              if (!r.hasError()) {
                log.info("Removed class (speaker): {}", r.getReq().getClassId());
              } else {
                log.error("Class (speaker) removal error: {}", r.getError());
              }

              // Let main thread know the request has been received
              queue.add(
                  new ScenicResult(
                      unenrollRequstId, TaskType.UNENROLL, r.hasError())); // assume SID
            }
          };

      return ClientUtils.requestUnenrollClass(server, pp, speaker, rc, false);

    } catch (Exception e) {
      log.error("Class removal request failed: {}", e);
    }
    return false;
  }

  // SAD words have 100 frames pers second
  public class SADWord {

    int start;
    int end;

    SADWord(int start, int end) {
      this.start = start;
      this.end = end;
    }

    public double getStartTimeSeconds() {
      return start / 100.;
    }

    public double getEndTimeSeconds() {
      return end / 100.;
    }
  }

  // Helper class to track score request/results
  public class ScenicResult {

    boolean isError;
    TaskType type;
    private int id;

    public ScenicResult(int id, TaskType type, boolean error) {
      this.id = id;
      this.type = type;
      this.isError = error;
    }

    public TaskType getType() {
      return type;
    }

    public boolean isError() {
      return isError;
    }

    public int getId() {
      return id;
    }
  }
}
