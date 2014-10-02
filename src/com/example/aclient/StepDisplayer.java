package com.example.aclient;

import java.util.ArrayList;

/**
 * Counts steps provided by StepDetector and passes the current
 * step count to the activity.
 */
public class StepDisplayer implements StepListener {
 
    private int mCount = 0;
    PedometerSettings mSettings;
    Utils mUtils;
 
    public StepDisplayer(PedometerSettings settings, Utils utils) {
        mUtils = utils;
        mSettings = settings;
        notifyListener();
    }
    public void setUtils(Utils utils) {
        mUtils = utils;
    }
 
    public void setSteps(int steps) {
        mCount = steps;
        notifyListener();
    }
    public void onStep() {
        mCount ++;
        notifyListener();
    }
    public void reloadSettings() {
        notifyListener();
    }
    public void passValue() {
    }
     
     
 
    //-----------------------------------------------------
    // Listener
     
    public interface Listener {
        public void onStepsChanged(int value);
        public void passValue();
    }
    private ArrayList<Listener> mListeners = new ArrayList<Listener>();
 
    public void addListener(Listener l) {
        mListeners.add(l);
    }
    public void notifyListener() {
        for (Listener listener : mListeners) {
            listener.onStepsChanged((int)mCount);
        }
    } 
}
