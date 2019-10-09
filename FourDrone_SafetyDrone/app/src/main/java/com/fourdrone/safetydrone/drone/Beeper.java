package com.fourdrone.safetydrone.drone;


import android.content.Context;
import android.media.MediaPlayer;
public class Beeper{
    MediaPlayer player;
    public Beeper(Context context, int id){
        player = MediaPlayer.create(context, id);
    }
    public void play(){
        player.seekTo(0);
        player.start();
    }
}