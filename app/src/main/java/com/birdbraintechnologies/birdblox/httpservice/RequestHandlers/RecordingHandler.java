package com.birdbraintechnologies.birdblox.httpservice.RequestHandlers;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import com.birdbraintechnologies.birdblox.MainWebView;
//import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidResponse;
import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidSession;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandler;
import com.birdbraintechnologies.birdblox.httpservice.Status;
import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.CURRENT_PREFS_KEY;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.filesPrefs;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.getBirdbloxDir;

/**
 * Handler for handling recording and playback of (recorded) sounds,
 * as well as managing these recorded sound files.
 *
 * @author Shreyan Bakshi (AppyFizz)
 */

public class RecordingHandler implements RequestHandler {
    private final String TAG = this.getClass().getSimpleName();

    private static final long RECORD_MAX_IN_MILLIS = 300000;
    private static final long RECORD_EXTRA_IN_MILLIS = 500;

    private static MediaRecorder mediaRecorder;
    private static MediaPlayer mediaPlayer;

    // Directory where temporary recorded 'chunks' are to be stored, as a String
    private static String tempRecordedFilesDir;
    // Directory where recorded 'chunks' are to be stored, as a 'File'
    private static File tempRecordDir;
    // Directory where final recordings are to be stored, as a String
    private static String recordedFilesDir;
    // Directory where final recordings are to be stored, as a 'File'
    private static File recordDir;
    // Name of the file currently being recorded
    // null if no file is currently being recorded
    private static String currFilename;
    // Name of the latest chunk that was recorded.
    // null if no file is currently being recorded.
    private static String tempFilename;
    // ArrayList containing the paths to each 'chunk' as Strings.
    private static ArrayList<String> sourceFiles;
    // Current state of the Media Player/Recorder
    // Takes the values "Recording", "Paused", "Stopped", "Playing"
    private static String currState = "Stopped";
    //HttpService service;

    private static Timer timer;

    private static long startTime;
    private static long lastPauseTime;
    private static long extraTime;

    private String currProj;

    private Context context;

    //public RecordingHandler(HttpService service) {
    public RecordingHandler(Context context) {
        //this.service = service;
        this.context = context;
        mediaRecorder = new MediaRecorder();
        timer = new Timer();

        // create directory for temp recording chunks
        tempRecordedFilesDir = mainWebViewContext.getFilesDir().getAbsolutePath() + "/RecordingChunks";
        tempRecordDir = new File(tempRecordedFilesDir);
        if (!tempRecordDir.exists()) {
            try {
                tempRecordDir.mkdirs();
            } catch (SecurityException e) {
                Log.e("RecordingHandler", "Recordings' Directory: " + e.getMessage());
            }
        }

        // create directory for final recordings
        currProj = filesPrefs.getString(CURRENT_PREFS_KEY, null);
        if (currProj != null) {
            recordedFilesDir = getBirdbloxDir() + "/" + currProj + "/recordings";
            recordDir = new File(recordedFilesDir);
            if (!recordDir.exists()) {
                try {
                    recordDir.mkdirs();
                } catch (SecurityException e) {
                    Log.e("RecordingHandler", "Recordings' Directory: " + e.getMessage());
                }
            }
        }
    }

    public RecordingHandler() {
        if (mediaRecorder == null)
            mediaRecorder = new MediaRecorder();
        if (timer == null)
            timer = new Timer();

        // create directory for temp recording chunks
        tempRecordedFilesDir = mainWebViewContext.getFilesDir().getAbsolutePath() + "/RecordingChunks";
        tempRecordDir = new File(tempRecordedFilesDir);
        if (!tempRecordDir.exists()) {
            try {
                tempRecordDir.mkdirs();
            } catch (SecurityException e) {
                Log.e("RecordingHandler", "Recordings' Directory: " + e.getMessage());
            }
        }
        
        // create directory for final recordings
        if (filesPrefs != null) {
            currProj = filesPrefs.getString(CURRENT_PREFS_KEY, null);
        }
        if (currProj != null) {
            recordedFilesDir = getBirdbloxDir() + "/" + currProj + "/recordings";
            recordDir = new File(recordedFilesDir);
            if (!recordDir.exists()) {
                try {
                    recordDir.mkdirs();
                } catch (SecurityException e) {
                    Log.e("RecordingHandler", "Recordings' Directory: " + e.getMessage());
                }
            }
        }
    }

