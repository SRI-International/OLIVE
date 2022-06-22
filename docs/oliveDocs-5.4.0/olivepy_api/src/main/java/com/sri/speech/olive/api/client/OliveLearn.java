package com.sri.speech.olive.api.client;

import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Server;
import com.sri.speech.olive.api.Olive.AnnotationRegion;
import com.sri.speech.olive.api.Olive.AudioAnnotation;
import com.sri.speech.olive.api.Olive.Domain;
import com.sri.speech.olive.api.Olive.Plugin;
import com.sri.speech.olive.api.Olive.TraitType;
import com.sri.speech.olive.api.utils.ClientUtils;
import com.sri.speech.olive.api.utils.parser.LearningParser;
import com.sri.speech.olive.api.utils.parser.LearningParser.LearningDataType;
import com.sri.speech.olive.api.utils.Pair;
import com.sri.speech.olive.api.utils.RegionWord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple example of using the Scenic API to make training and adaptation requests. This utility
 * supports:
 *
 * <ul>
 *   <li>Supervised Training
 *   <li>Supervised Adaptation
 *   <li>Unsupervised Adaptation
 * </ul>
 *
 * NOTE: Unsupervised Training is not supported. Supervised or unsupervised dataType is based on the
 *
 * <p>Train: OliveLearn --adapt new_domain_name  -i input_data_list -- plugin X  --domain Y
 *
 * <p>Adapt: OliveLearn --train new_domain_name  -i input_data_list -- plugin X
 */
public class OliveLearn {

  // Command line options
  static final String helpOptionStr = "h";
  static final String inputOptionStr = "input";
  static final String adaptOptionStr = "adapt";
  static final String trainOptionStr = "train";

  // Option
//  static final String optionOptionStr = "options";
  static final String domainOptionStr = "domain";
  static final String pluginOptionStr = "plugin";

  static final String channelOptionStr = "channel"; // vector conversion
  static final String scenicOptionStr = "s";
  static final String portOptionStr = "p";
  static final String timeoutOptionStr = "t";
  static final String outputOptionStr = "output"; // the directory to write any output
  //    static final String stereoOptionStr       = "stereo";  // when possible process audio as
  // stereo
  static final String decodedOptionStr = "decoded"; // send the file as a decoded audio sample buffer
  static final String pathOptionStr = "path";           // send the file path (not a buffer), Server must share the filesystem with this client

  // Stores requested tasks
  private static final int TIMEOUT = 10000;
  private static final String DEFAUL_SERVERNAME = "localhost";
  private static final int DEFAULT_PORT = 5588;

  private static Logger log = LoggerFactory.getLogger(OliveLearn.class);


  private static String scenicSeverName;
  private static int scenicPort;
  private static String outputDirName;
  private static String domainName;
  private static String pluginName;
  private static int timeout;

    // by default, assume non-stereo file(s) so a channel number is not specified
  private static int channelNumber =   -1;
  private static LearningParser learningParser = new LearningParser();

  // learning options
  private static LearningDataType dataType;
  private static LearningMode mode;

  private static String newDomainName;

  // By default send audio as samples
  private static ClientUtils.AudioTransferType transferType = ClientUtils.AudioTransferType.SEND_SAMPLES_BUFFER;

