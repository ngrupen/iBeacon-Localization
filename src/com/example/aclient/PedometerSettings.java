package com.example.aclient;

import android.content.SharedPreferences;

import com.example.aclient.Utils;

/**
 * Wrapper for {@link SharedPreferences}, handles preferences-related tasks.
 * @author Levente Bagi
 */
public class PedometerSettings {
 
    SharedPreferences mSettings;
     
    public static int M_NONE = 1;
    public static int M_PACE = 2;
    public static int M_SPEED = 3;
     
    public PedometerSettings(SharedPreferences settings) {
        mSettings = settings;
    }
     
    public boolean isMetric() {
        return mSettings.getString("units", "imperial").equals("metric");
    }
     
    public float getStepLength() {
        try {
            return Float.valueOf(mSettings.getString("step_length", "20").trim());
        }
        catch (NumberFormatException e) {
            // TODO: reset value, & notify user somehow
            return 0f;
        }
    }
     
    public String getServerIP(){
        return mSettings.getString("serverip", "192.168.1.1");
    }
     
    public String getServerPort(){
        return mSettings.getString("serverport", "22050");
    }
     
    public String getUsername(){
        return mSettings.getString("username", "unknown");
    }
 
 
    public boolean isRunning() {
        return mSettings.getString("exercise_type", "running").equals("running");
    }
 
     
    public boolean wakeAggressively() {
        return mSettings.getString("operation_level", "run_in_background").equals("wake_up");
    }
    public boolean keepScreenOn() {
        return mSettings.getString("operation_level", "run_in_background").equals("keep_screen_on");
    }
     
    //
    // Internal
     
    public void saveServiceRunningWithTimestamp(boolean running) {
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean("service_running", running);
        editor.putLong("last_seen", Utils.currentTimeInMillis());
        editor.commit();
    }
     
    public void saveServiceRunningWithNullTimestamp(boolean running) {
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean("service_running", running);
        editor.putLong("last_seen", 0);
        editor.commit();
    }
 
    public void clearServiceRunning() {
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean("service_running", false);
        editor.putLong("last_seen", 0);
        editor.commit();
    }
 
    public boolean isServiceRunning() {
        return mSettings.getBoolean("service_running", false);
    }
     
    public boolean isNewStart() {
        // activity last paused more than 10 minutes ago
        return mSettings.getLong("last_seen", 0) < Utils.currentTimeInMillis() - 1000*60*10;
    }
 
}