    @Override
    //public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
    public NativeAndroidResponse handleRequest(NativeAndroidSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        String responseBody = "";
        switch (path[0]) {
            case "start":
                return startRecording();
            case "stop":
                responseBody = stopRecording();
                break;
            case "discard":
                responseBody = discardRecording();
                break;
            case "pause":
                responseBody = pauseRecording();
                break;
            case "unpause":
                responseBody = resumeRecording();
                break;
            default:
                break;
        }
        //NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
        //        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, responseBody);
        NativeAndroidResponse r = new NativeAndroidResponse(Status.OK, responseBody);
        return r;
    }

    /**
     * Checks if user has provided permissions tp access mic.
     *
     * @return Returns true if user has provided these permissions, and false otherwise.
     */
    private boolean checkMicPermission() {
        //return (ActivityCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
        return (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Starts audio recording.
     *
     * @return Filename of the recording if success.
     * Returns null in case of error.
     */
    //private NanoHTTPD.Response startRecording() {
    private NativeAndroidResponse startRecording() {
        //PackageManager packageManager = service.getPackageManager();
        PackageManager packageManager = context.getPackageManager();
        boolean microphone = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
        if (!microphone) {
            //return NanoHTTPD.newFixedLengthResponse(
            //        NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Microphone not detected");
            return new NativeAndroidResponse(Status.SERVICE_UNAVAILABLE, "Microphone not detected");
        } else if (checkMicPermission()) {
            currState = "Recording";
            try {
                Calendar c = Calendar.getInstance();
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String filename = df.format(c.getTime());
                if (sourceFiles == null) {
                    sourceFiles = new ArrayList<>();
                    clearTimerTasks();
                    startTime = System.currentTimeMillis();
                    extraTime = 0;
                    initializeRecordTimer(RECORD_MAX_IN_MILLIS);
                }
                tempFilename = FileManagementHandler.findAvailableName(tempRecordDir, filename, ".m4a");
                sourceFiles.add(tempRecordedFilesDir + "/" + tempFilename + ".m4a");
                if (sourceFiles.size() == 1)
                    currFilename = tempFilename;
                if (mediaRecorder == null) {
                    mediaRecorder = new MediaRecorder();
                }
                mediaRecorder.reset();
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setOutputFile(tempRecordedFilesDir + "/" + tempFilename + ".m4a");
                mediaRecorder.prepare();
                mediaRecorder.start();
                Log.d("RecordingHandler", "Started Recording");
                //return NanoHTTPD.newFixedLengthResponse(
                //        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Started");
                return new NativeAndroidResponse(Status.OK, "Started");
            } catch (SecurityException | IllegalStateException | IOException | NullPointerException e) {
                // Stop recording and save here.
                Log.e("RecordingHandler", "Start Recording: " + e.getMessage());
                //return NanoHTTPD.newFixedLengthResponse(
                //        NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Could not start recording.");
                return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Could not start recording.");
            }
        } else {
            Intent getMicPerm = new Intent(MainWebView.MICROPHONE_PERMISSION);
            //LocalBroadcastManager.getInstance(service).sendBroadcast(getMicPerm);
            LocalBroadcastManager.getInstance(context).sendBroadcast(getMicPerm);
            //return NanoHTTPD.newFixedLengthResponse(
            //        NanoHTTPD.Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Microphone permission disabled");
            return new NativeAndroidResponse(Status.UNAUTHORIZED, "Microphone permission disabled");
        }
    }

    /**
     * Pauses recording.
     *
     * @return Returns the String "Paused" if success.
     * Returns null in case of error.
     */
    String pauseRecording() {
        try {
            if (currState.equals("Recording") && mediaRecorder != null) {
                lastPauseTime = System.currentTimeMillis();
                clearTimerTasks();
                final MediaRecorder mediaRecorder2 = mediaRecorder;
                mediaRecorder = new MediaRecorder();
                mediaRecorder.reset();
                new Handler(mainWebViewContext.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        /** Run after {@link RECORD_EXTRA_IN_MILLIS} ms */
                        if (mediaRecorder2 != null) {
                            mediaRecorder2.stop();
                            mediaRecorder2.reset();
                        }
                    }
                }, RECORD_EXTRA_IN_MILLIS);
            }
            if (currState.equals("Recording"))
                currState = "Paused";
            return "Paused";
        } catch (SecurityException | IllegalStateException | NullPointerException e) {
            Log.e("RecordingHandler", "Pause Recording: " + e.getMessage());
        }
        return null;
    }

