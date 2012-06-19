package org.sana.android.util;

import org.sana.android.net.APIException;
import org.sana.android.net.MDSInterface;

import android.content.ContentResolver;
import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * DataConnectionListener is a listener that waits for a data connection. Once a 
 * data connection is available to the phone, it checks the pending transfer database 
 * to see if there are any pending transfers. If so, they are uploaded.
 * 
 * NOTE that uploading only happens when the data connection is initiated. This requires
 * a transition from no connection to connection.
 * 
 * This class is not explicitly necessary - in theory, the BackgroundUploader process
 * will do this work. However, for places where a connection may only come for a few 
 * seconds, this class ensures uploads start as soon as the connection starts.
 */
public class DataConnectionListener extends PhoneStateListener {
	private Context context;
	private int currentState;
	public static final String TAG = DataConnectionListener.class.toString();

	public DataConnectionListener(Context c) {
		super();
		context = c;
	}

	@Override
	public void onDataConnectionStateChanged(int state) {
		currentState = state;
		
		new Thread() {
			public void run() {
				if (currentState == TelephonyManager.DATA_CONNECTED) {
					Log.i("TAG", "Data-link is now connected");
					try {
						// TODO(XXX) This call can sometimes freeze the phone on app startup if the target MDS is down.
						boolean credentialsValid = MDSInterface.validateCredentials(context);
						BackgroundUploader.credentialsChanged(credentialsValid, context.getContentResolver());
					} catch (APIException e) {
						Log.e(TAG, "validateCredentials threw APIException " + e);
					}
					// To make sure status is updated in database
					BackgroundUploader.checkConnection(context, context.getContentResolver()); 
					Log.i(TAG, "Starting uploads");
					BackgroundUploader.initUploads(context);
					
					
				} else {
					Log.i("TAG", "Data-link is now disconnected");
					// To make sure status is updated in database
					BackgroundUploader.checkConnection(context, context.getContentResolver()); 
				}
			}
		}.start();
	}
}
