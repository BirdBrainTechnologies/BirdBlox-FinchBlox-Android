package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.MainWebView;
import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.FileManagementHandler.getBirdblocksDir;

/**
 * Handler for handling recording and playback of (recorded) sounds,
 * as well as managing these recorded sound files.
 *
 * @author Shreyan Bakshi (AppyFizz)
 */

public class RecordingHandler implements RequestHandler {
    HttpService service;

    private static MediaRecorder mediaRecorder;

    // Directory where recordings are to be stored, as a String
    private static String recordedFilesDir;
    // Directory where recordings are to be stored, as a 'File'
    private static File recordDir;

    public RecordingHandler(HttpService service) {
        this.service = service;
        recordedFilesDir = getBirdblocksDir().getAbsolutePath() + "/Recordings";
        recordDir = new File(recordedFilesDir);
        if(!recordDir.exists()) {
            try {
                recordDir.mkdirs();
            } catch (SecurityException e) {
                Log.e("RecordingHandler", "Recordings' Directory: " + e.getMessage());
            }
        }
        mediaRecorder = new MediaRecorder();
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        String responseBody = "";
        switch (path[0]) {
            case "check":
                responseBody = String.valueOf(checkMic());
                break;
            case "start":
                responseBody = recordAudio(m.get("filename").get(0));
                break;
            case "stop":
                responseBody = stopRecording();
                break;
            case "play":
                responseBody = playAudio(m.get("filename").get(0));
                break;
            case "list":
                responseBody = listRecordings();
                break;
            default:
                break;
        }
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }

    /**
     * Checks if device has a microphone.
     *
     * @return Returns true if device has a mic, false otherwise.
     */
    private boolean checkMic() {
        return MainWebView.deviceHasMicrophone;
    }

    /**
     * Starts audio recording.
     *
     * @param filename Filename of the recording.
     *                 Should be of type "____.m4a"
     *
     * @return  "No Mic" if device doesn't have a microphone,
     *          "Started" if recording successfully begun,
     *          "Error" otherwise.
     */
    private String recordAudio(String filename) {
        if (checkMic()) {
            try {
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setOutputFile(recordedFilesDir + "/" + filename);
                mediaRecorder.prepare();
                mediaRecorder.start();
                return "Started";
            } catch (IllegalStateException | IOException e) {
                Log.e("RecordingHandler", "Start Recording: " + e.getMessage());
                return "Error";
            }
        }
        return "No Mic";
    }

    /**
     *  Stops recording.
     *
     * @return Returns the String "Stopped".
     */
    private String stopRecording() {
        if (mediaRecorder != null)
        {
            mediaRecorder.stop();
            mediaRecorder.reset();
        }
        return "Stopped";
    }

    /**
     * Plays the requested (audio) recording.
     *
     * @param filename Filename of the recording to be played.
     *                 Should be of type "____.m4a"
     * @return         "Playing" if successful, "Error" otherwise.
     */
    private synchronized String playAudio(String filename) {
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(recordedFilesDir + "/" + filename);
            mediaPlayer.prepare();
            mediaPlayer.start();
            return "Playing";
        } catch (IOException e) {
            Log.e("RecordingHandler", "Playing Audio: " + e.getMessage());
            return "Error";
        }
    }

    /**
     * Lists the recorded sounds on the device
     *
     * @return List of recorded sounds stored on the device separated by \n
     */
    private String listRecordings() {
        File[] files = recordDir.listFiles();
        String response = "";
        if (files == null) {
            return response;
        }
        for (int i = 0; i < files.length; i++) {
            response += files[i].getName();
            if (i < files.length - 1) response += "\n";
        }
        return response;
    }

}
