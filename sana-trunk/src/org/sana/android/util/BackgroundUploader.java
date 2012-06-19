package org.sana.android.util;

import java.util.Collection;
import java.util.PriorityQueue;
import java.util.Timer;
import java.util.TimerTask;

import org.sana.android.Constants;
import org.sana.android.db.DispatchableContract.Procedures;
import org.sana.android.db.DispatchableContract.Encounters;
import org.sana.android.net.APIException;
import org.sana.android.net.MDSInterface;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

/**
 * Background service to upload pending transfers when data service is available.
 * NOTE: This is used in conjunction with DataConnectionListener which also offloads
 * pending transfers, but only on a no connection -> connection transition.
 * This class will try to offload the pending transfers at a certain interval -
 * useful because we may have given up on a transfer during an episode with virtually
 * no usable connection, but technically still within GPRS service area (so a transition
 * never occurs).
 */
public class BackgroundUploader extends Service {
	private static final String TAG = BackgroundUploader.class.toString();

	private static final int UPLOAD_STATUS_NOT_IN_QUEUE = -1;
	private static final int UPLOAD_STATUS_WAITING = 1;
	private static final int UPLOAD_STATUS_SUCCESS = 2;
	private static final int UPLOAD_STATUS_IN_PROGRESS = 3;
	private static final int UPLOAD_NO_CONNETIVITY = 4;
	private static final int UPLOAD_STATUS_FAILURE = 5;
	private static final int UPLOAD_STATUS_CREDENTIALS_INVALID = 6;

	private final IBinder mBinder = new LocalBinder();
	private static TelephonyManager telMan;
	private static WifiManager wifiMan;
	private Timer timer = new Timer();
	private static final long POLL_INTERVAL = Constants.DEFAULT_POLL_PERIOD * 1000;
	private static PriorityQueue<Uri> queue = new PriorityQueue<Uri>();

	private static boolean uploadFlag = false;
	private static boolean result = false;
	private static ContentResolver contentResolver;
	private static boolean isCredentialsValidated = false;

	private static final String[] PROJECTION = { Encounters._ID,
		Encounters.UUID, Encounters.PROCEDURE_ID,
		Encounters.UPLOAD_QUEUE };

	public class LocalBinder extends Binder {
		BackgroundUploader getService() {
			return BackgroundUploader.this;
		}
	}

