package com.birdbraintechnologies.birdblox.httpservice.RequestHandlers;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.text.TextUtils;
import android.util.Log;

import com.birdbraintechnologies.birdblox.Sound.CancelableMediaPlayer;
import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static com.birdbraintechnologies.birdblox.MainWebView.mediaPlayers;
import static com.birdbraintechnologies.birdblox.MainWebView.tones;
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

/**
 * Handler for playing sounds and tones on the device
 *
 * @author Terence Sun (tsun1215)
 */
public class SoundHandler implements RequestHandler, CancelableMediaPlayer.OnPreparedListener,
        CancelableMediaPlayer.OnCompletionListener {
    private static final String TAG = SoundHandler.class.getName();
    private static final String SOUNDS_DIR = "frontend/SoundClips";
    private static final String BLOCK_SOUNDS_DIR = "frontend/SoundsForUI";

    /**
     * Constants for playing tones
     */
    /* The sampling rate in Hz */
    private static double AUDIO_SAMPLING_RATE = 44100.0;
    /* The number of channels - only allowed values are 1 (mono) and 2 (stereo) */
    private static int AUDIO_NUM_CHANNELS = 2;
    /* Number of milliseconds per second*/
    private static double MILLIS_PER_SEC = 1000.0;

    private HttpService service;
    private CancelableMediaPlayer mediaPlayer;

    public SoundHandler(HttpService service) {
        this.service = service;
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        // Generate response body
        String responseBody = "";
        switch (path[0]) {
            case "names":
                if (m.get("type") == null || m.get("type").get(0).equals("effect"))
                    responseBody = listSounds(false);
                else if (m.get("type").get(0).equals("recording"))
                    responseBody = listSounds(true);
                else {
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Unknown sound type");
                }
                break;
            case "duration":
                if (m.get("type") == null || m.get("type").get(0).equals("effect"))
                    responseBody = getDuration(m.get("filename").get(0), false);
                else if (m.get("type").get(0).equals("recording"))
                    responseBody = getDuration(m.get("filename").get(0), true);
                else {
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Unknown sound type");
                }
                break;
            case "play":
                if (m.get("type") == null || m.get("type").get(0).equals("effect"))
                    playSound(m.get("filename").get(0), "effect");
                else if (m.get("type").get(0).equals("recording"))
                    playSound(m.get("filename").get(0), "recording");
                else if (m.get("type").get(0).equals("ui"))
                    playSound(m.get("filename").get(0), "ui");
                else {
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Unknown sound type");
                }
                break;
            case "note":
                if (Integer.valueOf(m.get("note").get(0)) != 0 && Integer.valueOf(m.get("duration").get(0)) != 0) {
                    playNote(Integer.valueOf(m.get("note").get(0)), Integer.valueOf(m.get("duration").get(0)));
                }
                break;
            case "stop":
                stopSound();
                break;
            case "stopAll":
                stopAll();
                break;
        }

        NanoHTTPD.Response r = newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }

    /**
     * Lists all the sounds that the app supports
     *
     * @param recording true if the requested sounds are recordings, false otherwise
     * @return String list of all the sounds
     */
    private String listSounds(boolean recording) {
        if (recording) {
            return (new RecordingHandler()).listRecordings();
        }
        AssetManager assets = service.getAssets();
        try {
            String[] sounds = assets.list(SOUNDS_DIR);
            for (int i = 0; i < sounds.length; i++) {
                sounds[i] = sounds[i].substring(0, sounds[i].indexOf(".wav"));
            }
            return TextUtils.join("\n", sounds);
        } catch (IOException e) {
            Log.e(TAG, "Unable to list sounds " + e.toString());
            return "";
        }
    }

    /**
     * Gets the duration of the given sound
     *
     * @param soundId   The sound's id
     * @param recording true if the requested sound is a recording, false otherwise
     * @return Duration in milliseconds of the sound
     */
    private synchronized String getDuration(String soundId, boolean recording) {
        if (recording) {
            return (new RecordingHandler()).getDuration(soundId);
        }
        String path = SOUNDS_DIR + "/%s.wav";
        try {
            AssetManager assets = service.getAssets();
            AssetFileDescriptor fd = assets.openFd(String.format(path, soundId));
            CancelableMediaPlayer mediaPlayer = new CancelableMediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            mediaPlayer.prepare();
            String response = Integer.toString(mediaPlayer.getDuration());
            mediaPlayer.reset();
            mediaPlayer.release();
            return response;
        } catch (IOException e) {
            Log.e(TAG, "Unable to get sound duration " + e.toString());
            return "0";
        }
    }

    /**
     * Plays the given sound by its id/filename
     *
     * @param soundId The sound's id/filename
     * @param type    "recording" if the requested sound is a recording,
     *                "ui" if block sound, and an effect sound otherwise
     */
    private synchronized void playSound(final String soundId, final String type) {
        final String path;
        if (type.equals("recording")) {
            (new RecordingHandler()).playAudio(soundId);
            return;
        } else if (type.equals("ui")) {
            path = BLOCK_SOUNDS_DIR + "/%s.wav";
            new Thread() {
                @Override
                public void run() {
                    try {
                        AssetManager assets = service.getAssets();
                        AssetFileDescriptor fd = assets.openFd(String.format(path, soundId));
                        CancelableMediaPlayer mediaPlayer = new CancelableMediaPlayer();
                        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mediaPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                        mediaPlayer.prepare();
                        mediaPlayer.start();
                    } catch (IOException | IllegalStateException e) {
                        Log.e(TAG, "Unable to play sound " + e.toString());
                    }
                }
            }.start();
        } else {
            path = SOUNDS_DIR + "/%s.wav";
            try {
                AssetManager assets = service.getAssets();
                AssetFileDescriptor fd = assets.openFd(String.format(path, soundId));
                mediaPlayer = new CancelableMediaPlayer();
                synchronized (mediaPlayers) {
                    if (mediaPlayers != null)
                        mediaPlayers.add(mediaPlayer);
                }
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                mediaPlayer.setOnCompletionListener(this);
                mediaPlayer.setOnPreparedListener(this);
                mediaPlayer.prepare();
            } catch (IOException | IllegalStateException e) {
                Log.e(TAG, "Unable to play sound " + e.toString());
            }
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
                generateTone(midiNoteToHz(noteNumber), durationMs);
            }
        }).start();
    }

    /**
     * Stops sounds (except tones)
     */
    private void stopSound() {
        try {
            (new RecordingHandler()).stopPlayback();
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                if (mediaPlayer.isPlaying())
                    mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.release();
            } else if (mediaPlayer != null) {
                mediaPlayer.cancel();
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Stop Sounds: " + e.getMessage());
        }
    }

    /**
     * Stops all sounds (including the tone)
     */
    private void stopAll() {
        synchronized (mediaPlayers) {
            if (mediaPlayers != null) {
                for (int i = 0; i < mediaPlayers.size(); i++) {
                    if (mediaPlayers.get(i) != null) {
                        try {
                            mediaPlayers.get(i).cancel();
                            if (mediaPlayers.get(i).isPlaying())
                                mediaPlayers.get(i).stop();
                            mediaPlayers.get(i).reset();
                            mediaPlayers.get(i).release();
                            mediaPlayers.remove(i);
                        } catch (IllegalStateException | ArrayIndexOutOfBoundsException | NullPointerException e) {
                            Log.e(TAG, "Stop All Sounds: " + e.getMessage());
                        }
                    }
                }
            }
        }
        synchronized (tones) {
            if (tones != null) {
                for (int i = 0; i < tones.size(); i++) {
                    releaseTrack(tones.get(i));
                    tones.remove(i);
                }
            }
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mp.release();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mp instanceof CancelableMediaPlayer) {
            if (!((CancelableMediaPlayer) mp).isCanceled()) {
                mp.start();
            } else {
                mp.reset();
                mp.release();
            }
        } else {
            mp.start();
        }
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
    private void generateTone(double freqHz, int durationMs) {
        int count = (int) (AUDIO_SAMPLING_RATE * AUDIO_NUM_CHANNELS
                * (durationMs / MILLIS_PER_SEC)) & ~1;  //  Clear zeroth bit to make even
        short[] samples = new short[count];
        for (int i = 0; i < count; i += 2) {
            short sample = (short) (Math.sin(2 * Math.PI *
                    i / (AUDIO_SAMPLING_RATE / freqHz)) * 0x7FFF);
            samples[i + 0] = sample;
            samples[i + 1] = sample;
        }
        int bufferSize = count * (Short.SIZE / 8);
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, (int) AUDIO_SAMPLING_RATE,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                bufferSize, AudioTrack.MODE_STATIC);
        if (track.getState() == AudioTrack.STATE_UNINITIALIZED) {
            releaseTrack(track);
        } else {
            track.setNotificationMarkerPosition(track.getPlaybackHeadPosition() + msToSamples(durationMs));
            track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override
                public void onMarkerReached(AudioTrack track) {
                    releaseTrack(track);
                }

                @Override
                public void onPeriodicNotification(AudioTrack track) {
                    // nothing to do here
                }
            });
            track.write(samples, 0, count);
            if (track.getState() == AudioTrack.STATE_INITIALIZED) {
                try {
                    track.play();
                    synchronized (tones) {
                        if (tones != null)
                            tones.add(track);
                    }
                } catch (IllegalStateException e) {
                    releaseTrack(track);
                }
            } else {
                releaseTrack(track);
            }
        }
    }

    /**
     * Releases a given AudioTrack
     * (Stops it first, then releases if it is playing)
     *
     * @param track The AudioTrack to be released
     */
    private void releaseTrack(AudioTrack track) {
        if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            try {
                track.pause();
                track.flush();
                track.stop();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error while stopping tone: " + e.getMessage());
            }
        }
        try {
            track.release();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error while releasing tone: " + e.getMessage());
        }
    }

    /**
     * Converts milliseconds to samples of buffer.
     *
     * @param ms The time in milliseconds
     * @return The size of the buffer in samples
     */
    private int msToSamples(final int ms) {
        return (int) (AUDIO_SAMPLING_RATE * (ms / MILLIS_PER_SEC));
    }
}
