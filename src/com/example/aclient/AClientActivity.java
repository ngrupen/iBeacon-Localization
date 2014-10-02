package com.example.aclient;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.example.aclient.TcpClient.OnMessageReceived;
import com.example.aclient.PathView;
import com.example.aclient.PedometerSettings;
import com.example.aclient.StepService;

import com.radiusnetworks.ibeacon.IBeacon;
import com.radiusnetworks.ibeacon.IBeaconConsumer;
import com.radiusnetworks.ibeacon.IBeaconManager;
import com.radiusnetworks.ibeacon.RangeNotifier;
import com.radiusnetworks.ibeacon.Region;

public class AClientActivity extends Activity implements OnClickListener, OnMessageReceived, IBeaconConsumer, RangeNotifier {	
	
    // main activity tag
    private static final String TAG = "AClient";
	//Number of zone classes for SVM training
	final int NUM_ZONES = 4;	
	//Lab Dimensions in meters
	final float LABWIDTHINMETERS = 9.23544f;
	final float LABHEIGHTINMETERS = 10.5156f;	
	//Used to reference UI elements of activity_aclient.xml
	private TextView text, message;
	private EditText msgBox;
	private Button sendMsg;	
	//NESL lab UUID for iBeacons
	public final String LAB_UUID = "46A7594F-672D-4B6C-81C1-785AECDBA0D5";	
	//Bitmaps for Lab View activity
	Bitmap labBitmap, canvasBitmap;
	boolean pointsPlotted;	
	//TcpClient for Server communication
	TcpClient mTcpClient;	
	//Used for ranging iBeacons
    IBeaconManager iBeaconManager;   
    //ArrayList to hold BeaconBuilder objects (iBeacons that have been ranged)
    ArrayList<BeaconBuilder> beaconList; 
    List<String> dataList;   
    //iBeacon and server connection information 
    int major, minor, rssi, totalBeacons = 0;
    String uuid, username, date, ip, port, toServer;
    //Coordinates received from server in onProgressUpdate
    float x, y; 
    //Access to global settings
    SharedPreferences sharedPref;
    //Provides updates regarding communication with server
    Toast toast;
    //For Hiding Keyboard
    InputMethodManager mgr;
    //Current Zone for Data Collection for Testing Sample and Radio Buttons for Specifying Zone
    int currentZone;
    private RadioButton radio1, radio2, radio3, radio4;
    private ToggleButton train;
    private TextView zone;
    boolean inTrainingMode = false;
    int index = 0;
    
    //For animation
    private Animation animShow, animHide;
    
    //Variables for step/turn/orientation detection
    // get step and angle text views
    private TextView mStepValueView;
    private TextView mAngleValueView;
    // dead reckoning views
    private int mStepValue;
    private int mAngleValue;
    // estimated orientation
    private int mOrientation;
    private TextView mOrientationView;
    // Set when user selected Quit from menu, can be used by onPause, onStop, onDestroy
    private boolean mQuitting = false;
    private boolean mIsRunning;
    // the main background service
    private StepService mService;
    // path canvas -- USE THIS LATER ONCE BASIC STUFF IS WORKING
    private PathView mPathView;
    private final int PATHVIEW_ID = 100;
    //pedometer settings
    PedometerSettings mPedometerSettings;
	
	private void setUpAllViews(int layout) {
		//layout serves as the reference id for activity_aclient.xml
		setContentView(layout);
		
		//TextViews and EditTexts for AClientActivity
		text = (TextView) findViewById(R.id.text);
		message = (TextView) findViewById(R.id.getMsg);
		msgBox = (EditText) findViewById(R.id.msgBox);
		sendMsg = (Button) findViewById(R.id.sendMsg);
		
		//Radio Buttons for Changing Zone
		radio1 = (RadioButton) findViewById(R.id.radio_one);
		radio2 = (RadioButton) findViewById(R.id.radio_two);
		radio3 = (RadioButton) findViewById(R.id.radio_three);
		radio4 = (RadioButton) findViewById(R.id.radio_four);
		train = (ToggleButton) findViewById(R.id.train);
		zone = (TextView) findViewById(R.id.zone);
		
		init_Popup();
	}
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	Log.i(TAG, "[ACTIVITY] onCreate");
        super.onCreate(savedInstanceState);     
        setUpAllViews(R.layout.activity_aclient);	
        
