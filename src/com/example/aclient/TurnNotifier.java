package com.example.aclient;

import java.util.ArrayList;

public class TurnNotifier implements TurnListener{
    private int currentAngle;
     
    public interface Listener {
        public void angleChanged(int value);
        public void passValue();
    }
     
    private ArrayList<Listener> mListeners = new ArrayList<Listener>();
 
    public void addListener(Listener l) {
        mListeners.add(l);
    }
     
    public void notifyListener() {
        for (Listener listener : mListeners) {
            listener.angleChanged(currentAngle);
        }
    }
 
    @Override
    public void onTurn(int turnDegree) {
        currentAngle = turnDegree;
        notifyListener();
    }
 
    @Override
    public void passValue() {}
     
}