  // public Olive.TraitType[] learningTraits =  {Olive.TraitType.SUPERVISED_ADAPTER,
  // Olive.TraitType.SUPERVISED_TRAINER, Olive.TraitType.UNSUPERVISED_ADAPTER };
  // Async request add result to this queue

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
        "OliveLearn",
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
    OliveLearn sc = new OliveLearn();
    sc.handleLearning(server);
  }

  private static String getShortProgramName() {
    return "OliveLearn";
  }

  private static CommandLine parseCommandLineArgs(String args[]) {

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = null;

    Options options = new Options();

    // Common options - consider moving to base/utility class
    options.addOption(helpOptionStr, false, "Print this help message");
    options.addOption(
        scenicOptionStr, "server", true, "Scenicserver hostname. Default is " + DEFAUL_SERVERNAME);
    options.addOption(
        portOptionStr, "port", true, "Scenicserver port number. Defauls is " + DEFAULT_PORT);
    options.addOption(
        timeoutOptionStr,
        "timeout",
        true,
        "timeout (in seconds) when waiting for server response.  Default is 10 seconds");

    options.addOption(
        Option.builder()
            .longOpt(outputOptionStr)
            .desc("Write any output to DIR, default is ./")
            .build());

    // Audio input file
    options.addOption(
        Option.builder("i")
            .argName("input")
            .longOpt(inputOptionStr)
            .desc("Input data from FILE")
            .hasArg()
            .required()
            .build());

    options.addOption(
        Option.builder()
            .longOpt(adaptOptionStr)
            .desc(
                "Adapt a plugin/domain, giving the new domain NAME")
            .hasArg()
            .build());options.addOption(

        Option.builder()
            .longOpt(trainOptionStr)
            .desc(
                "Train a plugin, giving the new domain NAME")
            .hasArg()
            .build());


    // plugin/domain options
    options.addOption(
        Option.builder().longOpt(domainOptionStr).desc("Use Domain NAME").hasArg().build());
    options.addOption(
        Option.builder().longOpt(pluginOptionStr).desc("Use Plugin NAME").hasArg().build());

    // Stereo audio processing
    //        options.addOption(Option.builder().longOpt(stereoOptionStr).desc("Process audio as
    // stereo, with results for each channel").build());
    options.addOption(
        Option.builder()
            .longOpt(decodedOptionStr)
            .desc("Send the audio file as decoded PCM16 samples, not as a serialzied buffer")
            .build());
    options.addOption(
        Option.builder()
            .longOpt(pathOptionStr)
            .desc("Send audio pathname (don't send as a serialized buffer). Client and server must share the filesystem")
            .build());
    options.addOption(
        Option.builder()
            .longOpt(channelOptionStr)
            .desc("Process stereo files using channel NUMBER")
            .hasArg()
            .build());


    /*options.addOption(
        Option.builder()
            .longOpt(optionOptionStr)
            .desc("Enrollment options from FILE ")
            .hasArg()
            .build());*/



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

    if (cmd.hasOption(inputOptionStr)) {

      String inputFilename = cmd.getOptionValue(inputOptionStr);
      learningParser.parse(inputFilename);
      if (!learningParser.isValid()) {
        System.err.println("Invalid input file: " + inputFilename);
        printUsageAndExit(options);
      }

      if (learningParser.hasRegions()) {
        dataType = LearningDataType.SUPERVISED_WITH_REGIONS;
      } else if (learningParser.hasClasses()) {
        dataType = LearningDataType.SUPERVISED;
      } else {
        dataType = LearningDataType.UNSUPERVISED;
      }
    }


    if (cmd.hasOption(outputOptionStr)) {

      outputDirName = cmd.getOptionValue(outputOptionStr);

      if (!Files.isDirectory(Paths.get(outputDirName))) {
        // Create the output dir
        try {
          Files.createDirectory(Paths.get(outputOptionStr));
        } catch (IOException e) {
          System.err.println(
              "ERROR: Output directory '" + outputDirName + "' could not be created");
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

    // learning options

    // First check if we have any special options
    if (cmd.hasOption(adaptOptionStr)) {
        newDomainName = cmd.getOptionValue(adaptOptionStr);
        mode = LearningMode.ADAPT;

    }

    if (cmd.hasOption(trainOptionStr)) {
        if(mode == LearningMode.ADAPT){

            System.err.println("ERROR: both the train and adapt arguments are set.  Specify 'train' or 'adapt' and try again");
            printUsageAndExit(options);
        }
        newDomainName = cmd.getOptionValue(trainOptionStr);
        mode = LearningMode.TRAIN;

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


    return cmd;
  }

  private static void printUsageAndExit(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp(getShortProgramName(), options);

    System.exit(1);
  }

  public void handleLearning(Server server) throws ClientException {


      // First get the plugins
      List<Pair<Olive.Plugin, Olive.Domain>> pluginList  = ClientUtils.requestPlugins(server);

    if (mode == LearningMode.TRAIN) {
      // We DO NOT support unsupervised training
      if (dataType == LearningDataType.UNSUPERVISED) {
        throw new ClientException("Unsupervised training is not supported");
      }
      // Preprocess audio for training

      Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, null, null, TraitType.SUPERVISED_TRAINER, pluginList);
      if(null == pd ){
        log.error("No learning plugin matching plugin name: {}", pluginName);
        return;
      }

      String trainID = UUID.randomUUID().toString();
      Plugin p = pd.getFirst();
      int numPreprocessed = 0;
      Map<String, List<AudioAnnotation>> annotations = new LinkedHashMap<>();

      for(String filename : learningParser.getFilenames()){

        try {
          Olive.Audio.Builder audio = ClientUtils.createAudioFromFile(filename, -1, transferType, null);
          if (preprocessAudioForTraining(
              server, p, trainID, filename, learningParser, audio.build(), annotations)) {
            numPreprocessed++;
          }
        } catch (IOException | UnsupportedAudioFileException e) {
          System.err.println("Unable to preprocess file: " + filename);
          log.debug("File preprocess error: ", e);
        }

      }

      if(numPreprocessed > 0){
        finalizeSupervisedTraining(server, p, trainID, newDomainName, annotations);
      }
      else {
        System.err.println("Can not train plugin because all audio preprocessing attempts failed. ");
      }

    } else {
      // Adapt

        Pair<Olive.Plugin, Olive.Domain> pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, null, learningParser.isUnsupervised() ? TraitType.UNSUPERVISED_ADAPTER : TraitType.SUPERVISED_ADAPTER, pluginList);
        if(null != pd ) {

          // Preproces audio - doesn't matter if supervised or unsupervised
          String adaptID = UUID.randomUUID().toString();
          Plugin p = pd.getFirst();
          Domain d = pd.getSecond();

          // optional annotations, generated if found in the parser (supervised adaptation)
          Map<String, List<AudioAnnotation>> annotations = new LinkedHashMap<>();
          //  annotations ->  <classID> ->* <AudioAnnotations>, annotations will be empty for unsupervised adaptation
          int numPreprocessed = preprocessAllAudio(server, p, d, learningParser, adaptID, annotations);

          if (!learningParser.isUnsupervised()) {
            //pd = ClientUtils.findPluginDomainByTrait(pluginName, domainName, null, TraitType.SUPERVISED_ADAPTER, pluginList);
            if (numPreprocessed > 0) {
              finalizeSupervisedAdaptation(server, p, d, adaptID, newDomainName, annotations);
            } else {
              System.err.println("Can not adapt domain because all audio preprocessing attempts failed. ");
            }
          } else {
            // unsupervised
            if (numPreprocessed > 0) {
              finalizeUnsupervisedAdaptation(server, p, d, adaptID, newDomainName);
            } else {
              System.err.println("Can not adapt domain because all audio preprocessing attempts failed. ");
            }
          }
        }
        else {
          log.error("No learning plugin matching plugin name: {}, domain: {}", pluginName, domainName);
        }

    }


      System.out.println("");
      System.out.println("Learning finished.  Exiting...");
      System.exit(0);


  }

/*  private String formatPluginErrorMsg(String pluginName, String domainName, Olive.TraitType type) {

    return String.format(
        "No plugin-domain found having trait %s, plugin name: '%s' and domain: '%s' ",
        type.toString(),
        null == pluginName ? "*" : pluginName,
        null == domainName ? "*" : domainName);
  }*/

/*  public double getThreshold(Olive.Plugin plugin, Olive.TraitType type) {

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
  }*/




  public int preprocessAllAudio(
      Server server,
      Olive.Plugin plugin,
      Olive.Domain domain,
      //        List<LearningRecord> records,
      LearningParser parser,
      // Map<String, Olive.Audio> adaptList,
      String adaptID,
      Map<String, List<AudioAnnotation>> annotations
      /*Map<String, List<String>> annotations*/ ) {

    // Send each file to the server for preprocessing, but  we do this synchronously so we can
    // update our annotations with files that were successfully processed and
      // we don't overwhelm
    // the server/network with lots of large amounts of audio data (you could also send the
    // audio as filenames);

    // FIXME  annotations can have start/end region for each class...
   // Map<String, List<String>> annotations = new HashMap<>();

    int numPreprocessed = 0;
    for (String filename : parser.getFilenames()) {

      try {
        Olive.Audio.Builder audio = ClientUtils.createAudioFromFile(filename, -1, transferType, null);
        //Olive.Audio.Builder audio =  Olive.Audio.newBuilder().setPath(filename);

        if (preprocessAudioForAdaptation(
            //parser.getAnnotations(filename)
            server, plugin, domain, adaptID, filename, parser, audio.build(), annotations)) {
          numPreprocessed++;
        }
      } catch (Exception /*| UnsupportedAudioFileException */e) {
        System.err.println("Unable to preprocess file: " + filename);
        log.debug("File preprocess error: ", e);
      }
    }



   return numPreprocessed;
  }

  public boolean preprocessAudioForAdaptation(
      Server server,
      Olive.Plugin plugin,
      Olive.Domain domain,
      String adaptID,
      String audioFilename,
      LearningParser parser,
      Olive.Audio audio,
      Map<String, List<AudioAnnotation>> annotations
      /*Map<String, List<String>> annotations*/) {

    // This is a bit of a hack - the plugin framework allows annotations to be specified when
    // preprocessing audio, but those annotations are never actually used during preprocessing,
    // so we use a placeholder class ID when preprocessing audio with annotations
    String id = null;

    Collection<String> classIDs = parser.getAnnotations(audioFilename).keySet();
    if(classIDs.size() > 0){
      id = "supervised";
    }

    // Prepare the request
    Olive.PreprocessAudioAdaptRequest.Builder req =
        Olive.PreprocessAudioAdaptRequest.newBuilder()
            .setPlugin(plugin.getId())
            .setDomain(domain.getId())
            .setAdaptSpace(adaptID)
            // We don't set the optional start/end regions... those are used later when
            // we finalize
            .setAudio(audio);

    // Hack: set a dummy class ID, so run_supervised_adaptation is called instead of submit_unsupervised_adaptation_audio
    if(null != id){
      req.setClassId(id);
    }
    // ugh, I don't remember do we need to do this so the supervised/unsupervised validation works?

    Server.Result<Olive.PreprocessAudioAdaptRequest, Olive.PreprocessAudioAdaptResult> result =
        server.synchRequest(req.build());

    if(result.hasError() ){
      System.err.println(  String.format("Error preprocessing audio %s because: %s", audioFilename, result.getError()));
    } else {
      handlePreprocessedAudioResult(annotations, parser, audioFilename, result.getRep().getAudioId());
      /*System.out.println( String.format("Audio file %s successfully preprocessed", audioFilename));

      // Set the audio ID for any classID(s) associated with this audio
      for (String classIDName : classIDs) {
        // Add the class/audio id mapping , and optionally add annotation regions

        List<AudioAnnotation> audioAnnots;
        if (annotations.containsKey(classIDName)) {
//          annotations.get(classIDName).add(result.getRep().getAudioId());
          audioAnnots = annotations.get(classIDName);
        } else {
          *//*List<String> audioFiles = new ArrayList<>();
          audioFiles.add(result.getRep().getAudioId());
          annotations.put(classIDName, audioFiles);*//*

          audioAnnots = new ArrayList<>();
          annotations.put(classIDName, audioAnnots);
        }
        Olive.AudioAnnotation.Builder aaBuilder = Olive.AudioAnnotation.newBuilder().setAudioId(result.getRep().getAudioId());
        for(RegionWord word : parser.getAnnotations(audioFilename).get(classIDName)){
          AnnotationRegion.Builder ab = AnnotationRegion.newBuilder().setStartT(word.start).setEndT(word.end);
          aaBuilder.addRegions(ab.build());
        }
        audioAnnots.add(aaBuilder.build());


      }*/
    }
    return !result.hasError();
  }

  public void finalizeSupervisedAdaptation(
      Server server,
      Olive.Plugin plugin,
      Olive.Domain domain,
      String adaptID,
      String newDomainName,
      Map<String, List<AudioAnnotation>> annotations) {

    List<Olive.ClassAnnotation> classAnnotations = buildAnnotations(annotations);

    // Prepare the request
    Olive.SupervisedAdaptationRequest.Builder req =
        Olive.SupervisedAdaptationRequest.newBuilder()
            .setPlugin(plugin.getId())
            .setDomain(domain.getId())
            .setAdaptSpace(adaptID)
            .setNewDomain(newDomainName)
            .addAllClassAnnotations(classAnnotations);

    // Now send the finalize request
    Server.Result<Olive.SupervisedAdaptationRequest, Olive.SupervisedAdaptationResult> result = server.synchRequest(req.build());

    if(result.hasError()){
      System.out.println(String.format("Failed to adapt new Domain '%s' because: %s", newDomainName, result.getError()));
    }
    else {
      System.out.println(String.format("New Domain '%s' Adapted", newDomainName));
    }
  }

  public void finalizeUnsupervisedAdaptation(
      Server server,
      Olive.Plugin plugin,
      Olive.Domain domain,
      String adaptID,
      String newDomainName) {

    // Prepare the request
    Olive.UnsupervisedAdaptationRequest.Builder req =
        Olive.UnsupervisedAdaptationRequest.newBuilder()
            .setPlugin(plugin.getId())
            .setDomain(domain.getId())
            .setAdaptSpace(adaptID)
            .setNewDomain(newDomainName);

    // Now send the finalize request
    Server.Result<Olive.UnsupervisedAdaptationRequest, Olive.UnsupervisedAdaptationResult>
        result = server.synchRequest(req.build());

    if(result.hasError()){
      System.out.println(String.format("Unsupervised adaptation failed for new domain '%s' because: %s", newDomainName, result.getError()));
    }
    else {
      System.out.println(String.format("New Domain '%s' Adapted", newDomainName));
    }

  }


  public boolean preprocessAudioForTraining(
      Server server,
      Olive.Plugin plugin,
      String trainID,
      String audioFilename,
      LearningParser parser,
      Olive.Audio audio,
      Map<String, List<AudioAnnotation>> annotations) {

    // This is a bit of a hack - the plugin framework allows annotations to be specified when
    // preprocessing audio, but those annotations are never actually used during preprocessing,
    // so we use a placeholder class ID when preprocessing audio with annotations
    String id = null;

    Collection<String> classIDs = parser.getAnnotations(audioFilename).keySet();
    if(classIDs.size() > 0){
      id = "supervised";
    }

    // Prepare the request
    Olive.PreprocessAudioTrainRequest.Builder req =
        Olive.PreprocessAudioTrainRequest.newBuilder()
            .setPlugin(plugin.getId())
            .setTrainSpace(trainID)
            // We don't set the optional start/end regions... those are used later when
            // we finalize
            .setAudio(audio);

    // Hack: set a dummy class ID, so run_supervised_adaptation is called instead of submit_unsupervised_adaptation_audio
    if(null != id){
      req.setClassId(id);
    }

    Server.Result<Olive.PreprocessAudioTrainRequest, Olive.PreprocessAudioTrainResult> result =
        server.synchRequest(req.build());

    if(result.hasError() ){
      System.err.println(  String.format("Error preprocessing audio %s because: %s", audioFilename, result.getError()));
    } else {
      handlePreprocessedAudioResult(annotations, parser, audioFilename, result.getRep().getAudioId());
    }

    return !result.hasError();
  }

  private void handlePreprocessedAudioResult(Map<String, List<AudioAnnotation>> annotations, LearningParser parser, String audioFilename, String audioID){

    System.out.println( String.format("Audio file %s successfully preprocessed", audioFilename));

    // Set the audio ID for any classID(s) associated with this audio
    for (String classIDName : parser.getAnnotations(audioFilename).keySet()) {
      // Add the class/audio id mapping , and optionally add annotation regions

      List<AudioAnnotation> audioAnnots;
      if (annotations.containsKey(classIDName)) {
//          annotations.get(classIDName).add(result.getRep().getAudioId());
        audioAnnots = annotations.get(classIDName);
      } else {
          /*List<String> audioFiles = new ArrayList<>();
          audioFiles.add(result.getRep().getAudioId());
          annotations.put(classIDName, audioFiles);*/

        audioAnnots = new ArrayList<>();
        annotations.put(classIDName, audioAnnots);
      }
      Olive.AudioAnnotation.Builder aaBuilder = Olive.AudioAnnotation.newBuilder().setAudioId(audioID);
      for(RegionWord word : parser.getAnnotations(audioFilename).get(classIDName)){
        AnnotationRegion.Builder ab = AnnotationRegion.newBuilder().setStartT(word.getStartTimeSeconds()).setEndT(word.getEndTimeSeconds());
        aaBuilder.addRegions(ab.build());
      }
      audioAnnots.add(aaBuilder.build());


    }

  }


  public void finalizeSupervisedTraining(
      Server server,
      Olive.Plugin plugin,
      String trainID,
      String newDomainName,
      Map<String, List<AudioAnnotation>> annotations) {

    List<Olive.ClassAnnotation> classAnnotations = buildAnnotations(annotations);

    // Prepare the request
    Olive.SupervisedTrainingRequest.Builder req =
        Olive.SupervisedTrainingRequest.newBuilder()
            .setPlugin(plugin.getId())
            .setTrainSpace(trainID)
            .setNewDomain(newDomainName)
            .addAllClassAnnotations(classAnnotations);

    // Now send the finalize request
    Server.Result<Olive.SupervisedTrainingRequest, Olive.SupervisedTrainingResult> result = server.synchRequest(req.build());

    if(result.hasError()){
      System.out.println(String.format("Failed to train new Domain '%s' because: %s", newDomainName, result.getError()));
    }
    else {
      System.out.println(String.format("New Domain '%s' trained", newDomainName));
    }
  }



  // Annotations are not used for preprocessign but are used when finalizing
  public List<Olive.ClassAnnotation> buildAnnotations(Map<String, List<AudioAnnotation>> annotations) {
    // Annotations are <class id> -> *<AudioAnnotation>
    List<Olive.ClassAnnotation> classAnnotations = new ArrayList<>();

    for (String id : annotations.keySet()) {
      Olive.ClassAnnotation.Builder caBuilder =
          Olive.ClassAnnotation.newBuilder().setClassId(id).addAllAnnotations(annotations.get(id));
      classAnnotations.add(caBuilder.build());
    }

    return classAnnotations;
  }

  public enum LearningMode {
    ADAPT,
    TRAIN
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