        //Setting up iBeacon
        iBeaconManager = IBeaconManager.getInstanceForApplication(this.getApplicationContext());
        iBeaconManager.bind(this);

       
        
        //Bitmap for LocationActivity
        labBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.lab_image);
        
        //Initialize pointsPlotted to false, only make true if coordinates have been received
        pointsPlotted = false;
        
        //ArrayList for holding BeaconBuilder objects and List for Data Strings
        beaconList = new ArrayList<BeaconBuilder>();
        dataList = new ArrayList<String>();
        
        //Initialize step service variables and path canvas
        mStepValue = 0;
        mAngleValue = 0;
        mOrientation = 0;
        final LinearLayout view = (LinearLayout) findViewById(R.id.canvas_row);
        mPathView = new PathView(this);
        mPathView.setId(PATHVIEW_ID);
        view.addView(mPathView);
        
        //CALL PASS CONTEXT AFTER CREATING SERVICE -- USED FOR PREFERENCES
    }
    
    /** Called when the activity is started */
    @Override
    protected void onStart() {
        Log.i(TAG, "[ACTIVITY] onStart");
        super.onStart();
    }
    
    /** Called when the activity is resumed */
    @Override
    protected void onResume() {
        Log.i(TAG, "[ACTIVITY] onResume");
        super.onResume();
         
        //For settings
        sharedPref = PreferenceManager.getDefaultSharedPreferences(getBaseContext()); 
        mPedometerSettings = new PedometerSettings(sharedPref);
         
        // Read from preferences if the service was running on the last onPause
        mIsRunning = mPedometerSettings.isServiceRunning();
         
        // Start the service if this is considered to be an application start (last onPause was long ago)
        if (!mIsRunning && mPedometerSettings.isNewStart()) {
            startStepService();
            bindStepService();
        }
        else if (mIsRunning) {
            bindStepService();
        }
         
        mPedometerSettings.clearServiceRunning();
 
        // re-hook the UI views
        mStepValueView     = (TextView) findViewById(R.id.step_value);
        mAngleValueView    = (TextView) findViewById(R.id.angle_value);
        mOrientationView   = (TextView) findViewById(R.id.orientation);
        mPathView          = (PathView) findViewById(PATHVIEW_ID);
 
    }
    
    /** Called when the activity is paused */
    @Override
    protected void onPause() {
        Log.i(TAG, "[ACTIVITY] onPause");
        if (mIsRunning) {
            unbindStepService();
        }
        if (mQuitting) {
            mPedometerSettings.saveServiceRunningWithNullTimestamp(mIsRunning);
        }
        else {
            mPedometerSettings.saveServiceRunningWithTimestamp(mIsRunning);
        }
 
        super.onPause();
    }
    
    /** Called when the activity is stopped */
    @Override
    protected void onStop() {
        Log.i(TAG, "[ACTIVITY] onStop");
        super.onStop();
    }
 
    //Unbinds IBeaconManager in process of Destroying App
    @Override
    public void onDestroy() {
    	Log.i(TAG, "[ACTIVITY] onDestroy");
        super.onDestroy();
        iBeaconManager.unBind(this);
    }
     
    /** Called when the activity is restarted */
    protected void onRestart() {
        Log.i(TAG, "[ACTIVITY] onRestart");
        super.onDestroy();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
    	MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_bar_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
        	openSettings();
            return true;
        }
        if (id == R.id.lab_view)
        {
        	openLabView();
        	return true;
        }
    	if (id == R.id.pause)
    	{
    		unbindStepService();
    		stopStepService();
    		return true;
    	}
    	if (id == R.id.resume)
    	{
    		startStepService();
    		bindStepService();
    		return true;
    	}
    	if (id == R.id.reset)
    	{
    		resetValues(true);
    		return true;
    	}
    	if (id == R.id.quit)
        {
    		resetValues(false);
    		unbindStepService();
    		stopStepService();
    		mQuitting = true;
    		finish();
    		return true;
        }   
        return super.onOptionsItemSelected(item);
    }
    
    //**************************************************************************
    //Manages send button clicks from the screen
    //**************************************************************************
    public void onClick(View v) {
    	
    	switch(v.getId()) {    	    	
    	    //Case that Send Button is Pressed 	
    		case R.id.sendMsg:
    			//Format message to server
    			toServer = "";
    			toServer += msgBox.getText().toString();   //Gets message from message box
    			
    			//toast = Toast.makeText(this, "Sent: " + toServer, Toast.LENGTH_SHORT);
    			//toast.show();
    			
    			sendToServer(toServer);
    			text.setText("Waiting for a response...");
    			msgBox.setText("");
    			
    			//Hide Keyboard
    		    mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    		    mgr.hideSoftInputFromWindow(msgBox.getWindowToken(), 0);
    			break;
    			
    		case R.id.train:
    			if(train.isChecked())
    			{
    				inTrainingMode = true;
    	    		
    				//Disable sending buttons
    				sendMsg.setEnabled(false);
    				msgBox.setEnabled(false);
    				
    				//Enable Radio Buttons
    				radio1.setEnabled(true);
    				radio2.setEnabled(true);
    				radio3.setEnabled(true);
    				radio4.setEnabled(true);
    				
    				//Show Radio Button Text View
    				zone.setVisibility(View.VISIBLE);
    			}
    			
    			if(!train.isChecked())
    			{
    				inTrainingMode = false;
    				
    				//Enable sending buttons
    				sendMsg.setEnabled(true);
    				msgBox.setEnabled(true);
    				
    				//Disable Radio Buttons
    				radio1.setEnabled(false);
    				radio2.setEnabled(false);
    				radio3.setEnabled(false);
    				radio4.setEnabled(false);
    				
    				//Hide Radio Button Text View
    				zone.setVisibility(View.INVISIBLE);
    				
    				//Send all data strings to server
    				sendCollectedData();
    			}
    			break;
    			
    	}

    }
    
    //********************************************************************
    // Manages Radio Button Clicks (Changing Zones During Testing)
    //********************************************************************
    public void onRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();
        
        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.radio_one:
                if (checked)
                {
                    //Change current zone to zone 1
                	currentZone = 1;
                	zone.setText("Your current zone is: " + currentZone);
                }
               	break;
                
            case R.id.radio_two:
                if (checked)
                {
                	//Change current zone to zone 2
                	currentZone = 2;
                	zone.setText("Your current zone is: " + currentZone);
                }
                break;
                
            case R.id.radio_three:
                if (checked)
                {
                	//Change current zone to zone 3
                	currentZone = 3;
                	zone.setText("Your current zone is: " + currentZone);
                }
                break;
                
            case R.id.radio_four:
                if (checked)
                {
                	//Change current zone to zone 4
                	currentZone = 4;
                	zone.setText("Your current zone is: " + currentZone);
                }
                break;
        }
    }
 
    //********************************************************************
    // ACTION BAR CALLS (Settings, Lab View)
    //********************************************************************
    public void openSettings() {
    	startActivity(new Intent(this, SettingsActivity.class));
    }
    
    public void openLabView() {
    	//Compress bitmap for smoother pass to LocationActivity
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	byte[] b;
    	
    	Intent intent = new Intent(this, LocationActivity.class);
    	
    	if(pointsPlotted) {
    		canvasBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
    		b = baos.toByteArray();
    		intent.putExtra("imageBitmap", b);
    	}
    	else {
    		labBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
    		b = baos.toByteArray();
    		intent.putExtra("imageBitmap", b);
    	}
    	
    	startActivity(intent);
    }
    
    //Provides animation to show Sensor Information on screen
    private void init_Popup() {
    	 
        final SlidingPanel popup = (SlidingPanel) findViewById(R.id.popup_window);
 
        // Hide the popup initially.....
        popup.setVisibility(View.GONE);
 
        animShow = AnimationUtils.loadAnimation( this, R.anim.popup_show);
        animHide = AnimationUtils.loadAnimation( this, R.anim.popup_hide);
 
        final ImageButton   showButton = (ImageButton) findViewById(R.id.show_popup_button);
        final ImageButton   hideButton = (ImageButton) findViewById(R.id.hide_popup_button);
        showButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                popup.setVisibility(View.VISIBLE);
                popup.startAnimation(animShow);
                showButton.setEnabled(false);
                hideButton.setEnabled(true);
        }});
 
        hideButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                popup.startAnimation( animHide );
                showButton.setEnabled(true);
                hideButton.setEnabled(false);
                popup.setVisibility(View.GONE);
        }});
    }

    
    
    //**************************************************************************
    // IBEACON INTERACTIONS
    //**************************************************************************
    @Override
    public void onIBeaconServiceConnect() {
    	Log.i("iBeaconRanging", "iBeacon Service connected");
        Region region = new Region("InfoActivityRanging", null, null, null);
        iBeaconManager.setRangeNotifier(this);
        try {         
            iBeaconManager.startRangingBeaconsInRegion(region);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void didRangeBeaconsInRegion(Collection<IBeacon> iBeacons, Region region) {
    	if(inTrainingMode) {
    		saveDataInstance(iBeacons);
    	}
    	
    	collectBeaconData(iBeacons);
    }
    
    //Collect iBeacon info, send info to server after every 10 collections
    public void collectBeaconData(Collection<IBeacon> iBeacons) {
		index++;
    	Log.i("iBeaconRanging", "Index = " + index);
    	
    	
    	//If 5th collection, send info to server and reset list
    	if(index == 20)
    	{
    		Log.i("DIAG", " <<<<<<<<<<<<<< TX BUNCH >>>>>>>>>>>");
    		//Create JSONArray of JSONObjects from beacons
    		JSONArray jsonArray = new JSONArray();
    		for (BeaconBuilder tempBeacon : beaconList)
    		{
    		    JSONObject tempJSONObject = tempBeacon.toJSONObject();  				
    			jsonArray.put(tempJSONObject);
    			
    			Log.i("DIAG", "minor:" + tempBeacon.minor + ", rssi:" + tempBeacon.rssi +  ", avgrssi:" + tempBeacon.avgRSSI);
    			//tempBeacon.resetRSSI();
    		}

    		toServer = jsonArray.toString();
    		sendToServer("getlocation" + toServer);
			
    		//Reset List
    		beaconList = new ArrayList<BeaconBuilder>();
    		
    		index = 0;    	
    	}
    	
    	//Else, keep collecting info 
    	else 
    	{
    		for (IBeacon iBeacon: iBeacons) {
    			uuid = iBeacon.getProximityUuid();
    			major = iBeacon.getMajor();
    			minor = iBeacon.getMinor();
    			rssi = iBeacon.getRssi();
    			BeaconBuilder newBeacon = new BeaconBuilder(uuid, major, minor, rssi);
    			Log.i("iBeaconRanging", "BEACON("+minor+"): RSSI = "+rssi);
            
    			if(uuid.equalsIgnoreCase(LAB_UUID))
    			{
    				if(major == 4)
    				{
    	    			Log.i("iBeaconRanging", "BEACON RX("+minor+"): RSSI = "+rssi);

    					if(!beaconList.contains(newBeacon))
    					{
    						beaconList.add(newBeacon);
    						totalBeacons++;
    						Log.i("iBeaconRanging", "New Beacon Added to List");
    					}
            	
    					else
    					{	
    						BeaconBuilder existingBeacon = beaconList.get(beaconList.indexOf(newBeacon));
    						existingBeacon.averageRSSI(rssi);
    						Log.i("iBeaconRanging", "Beacon RSSI updated and averaged");
    					}
    				}
    			}
            
    			else Log.i("iBeaconRanging", "This ain't the right iBeacon! " + uuid);

    		}
    		
    	}
    	
    }    
    
    //***************************************************************************
    // Plot X and Y Coordinates to Lab View
    //***************************************************************************
    public void plotPoints(float x, float y) {
    	float xPos, yPos;
    	int halfHeight;
    	pointsPlotted = true;
    	
    	//Paint for LocationActivity
		Paint labPaint = new Paint();
		labPaint.setColor(Color.RED);
		labPaint.setStrokeWidth(4.5f);
		
    	//Initialize tempBitmap and attach a new canvas to it    
    	canvasBitmap = Bitmap.createBitmap(labBitmap.getWidth(), labBitmap.getHeight(), Bitmap.Config.ARGB_8888);
    	Canvas tempCanvas = new Canvas(canvasBitmap);
    	
    	halfHeight = labBitmap.getHeight() / 2;
    	
    	//Draw the image Bitmap onto the Canvas
    	tempCanvas.drawBitmap(labBitmap, 0, 0, null);
    
    	// Fit X and Y coordinates to screen density and draw on Canvas
    	float widthInPixels = getResources().getDisplayMetrics().widthPixels;
    	float heightInPixels = getResources().getDisplayMetrics().heightPixels;
    	float widthRatio = widthInPixels / LABWIDTHINMETERS; 
    	float heightRatio = heightInPixels / LABHEIGHTINMETERS;
    	
    	xPos = (x * heightRatio) + halfHeight;
    	yPos = y * widthRatio;
    	
    	tempCanvas.drawCircle(yPos, xPos, 5, labPaint);    		
    }    
    
    //***************************************************************************
    // Construct a single instance for training data set from iBeacon info
    //***************************************************************************
    public void saveDataInstance(Collection<IBeacon> iBeacons) {
    	String dataString = "";
    	for (IBeacon iBeacon: iBeacons) {
    		//Construct string
    		dataString += Integer.toString(iBeacon.getMinor()) + ", ";
    		dataString += Integer.toString(iBeacon.getRssi()) + "; ";
    	}
    	dataString += Integer.toString(currentZone);
    	dataList.add(dataString); 
    }  
    
    //*************************************************************************
    //Send data strings to server
    //*************************************************************************
    public void sendCollectedData() {
    	for(String data: dataList) {
    		sendToServer("trainingdata" + data); 
    	}  	
    }    
    
    //*********************************************************************************
    //Executes Connect Task, connects to server, sends message, closes connection
    //*********************************************************************************
    public void sendToServer(String message) {
    		Log.i("SENT", "sending message: " + message);
    		new ConnectTask().execute(message);
    }
    
    public void messageReceived(String message) {
    	
    }
    

    //************************************************************************************
    // ASYNCTASK: Creates connection to server
    //************************************************************************************
    public class ConnectTask extends AsyncTask<String,String,TcpClient> {
		@Override
		protected TcpClient doInBackground(String... message) {
			//we create a TCPClient object
			mTcpClient = new TcpClient(new TcpClient.OnMessageReceived() {
				@Override
				//here the messageReceived method is implemented
				public void messageReceived(String message) {
					//this method calls the onProgressUpdate
					publishProgress(message);
					Log.i("TESTING", "Received a message");
				}
			});
			
			ip = sharedPref.getString("ip_address", "");
			port = sharedPref.getString("port", "");
			mTcpClient.run(ip, port);
			String messageToServer = message[0];
			mTcpClient.sendMessage(messageToServer);
			return null;
		}
		
		//Provides Progress Updates (when message is received from server)
		@Override
		protected void onProgressUpdate(String... values) {
			super.onProgressUpdate(values);
			//here we can receive values when called from publishProgress
			//this function allows you to update the UI without hanging or any other problems
			//when you call your GUI object on it's .notifyDataSetChanged(), it will refresh automatically		
			text.setText("You got a message!!");
			text.setText("Message: ");
			//toast = Toast.makeText(getBaseContext(), "Received Message: See Message Box", Toast.LENGTH_SHORT);
			//toast.show();
			try{ JSONObject jsonReceived = new JSONObject(values[0]);
			
				x = Float.parseFloat(jsonReceived.getString("x"));
				y = Float.parseFloat(jsonReceived.getString("y"));
				Log.i("TESTING", "x: " + x + "y: " + y);
			
			} catch (Exception je) {}
			
			message.setText(Arrays.toString(values));
			plotPoints(x, y);
			
			//Close connection when message is received
			Log.i("TESTING", "Stopping Client.");			
			mTcpClient.stopClient();
		}	
    }
    
    //*******************************************************************************************************************
    //*******************************************************************************************************************
    //All Methods for StepService
    //*******************************************************************************************************************

    /** Called when the activity is first created to bind service */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = ((StepService.StepBinder)service).getService();
            mService.registerCallback(mCallback);
            mService.reloadSettings();
            Log.i(TAG, "Service Connection Happened");
        }
        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }
    };
     
 
    private void startStepService() {
        if (! mIsRunning) {
            Log.i(TAG, "[SERVICE] Start");
            mIsRunning = true;
            startService(new Intent(AClientActivity.this,StepService.class));
        }
    }
     
    private void bindStepService() {
        Log.i(TAG, "[SERVICE] Bind");
        bindService(new Intent(AClientActivity.this,
                StepService.class), mConnection, Context.BIND_AUTO_CREATE + Context.BIND_DEBUG_UNBIND);
    }
 
    private void unbindStepService() {
        Log.i(TAG, "[SERVICE] Unbind");
        unbindService(mConnection);
    }
     
    private void stopStepService() {
        Log.i(TAG, "[SERVICE] Stop");
        Log.i(TAG, "MSERVICE = " + mService);
        if (mService != null) {
            Log.i(TAG, "[SERVICE] stopService");
            stopService(new Intent(AClientActivity.this, StepService.class));
        }
        mIsRunning = false;
    }
     
    private void resetValues(boolean updateDisplay) {
        if (mService != null && mIsRunning) {
            mService.resetValues();                   
        }
        else {
            mStepValueView.setText("0");
            mAngleValueView.setText("0");
            mOrientationView.setText("?");
 
            SharedPreferences state = getSharedPreferences("state", 0);
            SharedPreferences.Editor stateEditor = state.edit();
            if (updateDisplay) {
                stateEditor.putInt("steps", 0);
                stateEditor.putInt("angle", 0);
                stateEditor.putInt("orientation", 0);
                stateEditor.commit();
            }
        }
    }

    // Message handling
    private StepService.ICallback mCallback = new StepService.ICallback() {
        public void stepsChanged(int value) {
            mHandler.sendMessage(mHandler.obtainMessage(STEPS_MSG, value, 0));
        }
        public void angleChanged(int value) {
            mHandler.sendMessage(mHandler.obtainMessage(ANGLE_MSG, value, 0));
        }
        public void orientChanged(int value) {
            mHandler.sendMessage(mHandler.obtainMessage(ORIENT_MSG, value, 0));
        }
    };
     
    private static final int STEPS_MSG = 1;
    private static final int ANGLE_MSG = 2;
    private static final int ORIENT_MSG = 3;
    private static final int ORIENT_DFT = 0;
    private static final int ORIENT_OFF = 1;
    private static final int ORIENT_HND = 2;
    private static final int ORIENT_BDY = 3;
    String orientationValue;
     
    private Handler mHandler = new Handler() {
        @Override public void handleMessage(Message msg) {
            switch (msg.what) {
                case STEPS_MSG:
                    mStepValue = (int)msg.arg1;
                    mStepValueView.setText("" + mStepValue);
                    mPathView.addStep(1.0);
                    mPathView.invalidate();
                    sensorValsToServer();
                    break;
                case ANGLE_MSG:
                    mAngleValue =(int)msg.arg1;
                    //if(mAngleValue <= 0){ mAngleValueView.setText("0");}
                    mAngleValueView.setText("" + mAngleValue);
                    mPathView.addTurn(mAngleValue);
                    break;
                case ORIENT_MSG:
                    mOrientation = (int)msg.arg1;
                    switch(mOrientation){
                        case ORIENT_DFT:
                        	orientationValue = "Unknown";
                            mOrientationView.setText("" + orientationValue);
                            break;
                        case ORIENT_BDY:
                        	orientationValue = "On Body (pocket)";
                            mOrientationView.setText("" + orientationValue);
                            break;
                        case ORIENT_HND:
                        	orientationValue = "In Hand";
                            mOrientationView.setText("" + orientationValue);
                            break;
                        case ORIENT_OFF:
                        	orientationValue = "Still";
                            mOrientationView.setText("" + orientationValue);
                            break;
                        default:
                        	orientationValue = "Unknown State";
                            mOrientationView.setText("" + orientationValue);
                    }
                default:
                    super.handleMessage(msg);
            }
        }
    };
    
    public void sensorValsToServer() {
    	//Send sensor info to server as JSON Object
    	JSONObject info = new JSONObject();
    	String stepLength = sharedPref.getString("step_length", "");
		try {
			//info.put("Step", String.valueOf(mStepValue));
			//info.put("Angle", String.valueOf(mAngleValue));
			info.put("StepLength", stepLength);
		}
		catch (Exception e){
			Log.e(TAG, "ERROR");
		}
		
    	String sensor_msg = "deadreckoninginfo" + info.toString();
    	sendToServer(sensor_msg);   	
    }    
}