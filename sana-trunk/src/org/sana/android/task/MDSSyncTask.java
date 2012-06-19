package org.sana.android.task;

import java.util.ArrayList;
import java.util.List;

import org.sana.android.R;
import org.sana.android.db.Event;
import org.sana.android.db.DispatchableContract.Events;
import org.sana.android.net.APIException;
import org.sana.android.net.MDSInterface;
import org.sana.android.util.MocaUtil;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Task for synching with an MDS instance.
 * 
 * @author Sana Development Team
 *
 */
public class MDSSyncTask extends AsyncTask<Context, Void, Integer> {
	public static final String TAG = MDSSyncTask.class.getSimpleName();
	/** Indicates a connection could not be established for synching. */
	public static final Integer EMR_SYNC_NO_CONNECTION = 0;
	/** Indicates synching was successful. */
	public static final Integer EMR_SYNC_SUCCESS = 1;
	/** Indicates synching failed. */
	public static final Integer EMR_SYNC_FAILURE = 2;
	
	private ProgressDialog progressDialog;
	private Context mContext = null; // TODO context leak?
	
	/** 
	 * A new synchronization task.
	 * 
	 * @param c the COntext to synch with
	 */
	public MDSSyncTask(Context c) {
		mContext = c;
	}
	
	private boolean syncPatients(Context c) {
		try {
			return MDSInterface.updatePatientDatabase(c, c.getContentResolver());
		} catch (APIException e) {
			// TODO Auto-generated catch block
			return false;
		}
	}
	
	private boolean syncEvents(Context c) {
		Cursor cursor = null; 
		Log.i(TAG, "Syncing the event log to the MDS.");
		
		try {
			// Get all un-uploaded events.
			cursor = c.getContentResolver().query(Events.CONTENT_URI, 
					new String[] {  Events._ID, 
								    Events.CREATED_DATE, 
									Events.EVENT_TYPE, 
									Events.EVENT_VALUE, 
									Events.ENCOUNTER_REFERENCE, 
									Events.PATIENT_REFERENCE, 
									Events.USER_REFERENCE }, 
					Events.UPLOADED+"=?", new String[] { "0" }, null);
			int numEvents = cursor.getCount();
			
			if (numEvents == 0) {
				// Nothing to upload, quit.
				Log.i(TAG, "No unuploaded events. Skipping syncEvents.");
				return true;
			} else {
				Log.i(TAG, "There are " + numEvents + " unuploaded events.");
			}
			
			StringBuilder sb = new StringBuilder("(");
			List<Event> events = new ArrayList<Event>(numEvents);

			cursor.moveToFirst();
			while (!cursor.isAfterLast()) {
				
				Event e = new Event();
				e.event_time = cursor.getLong(cursor.getColumnIndex(
						Events.CREATED_DATE));
				e.event_type = cursor.getString(cursor.getColumnIndex(
						Events.EVENT_TYPE));
				e.event_value = cursor.getString(cursor.getColumnIndex(
						Events.EVENT_VALUE));
				e.encounter_reference = cursor.getString(cursor.getColumnIndex(
						Events.ENCOUNTER_REFERENCE));
				e.patient_reference = cursor.getString(cursor.getColumnIndex(
						Events.PATIENT_REFERENCE));
				e.user_reference = cursor.getString(cursor.getColumnIndex(
						Events.USER_REFERENCE));
				int id = cursor.getInt(cursor.getColumnIndex(
						Events._ID));
				events.add(e);
				
				sb.append(id);
				if (!cursor.isLast()) {
					sb.append(",");
				}
				cursor.moveToNext();
			}
			sb.append(")");

			// Submit the events to the MDS
			boolean result = MDSInterface.submitEvents(c, events);
			
			// Set the uploaded events as uploaded in the database.
			if (result) {
				Log.i(TAG, "Successfully uploaded " + numEvents + " events.");
				ContentValues cv = new ContentValues();
				cv.put(Events.UPLOADED, 1);
				int rowsUpdated = c.getContentResolver().update(
						Events.CONTENT_URI, cv, 
						Events._ID +" in " + sb.toString(), null);
				if (rowsUpdated != numEvents) {
					Log.w(TAG, 
					"Didn't get as many rows updated as we thought we would.");
				}
			} 
			return result;
		} catch (Exception e) {
			Log.e(TAG, "While trying to submit the event log, got exception: "
					+ e.toString());
			e.printStackTrace();
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected Integer doInBackground(Context... params) {
		Log.i(TAG, "Executing EMRSyncTask");
		Context c = params[0];
		
		Integer result = EMR_SYNC_NO_CONNECTION; // TODO detect this case better
		try{
			if (MocaUtil.checkConnection(c)) {
				boolean patientSyncResult = syncPatients(c);
				boolean eventSyncResult = syncEvents(c);
			
				result = (patientSyncResult && eventSyncResult) ? 
						EMR_SYNC_SUCCESS : EMR_SYNC_FAILURE;  
			}
		} catch(Exception e){
			Log.e(TAG, "Could not sync. " + e.toString());
		}
		return result;
	}

	/** {@inheritDoc} */
	@Override
	protected void onPreExecute() {
		Log.i(TAG, "About to execute EMRSyncTask");
		if (progressDialog != null) {
    		progressDialog.dismiss();
    		progressDialog = null;
    	}
		progressDialog = new ProgressDialog(mContext);
    	progressDialog.setMessage("Updating patient database cache");
    	progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    	progressDialog.show();	
    }

	/** {@inheritDoc} */
	@Override
	protected void onPostExecute(Integer result) {
		Log.i(TAG, "Completed EMRSyncTask");
		if (progressDialog != null) {
    		progressDialog.dismiss();
    		progressDialog = null;
    	}
	}
	
}
