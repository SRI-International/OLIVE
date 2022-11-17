package com.sri.speech.olive.api.utils;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;


/**
 *  Utility for converting raw audio data from/to wave data.
 */
public final class AudioUtil {

    public final static int AUDIO_SAMPLE_RATE = 16000; // Hz
    public final static int AUDIO_SAMPLE_SIZE = Short.SIZE;
    public final static int AUDIO_FRAME_RATE = 16000;

    /**
     * Convert raw audio sample into a wave formatted stream  that can be saved to a file as a wave file.
     *
     * @param samples  raw PCM 16 bit samples at 16000Hz
     * @return a stream of data that be save directly to a "wave" file
     * @throws java.io.IOException
     */
    public static ByteArrayOutputStream convertSamples2Wav(short[] samples) throws IOException {

        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2);
        for (int i = 0; i < samples.length; i++) {
            buffer.putShort(samples[i]);
        }

        ByteArrayOutputStream audioOut = new ByteArrayOutputStream();
        AudioFormat af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, AUDIO_SAMPLE_RATE, AUDIO_SAMPLE_SIZE, 1, AUDIO_SAMPLE_SIZE/ 8, AUDIO_FRAME_RATE, false);
        AudioInputStream is = new AudioInputStream(new ByteArrayInputStream(buffer.array()), af, samples.length);

        // convert to a wave
        AudioSystem.write(is, AudioFileFormat.Type.WAVE, audioOut);
        is.close();

        return  audioOut;
    }


    /**
     *
     * @param samples the raw samples to convert
     * @param numSamples the number of samples
     * @param sampleRate  the sample rate (should be either 1600 or 8000)
     *
     * @return  a data stream that can be saved directly as a wave file
     * @throws IOException
     */
    public static ByteArrayOutputStream convertSamples2Wav(byte[] samples, int numSamples, int sampleRate) throws IOException {

        // TODO send endian flag?

        //AUDIO_SAMPLE_RATE -> 16000
        //AUDIO_SAMPLE_SIZE -> Short.Size (16) [sample size in bits]

        ByteArrayOutputStream audioOut = new ByteArrayOutputStream();
        //AudioFormat af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, AUDIO_SAMPLE_SIZE, 1, AUDIO_SAMPLE_SIZE/ 8, AUDIO_FRAME_RATE, false);
        AudioFormat af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, AUDIO_SAMPLE_SIZE, 1, AUDIO_SAMPLE_SIZE/ 8, sampleRate, false); // needs to be false for audio conversion?
        AudioInputStream is = new AudioInputStream(new ByteArrayInputStream(samples), af, numSamples);

        // convert to a wave
        AudioSystem.write(is, AudioFileFormat.Type.WAVE, audioOut);
        is.close();

        return  audioOut;
    }

    public static ByteArrayOutputStream convertSamples2Wav(byte[] samples, int numSamples) throws IOException {


        ByteArrayOutputStream audioOut = new ByteArrayOutputStream();
        AudioFormat af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, AUDIO_SAMPLE_RATE, AUDIO_SAMPLE_SIZE, 1, AUDIO_SAMPLE_SIZE/ 8, AUDIO_FRAME_RATE, false);
        AudioInputStream is = new AudioInputStream(new ByteArrayInputStream(samples), af, numSamples);

        // convert to a wave
        AudioSystem.write(is, AudioFileFormat.Type.WAVE, audioOut);
        is.close();

        return  audioOut;
    }

    /**
     * Save a wave data to a wave file.  Data must be in wave (not raw) format
     *
     * @throws IOException
     *
     * @see #convertSamples2Wav(short[])
     */
    public static void saveWave2File(byte[] audio, Path dest) throws IOException {

        try(InputStream wrapper  = new ByteArrayInputStream(audio)){
            Files.copy(wrapper, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void saveSamples2Wave(short[] samples, Path dest) throws IOException {

        ByteArrayOutputStream baos = convertSamples2Wav(samples);
        saveSamples2Wave(baos, dest);
    }

    public static void saveSamples2Wave(ByteArrayOutputStream baos, Path dest) throws IOException {

        saveWave2File(baos.toByteArray(), dest);
    }



    /**
     *
     * Convert a wav file back to raw data (16bit pcm, 16000 Hz)
     */
    public static AudioInputStream convertWave2Stream(File audioFile) throws IOException, UnsupportedAudioFileException {
        return AudioSystem.getAudioInputStream(audioFile);
    }

    public static AudioInputStream convertWave2Stream(InputStream input, int length) throws IOException, UnsupportedAudioFileException {
        AudioFormat af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, AUDIO_SAMPLE_RATE, AUDIO_SAMPLE_SIZE, 1, AUDIO_SAMPLE_SIZE/ 8, AUDIO_FRAME_RATE, false);
        return new AudioInputStream(input, af, length);
    }

    /**
     * Convert a wave file
     *
     * @param is a wave file inputsteam
     *
     * @return raw 16bit samples  @ 16000 Hz
     *
     * @throws IOException
     * @throws UnsupportedAudioFileException
     */
    public static short[] convertWav2Samples(AudioInputStream is) throws IOException, UnsupportedAudioFileException {


        //ByteBuffer bb = convertWav2Buffer(is);
        byte[] data = convertWav2Buffer(is);

        ByteBuffer bb = ByteBuffer.allocate(data.length);
        bb.put(data);
        bb.rewind();


        int  total = bb.capacity();
        short[] shortArray = new short[total/2];
        for(int i = 0; i < total/2; i++){
            shortArray[i] = bb.getShort();
        }

        //is.close();
        return shortArray;
    }

    /**
     * Convert a wave file to raw bytes
     *
     * @param is wave file inputsteam
     *
     * @return raw 16bit samples @ 16000 Hz
     *
     * @throws IOException
     * @throws UnsupportedAudioFileException
     */
    public static byte[] convertWav2ByteArray(AudioInputStream is) throws IOException, UnsupportedAudioFileException {

        return convertWav2Buffer(is);

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


    public static short[] convertBytes2Shorts(byte[] input)  {

        // todo big or little endian?

        // convert to a short array
        ByteBuffer bb = ByteBuffer.allocate(input.length);
        bb.put(input);
        bb.rewind();
        short[] shortArray = new short[input.length/Short.SIZE/8];
        for(int i = 0; i < shortArray.length; i++){
            shortArray[i] = bb.getShort();
        }

        return shortArray;

    }

    public static double getSampleDuration(int numBytes, AudioFormat af){
        return numBytes/(af.getSampleSizeInBits()/8)/af.getSampleRate();
    }
}

