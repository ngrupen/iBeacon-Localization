package com.example.aclient;

import java.util.ArrayList;


import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

 
/**
 * This is an example of implementing an application service that runs locally
 * in the same process as the application.  The {@link StepServiceController}
 * and {@link StepServiceBinding} classes show how to interact with the
 * service.
 *
 * <p>Notice the use of the {@link NotificationManager} when interesting things
 * happen in the service.  This is generally how background services should
 * interact with the user, rather than doing something more disruptive such as
 * calling startActivity().
 */
public class StepService extends Service implements SensorEventListener {
	//******************************************************************************
	//Basic variables for StepService
    private static final String TAG = "StepService";
    private StepDisplayer mStepDisplayer;
    private TurnNotifier mTurnNotifier;
    private OrientationNotifier mOrientNotifier;

    private int mSteps;
    private int mAngle;
    private int mOrient;
    
    private SharedPreferences mSettings;
    private PedometerSettings mPedometerSettings;
    private SharedPreferences mState;
    private SharedPreferences.Editor mStateEditor;
    private Utils mUtils;
    //Sensor Management
    private SensorManager mSensorManager;
    private Sensor mAccelerometer, mGyroscope;
    //********************************************************************************
    //Variables for Step Detection
    private ArrayList<StepListener> stepListeners = new ArrayList<StepListener>();
    private float mLimit;
    private float mLastValues[];
    private float mScale[];
    private float mYOffset;
    private float mLastDirections[];
    private float mLastExtremes[][] = { new float[3*2], new float[3*2] };
    private float mLastDiff[];
    private int   mLastMatch;
    int h;
     
    //back off timer to reduce false positives
    private int timerBackoff = 0;
    //********************************************************************************
    //Variables for Orientation Detection
    private ArrayList<OrientationListener> orientationListeners = new ArrayList<OrientationListener>();
    private final static int BUFFER_LEN = 64;
    // Ring Buffer
    private static double accBuffer[][] = new double[3][BUFFER_LEN];
    private static int orientationBuffHead = 0;
    private static int orientationBuffTail = 0;
    private static int orientationBuffNumVals = 0;
    // Current values
    private static double accCurrentValue[] = {0, 0, 0};
    private final static int ORIENTATION_BACKOFF_DELAY = 64; // samples
    private static int orientationBackoffTimer = 0;
    // filtering variance
    private final double filter_alpha = 0.5;
    private double var_last = 0.0;
    // orientation
    private int orient = 0;
    //********************************************************************************
    //Variables for Turn Detection
    private ArrayList<TurnListener> turnListeners = new ArrayList<TurnListener>();
    // Ring Buffer
    private static float gyroBuffer[] = new float[BUFFER_LEN];
    private static int turnBuffHead = 0;
    private static int turnBuffTail = 0;
    private static int turnBuffNumVals = 0;
    private static float gyroCurrentValue[] = {0, 0, 0};
    private final static float TURN_MINIMUM = 1; // degrees
    private final static int TURN_BACKOFF = 64; // samples
    private static int turnBackoffTimer = 0;
    
    
    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class StepBinder extends Binder {
        StepService getService() {
            return StepService.this;
        }
    }
     
    @Override
    public void onCreate() {
        Log.i(TAG, "[SERVICE] onCreate");
        super.onCreate();
         
        // Load settings
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        mPedometerSettings = new PedometerSettings(mSettings);
        mState = getSharedPreferences("state", 0);
        
        mUtils = Utils.getInstance();
        mUtils.setService(this);
        
        //Initialize step detection variables
        mLimit = 10;
        mLastValues = new float[3*2];
        mScale = new float[2];
        mLastDirections = new float[3*2];
        mLastDiff = new float[3*2];
        mLastMatch = -1;
        h = 480; 
        mYOffset = h * 0.5f;
        mScale[0] = - (h * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));
        mScale[1] = - (h * 0.5f * (1.0f / (SensorManager.MAGNETIC_FIELD_EARTH_MAX)));
        
        mStepDisplayer = new StepDisplayer(mPedometerSettings, mUtils);
        mStepDisplayer.setSteps(mSteps = 0);
        mStepDisplayer.addListener(mStepListener);
        addStepListener(mStepDisplayer);
 
        mTurnNotifier = new TurnNotifier();
        mTurnNotifier.addListener(mAngleListener);
        addTurnListener(mTurnNotifier);
         
        mOrientNotifier = new OrientationNotifier();
        mOrientNotifier.addListener(mOrientListener);
        addOrientationListener(mOrientNotifier);
 
        reloadSettings();
        
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        registerListeners();
        