    /**
     * Resumes Recording.
     *
     * @return Returns the String "Resumed" if success.
     * Returns null in case of error.
     */
    String resumeRecording() {
        if (currState.equals("Paused"))
            currState = "Recording";
        extraTime += (System.currentTimeMillis() - lastPauseTime);
        long elapsedTime = System.currentTimeMillis() - startTime - extraTime;
        long remainingTime = RECORD_MAX_IN_MILLIS - elapsedTime;
        initializeRecordTimer(remainingTime);
        return startRecording() == null ? null : "Resumed";
    }

    /**
     * Stops Recording.
     *
     * @return Returns the String "Stopped" if success.
     * Returns null in case of error.
     */
    public String stopRecording() {
        try {
            clearTimerTasks();
            extraTime = 0;
            final String currState2 = currState;
            final MediaRecorder mediaRecorder2 = mediaRecorder;
            mediaRecorder = new MediaRecorder();
            mediaRecorder.reset();
            final ArrayList<String> sourceFiles2 = sourceFiles;
            final String currFilename2 = currFilename;
            final File tempRecordDir2 = tempRecordDir;
            new Handler(mainWebViewContext.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    /** Run after {@link RECORD_EXTRA_IN_MILLIS} ms */
                    if (currState2.equals("Recording") && mediaRecorder2 != null) {
                        mediaRecorder2.stop();
                        mediaRecorder2.reset();
                    }
                    new Thread() {
                        @Override
                        public void run() {
                            super.run();
                            if (sourceFiles2 != null) {
                                mergeMediaFiles(sourceFiles2, currFilename2);
                            }
                            for (File f : tempRecordDir2.listFiles()) {
                                f.delete();
                            }
                        }
                    }.start();
                }
            }, RECORD_EXTRA_IN_MILLIS);
            sourceFiles = null;
            tempFilename = null;
            currFilename = null;
            if (currState.equals("Recording") || currState.equals("Paused"))
                currState = "Stopped";
            return "Stopped";
        } catch (SecurityException | IllegalStateException | NullPointerException e) {
            Log.e("RecordingHandler", "Stop Recording: " + e.getMessage());
        }
        return null;
    }

    /**
     * Stops recording, and discards the last recording.
     *
     * @return Returns the String "Discarded" if success.
     * Returns null in case of error.
     */
    private String discardRecording() {
        try {
            clearTimerTasks();
            extraTime = 0;
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.reset();
            }
            for (File f : tempRecordDir.listFiles()) {
                f.delete();
            }
            tempFilename = null;
            currFilename = null;
            sourceFiles = null;
            if (currState.equals("Recording") || currState.equals("Paused"))
                currState = "Stopped";
            return "Discarded";
        } catch (SecurityException | IllegalStateException | NullPointerException e) {
            Log.e("RecordingHandler", "Discard Recording: " + e.getMessage());
        }
        return null;
    }

    /**
     * Plays the requested (audio) recording.
     *
     * @param filename Filename of the recording to be played.
     * @return "Playing" if successful, "Error" otherwise.
     */
    synchronized String playAudio(String filename) {
        try {
            if (currProj != null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.setDataSource(recordedFilesDir + "/" + filename + ".m4a");
                mediaPlayer.prepare();
                mediaPlayer.start();
            }
            return "Playing";
        } catch (IOException | IllegalStateException e) {
            Log.e("RecordingHandler", "Playing Audio: " + e.getMessage());
            return "Error";
        }
    }

    /**
     * Stops playing the currently playing (audio) recording, if any.
     *
     * @return "StoppedPlayback" if successful, "Error" otherwise.
     */
    String stopPlayback() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.release();
            }
            return "StoppedPlayback";
        } catch (IllegalStateException | SecurityException | NullPointerException e) {
            Log.e("RecordingHandler", "Stopping Playback: " + e.getMessage());
        }
        return "Error";
    }

    /**
     * Gets the duration of a given recording.
     *
     * @param filename Filename of the recording in question.
     * @return Duration in milliseconds of the requested sound, if success.
     * Returns "0" otherwise.
     */
    synchronized String getDuration(String filename) {
        try {
            if (currProj != null) {
                MediaPlayer mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.setDataSource(recordedFilesDir + "/" + filename + ".m4a");
                mediaPlayer.prepare();
                String response = Integer.toString(mediaPlayer.getDuration());
                mediaPlayer.reset();
                mediaPlayer.release();
                return response;
            }
        } catch (IOException | IllegalStateException e) {
            Log.e("RecordingHandler", "Playing Audio: " + e.getMessage());
        }
        return "0";
    }

    /**
     * Lists the recorded sounds on the device
     *
     * @return List of recorded sounds stored in the current project separated by \n, on success
     * Returns empty string otherwise
     */
    String listRecordings() {
        try {
            String response = "";
            if (recordDir == null) {
                return response;
            }
            File[] files = recordDir.listFiles();
            if (files == null) {
                return response;
            }
            for (int i = 0; i < files.length; i++) {
                response += files[i].getName().substring(0, files[i].getName().length() - 4);
                if (i < files.length - 1) response += "\n";
            }
            return response;
        } catch (IllegalStateException | NullPointerException | SecurityException e) {
            Log.e("RecordingHandler", e.getMessage());
        }
        return "";
    }

    private void initializeRecordTimer(final long duration) {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runJavascript("CallbackManager.sounds.recordingEnded();");
                stopRecording();
            }
        }, duration);
    }

    private void clearTimerTasks() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
    }

    /**
     * Get the list of chunks for the current recording if any.
     *
     * @return ArrayList<String> containing the respective chunks in order, if any.
     * null otherwise.
     */
    public ArrayList<String> getCurrentChunks() {
        return sourceFiles;
    }

    /**
     * Sets the list of chunks for the current recording, if any,
     * based on the files present in tempRecordDir.
     */
    public void setCurrentChunks() {
        sourceFiles = new ArrayList<>();
        for (File file : tempRecordDir.listFiles()) {
            sourceFiles.add(file.getAbsolutePath());
        }
    }

    /**
     * Method to merge media files together.
     * <p>
     * SOURCE: https://stackoverflow.com/questions/16435945/how-can-i-pause-voice-recording-in-android
     * Modified slightly to suit our needs.
     *
     * @return true if success, false otherwise.
     */
    private boolean mergeMediaFiles(ArrayList<String> sourceFiles2, String currFilename2) {
        try {
            if (sourceFiles2 != null && sourceFiles2.size() > 0 && recordDir.exists()) {
                String targetFile = recordedFilesDir + "/" + currFilename2 + ".m4a";
                String mediaKey = "soun";
                List<Movie> listMovies = new ArrayList<>();
                for (String filename : sourceFiles2) {
                    listMovies.add(MovieCreator.build(filename));
                }
                List<Track> listTracks = new LinkedList<>();
                for (Movie movie : listMovies) {
                    for (Track track : movie.getTracks()) {
                        if (track.getHandler().equals(mediaKey)) {
                            listTracks.add(track);
                        }
                    }
                }
                Movie outputMovie = new Movie();
                if (!listTracks.isEmpty()) {
                    outputMovie.addTrack(new AppendTrack(listTracks.toArray(new Track[listTracks.size()])));
                }
                Container container = new DefaultMp4Builder().build(outputMovie);
                FileChannel fileChannel = new RandomAccessFile(String.format(targetFile), "rw").getChannel();
                container.writeContainer(fileChannel);
                fileChannel.close();
                runJavascript("CallbackManager.sounds.recordingsChanged();");
            }
            return true;
        } catch (IOException e) {
            Log.e("RecordingHandler", "Merge Media Files: " + e.getMessage());
            return false;
        }
    }

}
