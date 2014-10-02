package com.example.aclient;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

import org.json.JSONObject;

import android.util.Log;

public class TcpClient {

	public static String SERVER_IP; 					//your computer IP address
	public static int SERVER_PORT;						//port number for connection
	private String mServerMessage;						//message to send to the server
	private OnMessageReceived mMessageListener = null; 	//sends message received notifications
	private PrintWriter mBufferOut;						//used to send messages
	private BufferedReader mBufferIn;					//used to read messages from the server
	public static boolean isConnected = false;
	JSONObject jsonReceiver;
	boolean waitingForMessage = false;
	Socket socket;
	
	//Constructor of the class. OnMessagedReceived listens for the messages received from server
	public TcpClient(OnMessageReceived listener) {
		mMessageListener = listener;
	}

	//Sends the message entered by client to the server
	public void sendMessage(String message) {
		if (mBufferOut != null && !mBufferOut.checkError()) {
			mBufferOut.println(message);
			mBufferOut.flush();
			waitingForMessage = true;
		}
		listenForMessage();	
	}

	//Listens for message from server
	public void listenForMessage() {
		if (waitingForMessage) {
			try {
				mServerMessage = mBufferIn.readLine();
				if (mServerMessage != null && mMessageListener != null) {
					//Signal that a message has been received
					mMessageListener.messageReceived(mServerMessage);
				}
			} 
			catch (Exception e) {Log.e("TCP", "C: Error", e);}
		}
		Log.e("RESPONSE FROM SERVER", "S: Received Message: '" + mServerMessage + "'");
	}
	
	//Close the connection and release the members
	public void stopClient() {
		Log.i("Debug", "stopClient");
		
		try {
			socket.close();
		} catch (Exception e) {Log.e("TCP", "C: Error", e);}
		
		// send message that we are closing the connection
		if (mBufferOut != null) {
			mBufferOut.flush();
			mBufferOut.close();
		}

		mMessageListener = null;
		mBufferIn = null;
		mBufferOut = null;
		mServerMessage = null;

		isConnected = false;
	}

	//Run takes two string arguments to specify IP Address and Port number.
	public void run(String ipAddr, String port) {
		SERVER_IP = ipAddr;
		SERVER_PORT = Integer.parseInt(port);
		Log.i("TESTING", "99");

		try
		{
			Log.e("TCP Client", "C: Connecting...");
			
			//here you must put your computer's IP address.
			InetAddress serverAddr = InetAddress.getByName(SERVER_IP);

			//create a socket to make the connection with the server
			socket = new Socket(serverAddr, SERVER_PORT);
					
			isConnected = true;
			
			//sends the message to the server
			mBufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
			//receives the message which the server sends back
			mBufferIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			Log.i("TCP Client", "Created both Buffers");				
			
       }
		catch (Exception e) {Log.e("TCP", "C: Error", e);}
		
	}

	//Declare the interface. The method messageReceived(String message) will must be implemented in the MyActivity
	//class at on asynckTask doInBackground
	public interface OnMessageReceived {
		public void messageReceived(String message);
	}
}


