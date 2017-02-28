package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by tsun on 2/27/17.
 */

public class SoundHandler implements RequestHandler, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {
    private static final String TAG = SoundHandler.class.getName();

    private HttpService service;
    private MediaPlayer mediaPlayer;

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
            case "stop":
            case "stopAll":
        }


        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }

    private String listSounds() {
        AssetManager assets = service.getAssets();
        try {
            String[] sounds = assets.list("frontend/SoundClips");
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

    private synchronized String getDuration(String soundId) {
        String path = "frontend/SoundClips/%s.wav";
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


    private synchronized void playSound(String soundId) {
        String path = "frontend/SoundClips/%s.wav";
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

    @Override
    public void onCompletion(MediaPlayer mp) {
        mp.release();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();
    }
}
