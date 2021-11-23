package com.sri.speech.olive.api.utils;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.sri.speech.olive.api.Olive;
import com.sri.speech.olive.api.Server;
import com.sri.speech.olive.api.client.ClientException;
import com.sri.speech.olive.api.client.SADWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper for making enterprise API requests
 */
public class ClientUtils {

    private static Logger log = LoggerFactory.getLogger(ClientUtils.class);


    // Fixme:  data transfer type, not just audio
    public enum AudioTransferType {
        SEND_AS_PATH,       // Send the path of the file to the OLIVE server, requires the server be on the same host as the client or they share a filesystem
        SEND_SAMPLES_BUFFER,    // Send audio to the OLIVE server as a buffer, sent as decoded samples
        SEND_SERIALIZED_BUFFER   // Send audio to the OLIVE server as buffer, sent serialized from a file (send the file as bytes)
    }

    public enum DataType {
        AUDIO_DATA, //
        BINARY_DATA, //
    }


    public static List<Pair<Olive.Plugin, Olive.Domain>> requestPlugins(Server server) throws ClientException {


        List<Pair<Olive.Plugin, Olive.Domain>> pluginList = new ArrayList<>();

        Server.Result<Olive.PluginDirectoryRequest, Olive.PluginDirectoryResult> result =
                server.synchRequest(Olive.PluginDirectoryRequest.newBuilder().build());

        if(result.hasError()){
            log.error("Error requesting plugins: {}", result.getError());
        }
        else {
            for (Olive.Plugin p : result.getRep().getPluginsList()) {
                for (Olive.Domain d : p.getDomainList()) {
                    pluginList.add(new Pair<>(p, d));
                }
            }
        }


        return  pluginList;
    }

    public static boolean requestStatus(Server server,
                                            Server.ResultCallback<Olive.GetStatusRequest, Olive.GetStatusResult> rc,
                                            boolean async) throws IOException, UnsupportedAudioFileException {


        Olive.GetStatusRequest.Builder req = Olive.GetStatusRequest.newBuilder();


        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else {

            Server.Result<Olive.GetStatusRequest, Olive.GetStatusResult> result = server.synchRequest(req.build());
            rc.call(result);
        }


        return true;
    }