	@Override
	public void onCreate() {
		// Log.i(TAG, "in oncreate()");
		try {
			super.onCreate();
			startService();
			telMan = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
			wifiMan = (WifiManager) getSystemService(WIFI_SERVICE);
			contentResolver = getContentResolver();

			initQueue(getBaseContext());
		} catch (Exception e) {
			Log.e(TAG, "Exception creating background uploading service: "
					+ e.toString());
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		shutdownService();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	// Used by methods in this class, as well as in other classes,
	// such as in Settings.java before validating password.
	public static boolean checkConnection(Context c, ContentResolver cr) {
		try {
			boolean hasConnection;
			telMan = (TelephonyManager) c
			.getSystemService(Context.TELEPHONY_SERVICE);
			wifiMan = (WifiManager) c.getSystemService(WIFI_SERVICE);
			hasConnection = telMan.getDataState() == TelephonyManager.DATA_CONNECTED
			|| (wifiMan.isWifiEnabled() && wifiMan.pingSupplicant());
			Log.i(TAG, "checkConnection is returning: " + hasConnection);
			if (hasConnection) {
				try {
					Log.i(TAG, "Here is the queue right now: " + queue);
					Log.i(TAG,
							"Right now the value of isCredentialsValidated is: "
							+ isCredentialsValidated);
					int status;
					if (isCredentialsValidated) {
						// Signify procedures waiting in queue to be uploaded
						status = UPLOAD_STATUS_WAITING;
					} else {
						// Signify username/password incorrect
						status = UPLOAD_STATUS_CREDENTIALS_INVALID;
					}
					setProceduresUploadStatus(queue, status);
				} catch (Exception e) {
					Log.e(TAG, "Exception updating upload status in database: "
							+ e.toString());
				}
				return true;
			} else {
				try {
					// Signify procedures waiting for connectivity to upload
					setProceduresUploadStatus(queue, UPLOAD_NO_CONNETIVITY);
				} catch (Exception e) {
					Log.e(TAG, "Exception updating upload status in database: "
							+ e.toString());
				}
				return false;
			}
		} catch (Exception e) {
			Log.e(TAG, "Exception in checkConnection(): " + e.toString());
			return false;
		}
	}

	private void startService() {
		try {
			// We need to call validateCredentials in a thread because this is an 
			// Android callback. If we take too long here then Android will kill
			// us with an ANR. 
			new Thread() {
				public void run() {
					if (checkConnection(getBaseContext(), contentResolver)) {
						try {
							isCredentialsValidated = MDSInterface.validateCredentials(getBaseContext());
						} catch (APIException e) {
							Log.e(TAG, "Could not lookup credentials in BackgroundUploader.startService: " + e);
						}
					}
				}
			}.start();
			
			timer.scheduleAtFixedRate(
					new TimerTask() {
						public void run() {
							Log.i(TAG, "BackgroundUploader TimerTask fired -- calling initUploads");
							// initUploads already checks if we have a connection
							initUploads(getBaseContext());
						}
					},
					0,
					POLL_INTERVAL);
		}
		catch (Exception e) {
			Log.e(TAG, "Exception in starting service: " + e.toString());
		}
	}

	private void shutdownService() {
		if (timer != null) 
			timer.cancel();
	}

	private static void setProcedureUploadStatus(Uri procedureUri, int status) {
		ContentValues cv = new ContentValues();
		cv.put(Encounters.UPLOAD_STATUS, status); 
		contentResolver.update(procedureUri, cv, null, null); 
	}

	private static void setProceduresUploadStatus(Collection<Uri> procedureUris, int status) {
		ContentValues cv = new ContentValues();
		cv.put(Encounters.UPLOAD_STATUS, status);
		for (Uri uri : procedureUris) {
			contentResolver.update(uri, cv, null, null);
		}
	}

	public static boolean addProcedureToQueue(Uri procedureUri) {
		Log.i(TAG, "addProcedureToQueue" + procedureUri);
		
		boolean result = queue.add(procedureUri);
		Log.i(TAG, "Queue is now: " + queue.toString());
		
		if (isCredentialsValidated) {
			//Signify waiting in queue to be uploaded
			setProcedureUploadStatus(procedureUri, UPLOAD_STATUS_WAITING);
		} else {
			// Signify procedures incorrect username/password
			setProcedureUploadStatus(procedureUri, UPLOAD_STATUS_CREDENTIALS_INVALID);
		}

		updateQueueInDB();
		
		Log.i(TAG, "Succeeded in adding procedure to upload queue? " + result);
		return result;
	}

	public static boolean removeFromQueue(Uri procedureUri) {
		Log.i(TAG, "removeFromQueue " + procedureUri);
		if (isInQueue(procedureUri)) {
			queue.remove(procedureUri);
			updateQueueInDB();
			setProcedureUploadStatus(procedureUri, UPLOAD_STATUS_NOT_IN_QUEUE);
			return true;
		}
		return false;
	}

	public static boolean isInQueue(Uri procedureUri) {
		return queue.contains(procedureUri);
	}

	private static int queueIndex(Uri procedureUri) {
		if (isInQueue(procedureUri)) {
			Object[] queueList = queue.toArray();
			for (int i = 0; i < queueList.length; i++) {
				Uri test = (Uri) queueList[i];
				if (test.equals(procedureUri))
					return i;
			}
		}
		return -1;
	}

	//Only check credentials with openMRS when username or password have changed in settings
	public static void credentialsChanged(boolean credentials, ContentResolver cr) {
		isCredentialsValidated = credentials;
		Log.i(TAG, "Queue is now: " + queue);
		if (isCredentialsValidated) {
			Log.i(TAG, "In credentialsChanged, Validate Credentials returned true");
			setProceduresUploadStatus(queue, UPLOAD_STATUS_WAITING);
		} else {
			Log.i(TAG, "In credentialsChanged, Validate Credentials returned false - could not validate OpenMRS username/password");
			setProceduresUploadStatus(queue, UPLOAD_STATUS_CREDENTIALS_INVALID);
		}
	}

	public static void initUploads(final Context c) {
		// check if there are pending transfers in the database
		// if so, then spawn a thread to upload the first one

		Log.i(TAG, "initUploads has been called.");
		uploadFlag = (!queue.isEmpty()) && checkConnection(c, contentResolver);
		if (uploadFlag) {	  
			if (!isCredentialsValidated) {
				Log.i(TAG, "OpenMRS username/password incorrect - will not attempt to upload");
			}
			else {
				Thread t = new Thread() {
					public void run() {
						Looper.prepare();
						while (uploadFlag) {
							Log.i(TAG, "initUploads() looping");
							Uri procedure = queue.element();
							// Signify procedure upload in progress
							setProcedureUploadStatus(procedure, UPLOAD_STATUS_IN_PROGRESS);
							
							try {
								result = MDSInterface.submitCase(procedure, c);		  
								if(result) 
									uploadSuccess(c, procedure);
								else
									uploadFailure(c, procedure);
							} catch(APIException e) {
								Log.i(TAG, "While in submitCase() in the background uploader, received APIException: " + e.toString());
								uploadFailure(c, procedure);
							}
							uploadFlag = !queue.isEmpty() && checkConnection(c, contentResolver);
							Log.i(TAG, "uploadFlag after a loop: " + uploadFlag);
						}
						Looper.loop();
						Looper.myLooper().quit();
					}
				};
				Log.i(TAG, "running a new thread to do the upload.");
				t.start();
			}
		}
	}

	private static void uploadSuccess(Context c, Uri procedure) {
		Log.i(TAG, "Upload was successfull. Now in uploadSuccess()");
		removeFromQueue(procedure); //Remove the procedure from the queue after it has been successfully uploaded
		setProcedureUploadStatus(procedure, UPLOAD_STATUS_SUCCESS);

		Cursor cursor = c.getContentResolver().query(procedure, new String [] { Encounters._ID, Encounters.PROCEDURE_ID, Encounters.PROCEDURE_STATE }, null, null, null);        
		cursor.moveToFirst();
		long savedProcedureId = cursor.getLong(cursor.getColumnIndex(Encounters._ID));
		long procedureId = cursor.getLong(cursor.getColumnIndex(Encounters.PROCEDURE_ID));
		cursor.deactivate();
		int sizeOfQueue = queue.size();
		Uri procedureUri = ContentUris.withAppendedId(Procedures.CONTENT_URI, procedureId);;
		cursor = c.getContentResolver().query(procedureUri, new String[] { Procedures.TITLE }, null, null, null);
		cursor.moveToFirst();
		String procedureTitle = cursor.getString(cursor.getColumnIndex(Procedures.TITLE));
		cursor.deactivate();

		//String msg = "Successfully sent " + procedureTitle + " procedure\nwith ID = " + savedProcedureId;
		String msg = "Successfully sent procedure\nwith ID = " + savedProcedureId;
		if (sizeOfQueue != 0) {
			msg += "\nThere are still " + sizeOfQueue + "\nprocedures to be uploaded.";
		}
		else {
			msg += "\nAll procedures are done uploading.";
		}
		Toast toast = Toast.makeText(c, msg, Toast.LENGTH_LONG);
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.show();
	}

	private static void uploadFailure(Context c, Uri procedure) {
		Log.i(TAG, "Upload failed. Now in uploadFailure()");
		// Remove the procedure from the queue so it does not keep trying to upload
		removeFromQueue(procedure); 
		setProcedureUploadStatus(procedure, UPLOAD_STATUS_FAILURE);
	}

	private static void updateQueueInDB() {
		Log.i(TAG, "Updating queue information in the database");
		Log.i(TAG, "Queue is now: " + queue.toString());
		ContentValues cv;
		// TODO(XXX) This loop is inefficient -- O(n^2) when it could be O(n)
		for (Uri procedureUri : queue) {
			cv = new ContentValues();
			int index = queueIndex(procedureUri);
			Log.i(TAG, "In updateQueueInDB, queueIndex(" + procedureUri
					+ ") returns: " + index);
			cv.put(Encounters.UPLOAD_QUEUE, index);
			contentResolver.update(procedureUri, cv, null, null);
		}
	}

	private static void initQueue(Context c) {
		try {
			// Initialize the queue from the database
			Log.i(TAG, "In initQueue - getting queue from database");
			Cursor cursor = c.getContentResolver().query(
					Encounters.CONTENT_URI, PROJECTION,
					Encounters.UPLOAD_QUEUE + " >= 0", null,
					Encounters.QUEUE_SORT_ORDER);
			cursor.moveToFirst();

			while (!cursor.isAfterLast()) {
				int savedProcedureId = cursor.getInt(0);
				Log.i(TAG, "savedProcedureId is: " + savedProcedureId);
				Uri savedProcedureUri = ContentUris.withAppendedId(
						Encounters.CONTENT_URI, savedProcedureId);
				Log.i(TAG, "Adding procedure with this Uri to the queue: "
						+ savedProcedureUri);
				queue.add(savedProcedureUri);
				cursor.moveToNext();
			}
			Log.i(TAG, "Queue has been extracted from database. Here is the queue: " + queue);
		} catch (Exception e) {
			Log.e(TAG, "Exception in getting queue from database: "
					+ e.toString());
		}
	}
}