package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import java.io.IOException;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * Handler for playing sounds and tones on the device
 *
 * @author Terence Sun (tsun1215)
 */
public class SoundHandler implements RequestHandler, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener {
    private static final String TAG = SoundHandler.class.getName();
    private static final String SOUNDS_DIR = "frontend/SoundClips";

    /* Constants for playing tones */
    private static double AUDIO_SAMPLING_RATE = 44100.0;
    private static int AUDIO_NUM_CHANNELS = 2;
    private static double MILLIS_PER_SEC = 1000.0;

    private HttpService service;
    private MediaPlayer mediaPlayer;
    private AudioTrack tone;

    public SoundHandler(HttpService service) {
        this.service = service;
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");

        // Generate response body
        String responseBody = "";
        switch (path[0]) {
            case "names":
                responseBody = listSounds();
                break;
            case "duration":
                responseBody = getDuration(path[1]);
                break;
            case "play":
                playSound(path[1]);
                break;
            case "note":
                playNote(Integer.valueOf(path[1]), Integer.valueOf(path[2]));
                break;
            case "stop":
                stopSound();
                break;
            case "stop_all":
                stopAll();
                break;
        }


        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }

    /**
     * Lists all the sounds that the app supports
     *
     * @return String list of all the sounds
     */
    private String listSounds() {
        AssetManager assets = service.getAssets();
        try {
            String[] sounds = assets.list(SOUNDS_DIR);
            StringBuilder responseBuilder = new StringBuilder();
            for (String sound : sounds) {
                responseBuilder.append(sound.substring(0, sound.indexOf(".wav")) + "\n");
            }
            return responseBuilder.toString();
        } catch (IOException e) {
            Log.e(TAG, "Unable to list sounds " + e.toString());
            return "";
        }
    }

    /**
     * Gets the duration of the given sound
     *
     * @param soundId The sound's id
     * @return Duration in millseconds of the sound
     */
    private synchronized String getDuration(String soundId) {
        String path = SOUNDS_DIR + "/%s.wav";
        try {
            AssetManager assets = service.getAssets();
            AssetFileDescriptor fd = assets.openFd(String.format(path, soundId));
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            mediaPlayer.prepare();
            String response = Integer.toString(mediaPlayer.getDuration());
            mediaPlayer.release();
            return response;
        } catch (IOException e) {
            Log.e(TAG, "Unable to get sound duration " + e.toString());
            return "0";
        }
    }

    /**
     * Plays the given sound by its id
     *
     * @param soundId The sound's id
     */
    private synchronized void playSound(String soundId) {
        String path = SOUNDS_DIR + "/%s.wav";
        try {
            AssetManager assets = service.getAssets();
            AssetFileDescriptor fd = assets.openFd(String.format(path, soundId));
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.prepare();
        } catch (IOException e) {
            Log.e(TAG, "Unable to play sound " + e.toString());
            return;
        }
    }

    /**
     * Plays the given note number for the given duration
     *
     * @param noteNumber Midi note number
     * @param durationMs Duration in milliseconds
     */
    private void playNote(final int noteNumber, final int durationMs) {
        new Thread(new Runnable() {
            public void run() {
                tone = generateTone(midiNoteToHz(noteNumber), durationMs);
                tone.play();
            }
        }).start();
    }

    /**
     * Stops sounds
     */
    private void stopSound() {
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }
        } catch (IllegalStateException e) {

        }
    }

    /**
     * Stops all sounds (including the tone)
     */
    private void stopAll() {
        if (tone != null) {
            tone.stop();
            tone = null;
        }
        stopSound();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mp.release();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();
    }

    /**
     * Converts a MIDI note number into Hz
     *
     * @param noteNumber MIDI note number
     * @return
     */
    private static int midiNoteToHz(int noteNumber) {
        double a = 440.0;  // A in hz
        return (int) ((a / 32) * (Math.pow(2.0, ((noteNumber - 9) / 12.0))));
    }

    /**
     * Generates a tone from a frequency in Hz for a set duration (in milliseconds)
     * <p>
     * SOURCE: https://gist.github.com/slightfoot/6330866
     *
     * @param freqHz     Frequency of tone
     * @param durationMs Duration of tone
     * @return Generated tone
     */
    private AudioTrack generateTone(double freqHz, int durationMs) {
        int count = (int) (AUDIO_SAMPLING_RATE * AUDIO_NUM_CHANNELS
                * (durationMs / MILLIS_PER_SEC)) & ~1;  //  Clear zeroth bit to make even
        short[] samples = new short[count];
        for (int i = 0; i < count; i += 2) {
            short sample = (short) (Math.sin(2 * Math.PI *
                    i / (AUDIO_SAMPLING_RATE / freqHz)) * 0x7FFF);
            samples[i + 0] = sample;
            samples[i + 1] = sample;
        }
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, (int) AUDIO_SAMPLING_RATE,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                count * (Short.SIZE / 8), AudioTrack.MODE_STATIC);
        track.write(samples, 0, count);
        return track;
    }
}