    /**
     * Make a frame score request (usually SAD).
     *
     * @param server
     * @param pp
     * @param filename
     * @param channelNumber
     * @param transferType
     * @param options
     * @param classIDs
     * @return
     * @throws ClientException
     */
    public static boolean requestFrameScore(Server server,
                                            Pair<Olive.Plugin, Olive.Domain> pp,
                                            String filename,
                                            int channelNumber,
                                            Server.ResultCallback<Olive.FrameScorerRequest, Olive.FrameScorerResult> rc,
                                            boolean async,
                                            AudioTransferType transferType,
                                            List<RegionWord> regions,
                                            List<Pair<String, String>> options,
                                            List<String> classIDs) throws ClientException, IOException, UnsupportedAudioFileException {

        // make sure a valid plugin is sent
        if (null == pp){
            return false;
        }




        Olive.FrameScorerRequest.Builder req = Olive.FrameScorerRequest.newBuilder()
//                .setAudio()
                .setAudio( createAudioFromFile(filename, channelNumber, transferType, regions))
                // example of passing parameter .addOption(Olive.OptionValue.newBuilder().setName("filter_length").setValue("1"))
                .setPlugin(pp.getFirst().getId())
                .setDomain(pp.getSecond().getId());

        for (Pair<String, String> p : options){
            req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
        }

        req.addAllClassId(classIDs);

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
     * Request global score analysis (LID or SID) using an audio buffer
     * @param server the SCENIC server handle
     * @param plugin the plugin/domain to use
     *
     * @param channelNumber
     * @param transferType
     * @param options
     * @param classIDs
     * @return scores
     *
     * @throws ClientException if there is a communication error with the server
     */
    public static boolean requestGlobalScore(Server server,
                                             Pair<Olive.Plugin, Olive.Domain> plugin,
                                             Olive.TraitType trait,
                                             String filename,
                                             int channelNumber,
                                             Server.ResultCallback<Olive.GlobalScorerRequest, Olive.GlobalScorerResult> rc,
                                             boolean async, AudioTransferType transferType,
                                             List<RegionWord> regions,
                                             List<Pair<String, String>> options,
                                             List<String> classIDs) throws ClientException, IOException, UnsupportedAudioFileException {


        if(null == plugin){
            return false;
        }

        Olive.AudioVector vector = null;
        Olive.Audio.Builder audio = null;
        if(Olive.TraitType.AUDIO_VECTORIZER  == trait) {
            log.info("Loading audio vector: {}", filename);
            vector = Olive.AudioVector.parseFrom(new FileInputStream(Paths.get(filename).toFile()));
        }
        else {
            audio = createAudioFromFile(filename, channelNumber, transferType, regions);
        }

        Olive.GlobalScorerRequest.Builder req = Olive.GlobalScorerRequest.newBuilder()
                //.addOption(Olive.OptionValue.newBuilder().setName("foo").setValue("bar"))
                .setPlugin(plugin.getFirst().getId())
                .setDomain(plugin.getSecond().getId());

        for (Pair<String, String> p : options){
            req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
        }

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
     *
     * @param server the server connection
     * @param plugin the plugin/domain to use
     * @param filename_one One of the the audio files to compare
     * @param filename_two The other audio file to compare
     * @param channelNumber1 for simplicity we will assume both audio files use the same channel
     * @param rc this callback is notified when the server responds with the comparison result(s).
     * @param async true if this call should NOT block and return after submitting the request to the server.
     * @param transferType true if the files should be sent as a serialized byte array instead of a decoded samples
     * @param regions_one annotated regions in filename_one (regions may be ignored by the plugin)
     * @param regions_two annotated regions in filename_two (regions may be ignored by the plugin)
     * @param options a list of options to pass to the plugin (for both files)
     * @param classIDs optional class ids to use in filtering comparision (may not be supported by the plugin)
     * @return
     * @throws ClientException
     * @throws IOException
     * @throws UnsupportedAudioFileException
     */
    public static boolean requestGlobalCompare(Server server,
                                               Pair<Olive.Plugin, Olive.Domain> plugin,
                                               String filename_one,
                                               String filename_two,
                                               int channelNumber1,
                                               int channelNumber2,
                                               Server.ResultCallback<Olive.GlobalComparerRequest, Olive.GlobalComparerResult> rc,
                                               boolean async, AudioTransferType transferType,
                                               List<RegionWord> regions_one,
                                               List<RegionWord> regions_two,
                                               List<Pair<String, String>> options,
                                               List<String> classIDs) throws ClientException, IOException, UnsupportedAudioFileException {


        if(null == plugin){
            return false;
        }

        Olive.Audio.Builder audio1 = createAudioFromFile(filename_one, channelNumber1, transferType, regions_one);
        Olive.Audio.Builder audio2 = createAudioFromFile(filename_two, channelNumber2, transferType, regions_two);

        Olive.GlobalComparerRequest.Builder req = Olive.GlobalComparerRequest.newBuilder()
                .setPlugin(plugin.getFirst().getId())
                .setDomain(plugin.getSecond().getId());

        for (Pair<String, String> p : options){
            req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
        }

        req.addAllClassId(classIDs);

        req.setAudioOne(audio1);
        req.setAudioTwo(audio2);


        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else {

            Server.Result<Olive.GlobalComparerRequest, Olive.GlobalComparerResult> result = server.synchRequest(req.build());
            rc.call(result);
        }


        return true;

    }

    public static boolean requestAudioAlignment(Server server,
                                               Pair<Olive.Plugin, Olive.Domain> plugin,
                                                Map<String, Map<ChannelClassPair, List<RegionWord>>> fileRegions,
                                               Server.ResultCallback<Olive.AudioAlignmentScoreRequest, Olive.AudioAlignmentScoreResult> rc,
                                               boolean async,
                                                AudioTransferType transferType,
                                               List<Pair<String, String>> options,
                                               List<String> classIDs) throws ClientException, IOException, UnsupportedAudioFileException {


        if(null == plugin){
            return false;
        }

        Olive.AudioAlignmentScoreRequest.Builder req = Olive.AudioAlignmentScoreRequest.newBuilder()
                .setPlugin(plugin.getFirst().getId())
                .setDomain(plugin.getSecond().getId());

        for (Pair<String, String> p : options){
            req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
        }

        req.addAllClassId(classIDs);

        for(String filename : fileRegions.keySet()){
            for(ChannelClassPair ccp : fileRegions.get(filename).keySet()){
                Olive.Audio.Builder audio = createAudioFromFile(filename, ccp.getChannel(), transferType, fileRegions.get(filename).get(ccp));
                req.addAudios(audio);
            }
        }

        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else {

            Server.Result<Olive.AudioAlignmentScoreRequest, Olive.AudioAlignmentScoreResult> result = server.synchRequest(req.build());
            rc.call(result);
        }

        return true;

    }

    public static boolean requestImportClassModel(Server server,
                                                  Pair<Olive.Plugin, Olive.Domain> plugin,
                                                  String enrollmentImportName,
                                                  String classID,
                                                  Server.ResultCallback<Olive.ClassImportRequest, Olive.ClassImportResult> rc,
                                                  boolean async) throws InvalidProtocolBufferException {


        if (null == plugin) {
            return  false;
        }

        // Load the enrollment from file and send the request
        Olive.EnrollmentModel model = null;
        try {
            model = Olive.EnrollmentModel.parseFrom(new FileInputStream(Paths.get(enrollmentImportName).toFile()));
        } catch (IOException e) {
            log.error("Failed to open enrollment file");
            return false;
        }

        Olive.ClassImportRequest.Builder req = Olive.ClassImportRequest.newBuilder()
                .setPlugin(plugin.getFirst().getId())
                .setDomain(plugin.getSecond().getId())
                .setEnrollment(model);

        if (null != classID){
            req.setClassId(enrollmentImportName);   // NOTE import name is optional
        }

        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else{
            Server.Result<Olive.ClassImportRequest, Olive.ClassImportResult> result = server.synchRequest(req.build());
            rc.call(result);
        }

        server.enqueueRequest(req.build(), rc);

        return true;

    }

    public static boolean requestExportClassModel(Server server, Pair<Olive.Plugin, Olive.Domain> plugin, String classID, Server.ResultCallback<Olive.ClassExportRequest, Olive.ClassExportResult> rc, boolean async) throws InvalidProtocolBufferException {


        if (null == plugin) {
            return  false;
        }

        Olive.ClassExportRequest.Builder req = Olive.ClassExportRequest.newBuilder()
                .setPlugin(plugin.getFirst().getId())
                .setDomain(plugin.getSecond().getId())
                .setClassId(classID);


        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else{
            Server.Result<Olive.ClassExportRequest, Olive.ClassExportResult> result = server.synchRequest(req.build());
            rc.call(result);
        }

        return true;

    }


    public static boolean requestTextTransformation(Server server,
                                                    Pair<Olive.Plugin, Olive.Domain> plugin,
                                                    String textInput,
                                                    Server.ResultCallback<Olive.TextTransformationRequest, Olive.TextTransformationResult> rc,
                                                    boolean async,
                                                    List<Pair<String, String>> options,
                                                    List<String> classIDs)  throws  IOException, UnsupportedAudioFileException
    {

        if (null == plugin){
            return false;
        }

        Olive.TextTransformationRequest.Builder req = Olive.TextTransformationRequest.newBuilder()
                .setText(textInput)
                .setPlugin(plugin.getFirst().getId())
                .setDomain(plugin.getSecond().getId());

        // add any option
        for (Pair<String, String> p : options){
            req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
        }

        req.addAllClassId(classIDs);

        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else {
            Server.Result<Olive.TextTransformationRequest, Olive.TextTransformationResult> result = server.synchRequest(req.build());
            rc.call(result);
        }

        return true;

    }


    /**
     * Request region score analysis (KWS) using an audio buffer
     * @param server the SCENIC server handle
     * @param plugin the plugin/domain to use
     * @param filename the audio file to send
     * @param channelNumber
     * @param rc the call back
     * @param async if true, then then this function will return as soon as the request is sent to the server.
*              Otherwise this function will block, waiting for response for the server.  If not async, then the
*              callback will called before this function returns.
     * @param transferType
     * @param options
     * @param classIDs
     * @return scores
     *
     * @throws ClientException if there is a communicaiton error with the server
     */
    public static boolean requestRegionScores(Server server, Pair<Olive.Plugin, Olive.Domain> plugin,
                                              String filename,
                                              int channelNumber,
                                              Server.ResultCallback<Olive.RegionScorerRequest, Olive.RegionScorerResult> rc,
                                              boolean async,
                                              AudioTransferType transferType,
                                              List<RegionWord> regions,
                                              List<Pair<String, String>> options,
                                              List<String> classIDs) throws ClientException, IOException, UnsupportedAudioFileException {  //List<Pair<String, String>> options
        if (null == plugin){
            return false;
        }

        Olive.RegionScorerRequest.Builder req = Olive.RegionScorerRequest.newBuilder()
                .setAudio(createAudioFromFile(filename, channelNumber, transferType, regions))
                .setPlugin(plugin.getFirst().getId())
                .setDomain(plugin.getSecond().getId());

        // add any option
        for (Pair<String, String> p : options){
            req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
        }

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
     * Request bounding box score analysis using an image or video file
     * @param server the OLIVE server handle
     * @param plugin the plugin/domain to use
     * @param filename the audio file to send
     * @param rc the call back
     * @param async if true, then then this function will return as soon as the request is sent to the server.
     *              Otherwise this function will block, waiting for response for the server.  If not async, then the
     *              callback will called before this function returns.
     * @param transferType
     * @param options
     * @param classIDs
     * @return scores
     *
     * @throws ClientException if there is a communication error with the server
     */
    public static boolean requestBoundingBoxScores(Server server, Pair<Olive.Plugin, Olive.Domain> plugin,
                                              String filename,
                                              Server.ResultCallback<Olive.BoundingBoxScorerRequest, Olive.BoundingBoxScorerResult> rc,
                                              boolean async,
                                              AudioTransferType transferType,
                                              List<RegionWord> regions,
                                              List<Pair<String, String>> options,
                                              List<String> classIDs) throws ClientException, IOException, Exception {  //List<Pair<String, String>> options
        if (null == plugin){
            return false;
        }

        // We only send the path or as a serilized buffer

        Olive.BoundingBoxScorerRequest.Builder req = Olive.BoundingBoxScorerRequest.newBuilder()
                .setData(createBinaryMediaFromFile(filename, transferType, regions))
                .setPlugin(plugin.getFirst().getId())
                .setDomain(plugin.getSecond().getId());

        // add any option
        for (Pair<String, String> p : options){
            req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
        }

        req.addAllClassId(classIDs);

        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else {
            Server.Result<Olive.BoundingBoxScorerRequest, Olive.BoundingBoxScorerResult> result = server.synchRequest(req.build());
            rc.call(result);
        }

        return true;

    }


    public static boolean requestEnrollClass(Server server,
                                             Pair<Olive.Plugin, Olive.Domain> pp,
                                             String id,
                                             String wavePath,
                                             int channelNumber,
                                             Server.ResultCallback<Olive.ClassModificationRequest, Olive.ClassModificationResult> rc,
                                             boolean async,
                                             DataType dataType,
                                             AudioTransferType transferType,
                                             List<RegionWord> regions,
                                             List<Pair<String, String>> options) throws ClientException {

        if(null == pp){
            return false;
        }

        try {

            Olive.ClassModificationRequest.Builder req = Olive.ClassModificationRequest.newBuilder()
                    .setClassId(id)
                    .setPlugin(pp.getFirst().getId())
                    .setDomain(pp.getSecond().getId());
            if (null == dataType || dataType == DataType.AUDIO_DATA) {
                req.addAddition(createAudioFromFile(wavePath, channelNumber, transferType, regions));
            }
            else{
                // We assume image or video (binary)
                req = Olive.ClassModificationRequest.newBuilder()
                        .addAdditionMedia(createBinaryMediaFromFile(wavePath, transferType, regions));
            }

            // test sending option
//            req.addOption(Olive.OptionValue.newBuilder().setName("region").setValue("0.5, 1.75, 2.5, 4,6.25, 9"));
//            req.addOption(Olive.OptionValue.newBuilder().setName("isNegative").setValue("true"));

            for (Pair<String, String> p : options){
                req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
            }

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


    public static boolean requestUnenrollClass(Server server, Pair<Olive.Plugin, Olive.Domain> pp , String speaker, Server.ResultCallback<Olive.ClassRemovalRequest, Olive.ClassRemovalResult> rc, boolean async)  {

        try {

            if (null == pp){
                return false;
            }

            // Load the audio vector from disk:
            Olive.ClassRemovalRequest.Builder req = Olive.ClassRemovalRequest.newBuilder()
                    .setClassId(speaker)
                    .setPlugin(pp.getFirst().getId())
                    .setDomain(pp.getSecond().getId());

            log.info("Requesting removal of class ID: '{}' from plugin-domain: ({}-{})", speaker, pp.getFirst().getId(), pp.getSecond().getId());



            if(async){
                server.enqueueRequest(req.build(), rc);
            }
            else {
                Server.Result<Olive.ClassRemovalRequest, Olive.ClassRemovalResult> result = server.synchRequest(req.build());
                rc.call(result);
            }


            return true;
        } catch (Exception e) {
            log.error("Class removal request failed: {}", e);
        }
        return false;

    }


    public static Olive.AudioBitDepth getAudioDepth(AudioFormat format) {
        Olive.AudioBitDepth rtn;
        switch (format.getSampleSizeInBits()) {
            case 8:
                rtn = Olive.AudioBitDepth.BIT_DEPTH_8;
                break;
            case 16:
                rtn = Olive.AudioBitDepth.BIT_DEPTH_16;
                break;
            case 24:
                rtn = Olive.AudioBitDepth.BIT_DEPTH_24;
                break;
            default:
                rtn = Olive.AudioBitDepth.BIT_DEPTH_32;
        }

        return rtn;
    }

    /**
     * Request an  conversion (enhance audio)
     * @param server
     * @param channelNumber
     * @param transferType
     * @param options
     */
    public static boolean requestAudioEnhancement(Server server,
                                                  Pair<Olive.Plugin, Olive.Domain> plugin,
                                                  String filename,
                                                  int channelNumber,
                                                  Server.ResultCallback<Olive.AudioModificationRequest, Olive.AudioModificationResult> rc,
                                                  boolean async,
                                                  AudioTransferType transferType,
                                                  List<RegionWord> regions, List<Pair<String, String>> options) throws IOException, UnsupportedAudioFileException {

        if (null == plugin){
            return false;
        }


        Olive.AudioModificationRequest.Builder req = Olive.AudioModificationRequest.newBuilder().setPlugin(plugin.getFirst().getId()).setDomain(plugin.getSecond().getId());
        // Test sending options:
//         req.setRequestedChannels(2);
//         req.setRequestedRate(16000);
        //req.addOption(Scenic.OptionValue.newBuilder().setName("region").setValue("0.5, 1.75, 2.5, 4,6.25, 9"));


        for (Pair<String, String> p : options){
            req.addOption(Olive.OptionValue.newBuilder().setName(p.getFirst()).setValue(p.getSecond()));
        }


        // Test sending options:
//        req.addOption(Olive.OptionValue.newBuilder().setName("region").setValue("0.5, 1.75, 2.5, 4,6.25, 9"));

        req.addModifications(createAudioFromFile(filename, channelNumber, transferType, regions));
//        req.addModifications(createAudioFromFile("/Users/e24652/audio/sad_smoke.wav", channelNumber, transferType, regions));

        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else {

            Server.Result<Olive.AudioModificationRequest, Olive.AudioModificationResult> result = server.synchRequest(req.build());
            rc.call(result);
        }


        return true;

    }


    /**
     * Request unloading of a plugin
     *
     * @param server the server
     */
    public static boolean requestUnloadPlugin(Server server,
                                      Pair<Olive.Plugin, Olive.Domain> plugin,
                                      Server.ResultCallback<Olive.RemovePluginDomainRequest, Olive.RemovePluginDomainResult> rc,
                                      boolean async) throws IOException, UnsupportedAudioFileException {

        if(null == plugin){
            return false;
        }


        Olive.RemovePluginDomainRequest.Builder req = Olive.RemovePluginDomainRequest.newBuilder().setPlugin(plugin.getFirst().getId()).setDomain(plugin.getSecond().getId());

        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else{
            Server.Result<Olive.RemovePluginDomainRequest, Olive.RemovePluginDomainResult> result = server.synchRequest(req.build());
            rc.call(result);
        }

        return true;

    }

    public static boolean requestAudioVector(Server server,
                                             Pair<Olive.Plugin, Olive.Domain> plugin,
                                             String filename,
                                             int channelNumber,
                                             Server.ResultCallback<Olive.PluginAudioVectorRequest, Olive.PluginAudioVectorResult> rc,
                                             boolean async,
                                             AudioTransferType transferType,
                                             List<RegionWord> regions) throws IOException, UnsupportedAudioFileException {

        if(null == plugin){
            return false;
        }


        Olive.PluginAudioVectorRequest.Builder req = Olive.PluginAudioVectorRequest.newBuilder().setPlugin(plugin.getFirst().getId()).setDomain(plugin.getSecond().getId());

        req.addAddition(createAudioFromFile(filename, channelNumber, transferType, regions));

        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else{
            Server.Result<Olive.PluginAudioVectorRequest, Olive.PluginAudioVectorResult> result = server.synchRequest(req.build());
            rc.call(result);
        }

        return true;

    }



    public static boolean requestGetUpdateStatus(Server server,
                                                 Pair<Olive.Plugin, Olive.Domain> pp,
                                                 Server.ResultCallback<Olive.GetUpdateStatusRequest, Olive.GetUpdateStatusResult> rc,
                                                 boolean async) throws ClientException, IOException, UnsupportedAudioFileException {

        // make sure a valid plugin is sent
        if (null == pp){
            return false;
        }

        Olive.GetUpdateStatusRequest.Builder req = Olive.GetUpdateStatusRequest.newBuilder()
                .setPlugin(pp.getFirst().getId())
                .setDomain(pp.getSecond().getId());


        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else {
            Server.Result<Olive.GetUpdateStatusRequest, Olive.GetUpdateStatusResult> result = server.synchRequest(req.build());
            rc.call(result);
        }


        return true;
    }

    public static boolean requestApplyUpdate(Server server,
                                                 Pair<Olive.Plugin, Olive.Domain> pp,
                                                 Server.ResultCallback<Olive.ApplyUpdateRequest, Olive.ApplyUpdateResult> rc,
                                                 boolean async) throws ClientException, IOException, UnsupportedAudioFileException {

        // make sure a valid plugin is sent
        if (null == pp){
            return false;
        }

        Olive.ApplyUpdateRequest.Builder req = Olive.ApplyUpdateRequest.newBuilder()
                .setPlugin(pp.getFirst().getId())
                .setDomain(pp.getSecond().getId());

        // todo set options?



        if (async) {
            server.enqueueRequest(req.build(), rc);
        }
        else {
            Server.Result<Olive.ApplyUpdateRequest, Olive.ApplyUpdateResult> result = server.synchRequest(req.build());
            rc.call(result);
        }


        return true;
    }




    /**
     *
     * Attempts to find a plugin that matches one or more of the filter options.
     * <ul><li>
     *     If pluginName and domainName are specified then attempt to return that plugin without checking other options
     *     </li>
     * </ul>
     *
     * Find and return a plugin/domain that supports the trait 'type'.  Optionally specify a plugin and/or domain name to return a specific plugin-domain.  You can
     *
     *
     * @param pluginName  optional name of a plugin
     * @param domainName optional name  of a domain
     * @param taskName the optional task name  supported for the plugin  (i.e "SAD")
     * @param type a trait type supported by the plugin, this is the only requuired option
     * @param pluginList the available plugins
     *
     * @return a matching plugin/domain or null if no match was found
     */
    public static Pair<Olive.Plugin, Olive.Domain> findPluginDomainByTrait(String pluginName, String domainName, String taskName, Olive.TraitType type, List<Pair<Olive.Plugin, Olive.Domain>> pluginList){

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
    }

    /**
     * Confirm that the trait(s) supported by a plugin, pluginTratis, matches one or more of the traits in filterTraits.
     *
     * @param pluginTraits the list if traits supported by the plugin
     * @param filterTraits filter the plugin traits by this list, returning true if a match is found or this list is empty (no filtering)
     * @return true if a match is found or the filter list is empty
     *
     */
    public static boolean isTraitSupported(List<Olive.Trait> pluginTraits, List<Olive.TraitType> filterTraits){

        if (filterTraits.isEmpty()) return true;

        boolean traitSupported  = false;

        for(Olive.TraitType filterTrait : filterTraits) {
            for (Olive.Trait t : pluginTraits) {
                traitSupported = t.getType() == filterTrait;
                //  check for derived trait types (i.e. ClassEnroller->ClassModifier)
                if (!traitSupported && filterTrait == Olive.TraitType.CLASS_ENROLLER) {
                    traitSupported = t.getType() == Olive.TraitType.CLASS_MODIFIER;
                }
                if (traitSupported) break;
            }
        }

        return  traitSupported;
    }

    public static Pair<Olive.Plugin, Olive.Domain> findPluginDomain(String pluginName, String domainName, List<Pair<Olive.Plugin, Olive.Domain>> pluginList ){

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
    }

    private static String formatPluginErrorMsg(String pluginName, String domainName, Olive.TraitType type){
        return String.format("No plugin-domain found having trait %s, plugin name: '%s' and domain: '%s' ", type.toString(), null == pluginName ? "*" : pluginName, null == domainName ? "*" : domainName);
    }

  /*   public static Olive.Audio.Builder packageAudioAsPath(String wavFileName,  int channelNumber, List<RegionWord> regions){

      Olive.Audio.Builder audioBuilder = Olive.Audio.newBuilder().setPath(wavFileName);

      // For multi-channel (stereo) audio we have the option of specifying the channel to process, otherwise the file is treated as mono (if stereo)
      if (channelNumber > 0) {
          audioBuilder.setChannel(channelNumber);
      }


      // Add optional region annotations
      if (null != regions) {
          for (RegionWord word : regions) {
              audioBuilder.addRegions(Olive.AnnotationRegion.newBuilder().setStartT(word.start).setEndT(word.end));
          }
      }


      return audioBuilder;

  }*/

    /**
     * @param filename the filename to load into an Audio message
     * @param transferType how to send data to the client - either file or
     * @param regions optional regions, may be null. Not supported by all plugins
     * @return An Audio message
     * @throws Exception
     * @throws UnsupportedAudioFileException
     */
    public static Olive.BinaryMedia.Builder createBinaryMediaFromFile(
            String filename,
            AudioTransferType transferType,
            List<RegionWord> regions)
            throws Exception {

        Olive.BinaryMedia.Builder mediaBuilder = Olive.BinaryMedia.newBuilder();
        Olive.BinaryBuffer.Builder vbuff;

        switch (transferType) {
            case SEND_AS_PATH:
                mediaBuilder.setPath(filename);
                break;
            case SEND_SAMPLES_BUFFER:
                // Not supported for images/videos
                throw new Exception("Unable to send binary data as a decoded buffer");
            case SEND_SERIALIZED_BUFFER:
            default:
                // Serialize the file to a buffer
                // Not necessary to set the audio format info...
                byte[] serialized = Files.readAllBytes(Paths.get(filename));
                // Set FPS?
                vbuff = Olive.BinaryBuffer.newBuilder()
                                .setData(ByteString.copyFrom(serialized));
                mediaBuilder.setBuffer(vbuff.build());
                break;
        }

       // Optionally set this?
        mediaBuilder.setLabel(filename);

        return mediaBuilder;
    }


    /**
   * @param filename the filename to load into an Audio message
   * @param channelNumber sets the channel number to use when processing stereo audio (use a value
   *     of zero for mono files, or when stereo should be treated as mono)
   * @param transferType how to send audio to the client
   * @param regions optional regions, may be null. Not supported by all plugins
   * @return An Audio message
   * @throws IOException
   * @throws UnsupportedAudioFileException
   */
  public static Olive.Audio.Builder createAudioFromFile(
      String filename,
      int channelNumber,
      AudioTransferType transferType,
      List<RegionWord> regions)
      throws IOException, UnsupportedAudioFileException {

    Olive.Audio.Builder audioBuilder = Olive.Audio.newBuilder();
    Olive.AudioBuffer.Builder abuff;

    switch (transferType) {
      case SEND_AS_PATH:
        audioBuilder.setPath(filename);
        break;
      case SEND_SAMPLES_BUFFER:
        // Extract samples from the file:
        AudioInputStream ais = AudioUtil.convertWave2Stream(Paths.get(filename).toFile());
        byte[] samples = AudioUtil.convertWav2ByteArray(ais);
        abuff =
            Olive.AudioBuffer.newBuilder()
                // .setEncoding(Olive.AudioEncodingType.PCM16)  Does not need to be set - for
                // future use
                .setChannels(ais.getFormat().getChannels())
                .setRate((int) ais.getFormat().getSampleRate())
                .setSamples(samples.length)
                .setBitDepth(ClientUtils.getAudioDepth(ais.getFormat()));
        abuff.setData(ByteString.copyFrom(samples));
        audioBuilder.setAudioSamples(abuff.build());
        break;

      case SEND_SERIALIZED_BUFFER:
      default:
        // Serialize the file to a buffer
        // Not necessary to set the audio format info...
        byte[] serialized = Files.readAllBytes(Paths.get(filename));
        abuff =
            Olive.AudioBuffer.newBuilder()
                // .setEncoding()// optional, not needed
                .setSerializedFile(true)
                .setChannels(0)
                .setRate(0)
                .setSamples(0)
                .setBitDepth(Olive.AudioBitDepth.BIT_DEPTH_16); // Can't leave blank
        abuff.setData(ByteString.copyFrom(serialized));
        audioBuilder.setAudioSamples(abuff.build());
        break;
    }

    // For multi-channel (stereo) audio we have the option of specifying the channel to process,
    // otherwise the file is treated as mono (if stereo)
    if (channelNumber > 0) {
      // return Olive.Audio.newBuilder().setAudioSamples(abuff.build()).setChannel(channelNumber);
      audioBuilder.setSelectedChannel(channelNumber);
    }

    if (null != regions) {
      for (RegionWord word : regions) {
        audioBuilder.addRegions(
            Olive.AnnotationRegion.newBuilder().setStartT(word.getStartTimeSeconds()).setEndT(word.getEndTimeSeconds()));
      }
    }
    // Optionall set this?
    audioBuilder.setLabel(filename);

    return audioBuilder;
  }

    public static  List<SADWord> thresholdFrames(Olive.FrameScores fs, double thresh) {

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


}