        // Tell the user we started.
        Toast.makeText(this, getText(R.string.started), Toast.LENGTH_SHORT).show();
    }
     
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "[SERVICE] onStartCommand");
        Toast toast = Toast.makeText(this, "Started!", Toast.LENGTH_LONG);
        toast.show();
        return super.onStartCommand(intent,flags,startId);

    }
 
    @Override
    public void onDestroy() {
        Log.i(TAG, "[SERVICE] onDestroy");
        
        mStateEditor = mState.edit();
        mStateEditor.putInt("steps", mSteps);
        mStateEditor.putInt("angle", mAngle);
        mStateEditor.commit();

        unregisterListeners();
         
        super.onDestroy();
        
        // Tell the user we stopped.
        Toast.makeText(this, getText(R.string.stopped), Toast.LENGTH_SHORT).show();
    }
 
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "[SERVICE] onBind");
        return mBinder;
    }
 
    /**
     * Receives messages from activity.
     */
    private final IBinder mBinder = new StepBinder();
 
    public interface ICallback {
        public void stepsChanged(int value);
        public void angleChanged(int value);
        public void orientChanged(int value);
    }
     
    private ICallback mCallback;
 
    public void registerCallback(ICallback cb) {
        mCallback = cb;
        //mStepDisplayer.passValue();
        //mPaceListener.passValue();
    }
    
    public void reloadSettings() {
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
         
        setSensitivity(Float.valueOf(mSettings.getString("sensitivity", "10")));
         
        if (mStepDisplayer != null) mStepDisplayer.reloadSettings();
 
    }
     
    public void resetValues() {
        mStepDisplayer.setSteps(0);
    }
    
    //**********************************************************************************************
    //Methods to register and unregister sensor listeners
    private void registerListeners() {
        // step detector & orientation detector
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager.registerListener(this,mAccelerometer,SensorManager.SENSOR_DELAY_FASTEST);  
        mSensorManager.registerListener(this,mGyroscope,SensorManager.SENSOR_DELAY_GAME);
    }
 
    private void unregisterListeners() {
        mSensorManager.unregisterListener(this);
    }
     
    //Listener interactions:
    //Handles updates from step, orientation, and turn detectors
    private StepDisplayer.Listener mStepListener = new StepDisplayer.Listener() {
        public void onStepsChanged(int value) {
            mSteps = value;
            passValue();
        }
        
        public void passValue() {
            if (mCallback != null) {
                mCallback.stepsChanged(mSteps);
            }
        }
    };
     
    private OrientationNotifier.Listener mOrientListener = new OrientationNotifier.Listener(){
        @Override
        public void onOrientationChange(int value) {
            mOrient = value;
            // update stepDetector
            changeOrientation(value);
            passValue();
        }
 
        @Override
        public void passValue() {
            if(mCallback != null){
                mCallback.orientChanged(mOrient);
            }
        }   
    };
 
     
    private TurnNotifier.Listener mAngleListener = new TurnNotifier.Listener() {
        public void angleChanged(int value){
        	mAngle = value;
            passValue();
        }
        public void passValue() {
            Log.i("TurnDetector","mAngleListener");
            if(mCallback != null){
            	Log.i("TurnDetector", "mAngle: " + mAngle);
                mCallback.angleChanged(mAngle);
            }
        }
    };
    
    //Handles sensor events, detects Steps, Turns, and Orientation
    public void onSensorChanged(SensorEvent event) {
        //determine what type of data we got and parse it here.
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER )
        {
        	detectSteps(event);
        	detectOrientation(event);
        }
        if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE)
        {
        	detectTurns(event);
        }
        	
    }

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //Auto-generated method stub
    }
    
    //**********************************************************************************
    //Step Detection Methods
    public void detectSteps(SensorEvent event) {
        float vSum = 0;
        for (int i=0 ; i<3 ; i++) {
            final float v = mYOffset + event.values[i] * mScale[1];
            vSum += v;
        }
        int k = 0;
        float v = vSum / 3;

        float direction = (v > mLastValues[k] ? 1 : (v < mLastValues[k] ? -1 : 0));
        if (direction == - mLastDirections[k]) {
            // Direction changed
            int extType = (direction > 0 ? 0 : 1); // minumum or maximum?
                    mLastExtremes[extType][k] = mLastValues[k];
            float diff = Math.abs(mLastExtremes[extType][k] - mLastExtremes[1 - extType][k]);

            if (diff > mLimit) {

                boolean isAlmostAsLargeAsPrevious = diff > (mLastDiff[k]*2/3);
                boolean isPreviousLargeEnough = mLastDiff[k] > (diff/3);
                boolean isNotContra = (mLastMatch != 1 - extType);
                boolean notInBackoff = (timerBackoff <= 0);

                if (isAlmostAsLargeAsPrevious && isPreviousLargeEnough && isNotContra && notInBackoff) {
                    Log.i(TAG, "step");
                    for (StepListener stepListener : stepListeners) {
                        stepListener.onStep();
                    }
                    mLastMatch = extType;
                }
                else {
                    mLastMatch = -1;
                }
            }
            mLastDiff[k] = diff;
        }
        mLastDirections[k] = direction;
        mLastValues[k] = v; 	
    }
    
    public void changeOrientation(int orient){
        final int ORIENT_DFT = 0;
        final int ORIENT_OFF = 1;
        final int ORIENT_HND = 2;
        final int ORIENT_BDY = 3;
         
        switch(orient){
            case ORIENT_DFT:
            case ORIENT_OFF:
                setSensitivity(50);
                break;
            case ORIENT_HND:
                setSensitivity(2);
                break;
            case ORIENT_BDY:
                setSensitivity(9);
                break;
            default:
                setSensitivity(50);
        }
 
    }
    
    public void setSensitivity(float sensitivity) {
        mLimit = sensitivity; // 1.97  2.96  4.44  6.66  10.00  15.00  22.50  33.75  50.62
    }        
    
    public void addStepListener(StepListener sl) {
        stepListeners.add(sl);
    }
    
    //*************************************************************************************************
    //Orientation Detection Methods
    public void detectOrientation(SensorEvent event) {
        // grab current data and throw it into an array
        accCurrentValue[0] = event.values[0];
        accCurrentValue[1] = event.values[1];
        accCurrentValue[2] = event.values[2];
         
        addToOrientationBuffer(accCurrentValue);
         
        // only try to calculate orientation periodically
        if(orientationBackoffTimer <= 0 ){
            // ========== get windowed variance =========
            double varX = Statistics.getVariance(accBuffer[0]);
            double varY = Statistics.getVariance(accBuffer[1]);
            double varZ = Statistics.getVariance(accBuffer[2]);
             
            double totalVar = varX + varY + varZ;
            // filter
            double var_new = (filter_alpha)*var_last + (1-filter_alpha)*totalVar;
            var_last = var_new;
                                     
            // ========== classify position =========
            if( var_new < 0.20)
            {
                orient = 1;
            }
            else if( var_new < 3)
            {
                orient = 2;
            }
            else
            {
                orient = 3;
            }
             
            //notify listeners
            for (OrientationListener lstnr : orientationListeners) {
                lstnr.onChange((int)orient);
            }

            //backoff
            orientationBackoffTimer = ORIENTATION_BACKOFF_DELAY;
        }else{
            orientationBackoffTimer--;
        } 	
    }
    
    // Ring buffer subroutines
    public void addToOrientationBuffer(double[] vals){
        accBuffer[0][orientationBuffHead] = vals[0];
        accBuffer[1][orientationBuffHead] = vals[1];
        accBuffer[2][orientationBuffHead] = vals[2];
                     
        if(orientationBuffNumVals < BUFFER_LEN ){
            orientationBuffNumVals++;
        }
        if(orientationBuffNumVals < BUFFER_LEN ){
            orientationBuffHead = (orientationBuffHead + 1)%BUFFER_LEN;
        }else{
            orientationBuffHead = (orientationBuffHead + 1)%BUFFER_LEN;
            orientationBuffTail = (orientationBuffTail + 1)%BUFFER_LEN;
        }
    }
    
    public void addOrientationListener(OrientationListener sl) {
        orientationListeners.add(sl);
    }
    
    //**************************************************************************************
    //Turn Detection Methods
    public void detectTurns(SensorEvent event) {
        //Log.i("TurnDetector","onSensorChanged");
        // grab current data and throw it into an array
        gyroCurrentValue[0] = event.values[0];
        gyroCurrentValue[1] = event.values[1];
        gyroCurrentValue[2] = event.values[2];
        
         
        // add the Z-axis gyro component to the buffer
        addToTurnBuffer(gyroCurrentValue[2]);
                         
        // calculate the current cumulative sum
        float bufferSum = getBufferSum();
         
        // only try to calculate turns if we're not in backoff
        if(turnBackoffTimer <= 0 ){
            //Log.i("TurnDetector","Here" + bufferSum);
            float estimatedTurn = bufferSum*1.1f; // fudge factor
            if( Math.abs(estimatedTurn) > TURN_MINIMUM){
                //Log.i("TurnDetector","Here" + estimatedTurn);
                // Turn detected--notify our listeners!
                for (TurnListener turnListener : turnListeners) {
                    turnListener.onTurn((int)estimatedTurn);
                }
                // backoff
                turnBackoffTimer = TURN_BACKOFF;           
            }
             
        }else{
            turnBackoffTimer--;
            if(turnBackoffTimer == 0){
                for (TurnListener turnListener : turnListeners) {
                    turnListener.onTurn(0);
                }
            }
        }
    }
    
    // Ring buffer subroutines
    public void addToTurnBuffer(float val){
        gyroBuffer[turnBuffHead] = val;
        if(turnBuffNumVals < BUFFER_LEN ){
            turnBuffNumVals++;
        }
        if(turnBuffNumVals < BUFFER_LEN ){
            turnBuffHead = (turnBuffHead + 1)%BUFFER_LEN;
        }else{
            turnBuffHead = (turnBuffHead + 1)%BUFFER_LEN;
            turnBuffTail = (turnBuffTail + 1)%BUFFER_LEN;
        }
    }
    
    public float getBufferSum(){
        float sum = 0;
        for( int i=turnBuffTail; i!=turnBuffHead; i=(i+1)%BUFFER_LEN ){
            sum += gyroBuffer[i];
            //Log.i("TurnDetector","" + gyroBuffer[i]);
        }
        //Log.i("TurnDetector","Final sum: " + sum);
        return sum;
    }
    
    public void addTurnListener(TurnListener sl) {
        turnListeners.add(sl);
    }
}
