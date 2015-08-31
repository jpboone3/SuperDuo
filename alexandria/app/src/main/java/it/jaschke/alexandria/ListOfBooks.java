package com.example.administrator.spotify2;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.MediaController;

import java.io.IOException;

/*
 * this service habdles the media player for both
 * AudioActivity and AudioFragment.
 *
 */

public class AudioService extends Service {

    //media player
    private static MediaPlayer player = null;
    private static String currSong = " ";
    //binder
    private final IBinder musicBind = new AudioBinder();
    private MediaController mediacontroller = null;

    public void onCreate() {
        //create the service
        super.onCreate();
        //create player
        player = new MediaPlayer();
        //initialize
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        //set listeners


        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                //mp.stop();
                //mp.start();
                //if (mediacontroller != null)
                //    mediacontroller.show(0);
            }
        });
    }
    public void setMediaController(MediaController mc) {
        mediacontroller = mc;
    }
    public MediaPlayer getMediaPlayer() {
        return player;
    }

    //activity will bind to service
    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    //@Override
    public void start() {
        if (player != null)
            player.start();
    }

    public void pause() {
        if (player != null)
            player.pause();
    }

    public int getDuration() {
        if (player != null)
            return player.getDuration();
        return 0;
    }

    public int getCurrentPosition() {
        if (player != null)
            return player.getCurrentPosition();
        return 0;
    }

    public void stop() {
        if (player != null && player.isPlaying())
            player.stop();
    }

    //@Override
    public int getAudioSessionId() {
        return player.getAudioSessionId();
    }

    public void seekTo(int i) {
        if (player != null)
            player.seekTo(i);
    }

    public boolean isPlaying() {
        if (player != null)
            return player.isPlaying();
        return false;
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            if (player.isPlaying())
                player.stop();
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    //release resources when unbind
    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    //play a song
    public void playSong(String song) {
        //play
        if (player.isPlaying()) {
            if (song.equals(currSong)) {
                // this is probably due to a device rotation
                // and we do not want to start the same song over
                return;
            }
            player.stop();
        }
        player.reset();

        //set the data source
        try {
            player.setDataSource(song);
        } catch (IOException e) {
            Log.e("MUSIC SERVICE", "Error setting data source", e);
        }
        player.prepareAsync();
        currSong = song;
    }


    //binder
    public class AudioBinder extends Binder {
        AudioService getService() {
            return AudioService.this;
        }
    }
}
