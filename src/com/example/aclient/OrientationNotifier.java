package com.example.aclient;

import java.util.ArrayList;

public class OrientationNotifier implements OrientationListener{
    private int currentOrientation;
     
    public interface Listener {
        public void onOrientationChange(int value);
        public void passValue();
    }
     
    private ArrayList<Listener> mListeners = new ArrayList<Listener>();
 
    public void addListener(Listener l) {
        mListeners.add(l);
    }
     
    public void notifyListener() {
        for (Listener listener : mListeners) {
            listener.onOrientationChange(currentOrientation);
        }
    }
 
    @Override
    public void onChange(int newOrient) {
        currentOrientation = newOrient;
        notifyListener();
    }
 
    @Override
    public void passValue() {}
     
}

