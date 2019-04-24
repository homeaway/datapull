package com.homeaway.utils;

public class Wait {

    public static void inMinutes(int minutes){
        try {
            Thread.sleep(minutes*60*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void inSeconds(int seconds){
        try {
            Thread.sleep(seconds*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
