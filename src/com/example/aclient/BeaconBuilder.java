package com.example.aclient;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONObject;

import android.util.Log;

public class BeaconBuilder {
	
	String uuid;
	int major, minor, rssi, count;
	float totalRSSI,avgRSSI;
	
	//Constructor of the class. Takes UUID, Major, Minor, RSSI, and Accuracy as parameters
	public BeaconBuilder(String uuid, int major, int minor, int rssi)  {
		this.uuid = uuid;
		this.major = major;
		this.minor = minor;
		this.rssi = rssi;
		this.avgRSSI = rssi;
		totalRSSI = rssi;
		
		count = 1;
	}
	
	//Adds rssi to running average of all rssi values seen in this cycle
	public void averageRSSI(int moreRSSI) {
		totalRSSI += (float)moreRSSI;	
		avgRSSI = totalRSSI / ++count;
		//Log.i("AVGQ", "Minor: " + minor + "averageRSSI: " + avgRSSI);
		
	}	
	
	//Reset RSSI and averaging values for new collection
	public void resetRSSI() {
		rssi = 0;
		count = 1;
		totalRSSI = 0;
		avgRSSI = 0;
	}
	
	
	//*************************************************************************************
	// Routine Getters and Setters for Beacon 
	//*************************************************************************************	
	public String getUUID() {
		return uuid;
	}
	
	public int getMajor() {
		return major;
	}
	
	public int getMinor() {
		return minor;
	}
	
	public int getRSSI() {
		return rssi;
	}
	
	//toString(): returns formatted string with all of this Beacon's information
	public String toString() {
		String beacon = "UUID: " + uuid + "\nMajor: " + major + "\nMinor" + minor 
				+ "\nRSSI: " + rssi;
		
		return beacon;
	}
	
    // Returns a string in JSON format
    public JSONObject toJSONObject() {
    	JSONObject action = new JSONObject();
		//String date = getTimeStamp();		
		
		try {
			//Create JSON Object
			action.put("UUID", uuid);
			action.put("Major", String.valueOf(major));
			action.put("Minor", String.valueOf(minor));
			action.put("RSSI", String.valueOf((int)avgRSSI));
		}
		catch (Exception e){
			Log.e("JSON ERROR", "ERROR");
		}
    	
		return action;
    }
	
    // Get Time Stamp for JSON string
   public String getTimeStamp() {
	   String returnDate;
	   long milliseconds = System.currentTimeMillis();
	   Date tempDate = new Date(milliseconds);
	   SimpleDateFormat sdf= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  
	   returnDate = sdf.format(tempDate); //Gets Date and Time
   
	   return returnDate;
   }
   
   // Override of equals() and hashCode() so that we can use contains method of ArrayList
   @Override
   public boolean equals(Object object)
   {
       boolean isEqual = true;

       if (object == null || !(object instanceof BeaconBuilder))
       { 
    	   isEqual = false;
       }

       if (this.major != ((BeaconBuilder) object).major)
       {
          isEqual = false;
       }

       if (this.minor != ((BeaconBuilder)object).minor)
       {
          isEqual = false;
       }       

       return isEqual;
   }

   @Override
   public int hashCode() {
       return this.major * this.minor;
   }
	

}
