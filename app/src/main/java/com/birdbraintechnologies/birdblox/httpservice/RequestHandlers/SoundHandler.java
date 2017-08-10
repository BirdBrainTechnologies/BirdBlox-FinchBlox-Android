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

    /* Constants for playing tones */
    private static double AUDIO_SAMPLING_RATE = 44100.0;
    private static int AUDIO_NUM_CHANNELS = 2;
    private static double MILLIS_PER_SEC = 1000.0;

    private HttpService service;
    private CancelableMediaPlayer mediaPlayer;
    private AudioTrack tone;

    private boolean stopped = false;

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
                    //bad request
                }
                break;
            case "duration":
                if (m.get("type") == null || m.get("type").get(0).equals("effect"))
                    responseBody = getDuration(m.get("filename").get(0), false);
                else if (m.get("type").get(0).equals("recording"))
                    responseBody = getDuration(m.get("filename").get(0), true);
                else {
                    //bad request
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
                    //bad request
                }
                break;
            case "note":
                playNote(Integer.valueOf(m.get("note").get(0)), Integer.valueOf(m.get("duration").get(0)));
                break;
            case "stop":
                stopSound();
                break;
            case "stopAll":
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
            StringBuilder responseBuilder = new StringBuilder();
            for (String sound : sounds) {
                responseBuilder.append(sound.substring(0, sound.indexOf(".wav")));
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
            mediaPlayer = new CancelableMediaPlayer();
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
    private synchronized void playSound(String soundId, String type) {
        String path;
        if (type.equals("recording")) {
            (new RecordingHandler()).playAudio(soundId);
            return;
        } else if (type.equals("ui")) {
            path = BLOCK_SOUNDS_DIR + "/%s.wav";
            new Thread() {
                private void run(String soundId, String path) {
                    try {
                        AssetManager assets = service.getAssets();
                        AssetFileDescriptor fd = assets.openFd(String.format(path, soundId));
                        mediaPlayer = new CancelableMediaPlayer();
                        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mediaPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                        mediaPlayer.prepare();
                        mediaPlayer.start();
                    } catch (IOException | IllegalStateException e) {
                        Log.e(TAG, "Unable to play sound " + e.toString());
                    }
                }
            }.run(soundId, path);
        } else {
            path = SOUNDS_DIR + "/%s.wav";
            try {
                AssetManager assets = service.getAssets();
                AssetFileDescriptor fd = assets.openFd(String.format(path, soundId));
                mediaPlayer = new CancelableMediaPlayer();
                mediaPlayers.add(mediaPlayer);
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
                try {
//                    tone = generateTone(midiNoteToHz(noteNumber), durationMs);
//                    tone.play();
//                    tones.add(generateTone(midiNoteToHz(noteNumber), durationMs));
                    generateTone(midiNoteToHz(noteNumber), durationMs);
                } catch (IllegalStateException | NullPointerException e) {
                    Log.e("SoundHandler", "Play tone: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Stops sounds
     */
    private void stopSound() {
        if (tone != null) {
            try {
                tone.flush();
                if (tone.getPlayState() == AudioTrack.PLAYSTATE_PLAYING || tone.getPlayState() == AudioTrack.PLAYSTATE_PAUSED)
                    tone.stop();
                tone.release();
            } catch (IllegalStateException e) {
                Log.e("SoundHandler", "Stop Tone: " + e.getMessage());
            }
            tone = null;
        }
        try {
            (new RecordingHandler()).stopPlayback();
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.release();
            } else if (mediaPlayer != null) {
                mediaPlayer.cancel();
            }
            Log.d("StopSound", "Stop Request Sent");
        } catch (IllegalStateException e) {
            Log.e("SoundHandler", "Stop Sounds: " + e.getMessage());
        }
    }

    /**
     * Stops all sounds (including the tone)
     */
    private void stopAll() {
        if (mediaPlayers != null) {
            for (int i = 0; i < mediaPlayers.size(); i++) {
                if (mediaPlayers.get(i) != null) {
                    try {
                        if (mediaPlayers.get(i).isPlaying())
                            mediaPlayers.get(i).stop();
                        mediaPlayers.get(i).cancel();
                        mediaPlayers.get(i).reset();
                        mediaPlayers.get(i).release();
                    } catch (IllegalStateException | ArrayIndexOutOfBoundsException | NullPointerException e) {
                        Log.e("SoundHandler", "Stop Sounds: " + e.getMessage());
                    }
                }
                mediaPlayers.clear();
            }
        }
//        mediaPlayers = new ArrayList<>();
//        if (tones != null) {
//            for (int i = 0; i < tones.size(); i++) {
//                if (tones.get(i) != null) {
//                    try {
//                        tones.get(i).flush();
//                        try {
//                            tones.get(i).stop();
//                        } catch (IllegalStateException e) {
//                        }
//                        tones.get(i).release();
//                    } catch (IllegalStateException | ArrayIndexOutOfBoundsException | NullPointerException e) {
//                        Log.e("SoundHandler", "Stop Tones: " + e.getMessage());
//                    }
//                }
//                tones.clear();
//            }
//        }
//        tones = new ArrayList<>();
//        if (tones != null) {
//            for (int i = 0; i < tones.size(); i++) {
//                try {
//                        tones.get(i).flush();
//                        try {
//                            tones.get(i).stop();
//                        } catch (IllegalStateException e) {
//                        }
//                        tones.get(i).release();
//                    } catch (IllegalStateException | ArrayIndexOutOfBoundsException | NullPointerException e) {
//                        Log.e("SoundHandler", "Stop Tones: " + e.getMessage());
//                    }
//            }
//            tones.clear();
//        }
        Log.d("StopSoundAll", "Stop Request Sent");
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
        track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack track) {
                try {
//                    synchronized (tones) {
//                        if (tones != null) {
////                            tones.remove(track);
//                            tones = new HashSet<>();
//                        }
//                    }
                    track.flush();
                    track.stop();
                    track.release();
                } catch (IllegalStateException | ArrayIndexOutOfBoundsException | NullPointerException e) {
                    Log.e("SoundHandler", "Stop Tone: " + e.getMessage());
                }
            }

            @Override
            public void onPeriodicNotification(AudioTrack track) {
                // nothing to do
            }
        });
        track.write(samples, 0, count);
        if (track.getPlayState() == AudioTrack.STATE_INITIALIZED) {
            try {
                track.play();
//                synchronized (tones) {
//                    tones.add(track);
//                }
            } catch (IllegalStateException e) {
//                synchronized (tones) {
//                    if (tones != null) {
////                        tones.remove(track);
//                        tones = new HashSet<>();
//                    }
//                }
                track.flush();
                try {
                    track.stop();
                } catch (IllegalStateException ex) {
                }
                track.release();
            }
        } else {
//            synchronized (tones) {
////                if (tones != null) {
////                    // tones.remove(track);
////                    tones = new HashSet<>();
////                }
//            }
            track.flush();
            try {
                track.stop();
            } catch (IllegalStateException e) {
            }
            track.release();
        }
        return track;
    }
}
