package org.sana.android.net;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.sana.android.Constants;
import org.sana.android.db.Event;
import org.sana.android.db.DispatchableContract.ImageSQLFormat;
import org.sana.android.db.DispatchableContract.Procedures;
import org.sana.android.db.DispatchableContract.Encounters;
import org.sana.android.db.DispatchableContract.SoundSQLFormat;
import org.sana.android.procedure.Procedure;
import org.sana.android.procedure.ProcedureParseException;
import org.sana.android.procedure.ProcedureElement.ElementType;
import org.sana.android.util.MocaUtil;
import org.sana.android.util.PatientDatabase;
import org.xml.sax.SAXException;

import com.google.gson.Gson;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Interface for uploading to the Moca Dispatch Server.
 * 
 * This is where all of the packetization and http posting occurs.  Other than
 * some database interactions, it is fairly independent of Android.
 * 
 * The process for uploading a procedure is as follows (item number two takes
 * place on a remote server, the other steps take place in the code in this
 * source file):
 * 
 * 1) Post question/response pairs from completed procedure via http, tagging it
 *    with procedure, patient, and phone IDs.
 * 2) Moca Dispatch Server (MDS) parses the questions to see if they include any
 *    binary elements (i.e. a page in the procedure that asks to take a
 *    picture). If there are pending binary uploads, MDS knows to expect them
 *    and does not send the completed upload to OpenMRS until all parts are
 *    received.
 * 3) For each binary element, Moca uploads chunks of the element to the
 *    MDS. The size of these chunks starts at a default size. Each chunk is
 *    tagged with a procedure, patient, and phone ID as well as an element
 *    identifier and the start and end byte numbers (corresponding to the chunk
 *    location).
 * 4) If the first chunk successfully uploads, the chunk size for the next chunk
 *    transmission doubles. If the post fails, the chunk size halves.
 * 5) If the chunk size falls below a default "give up" threshold, the procedure
 *    is tagged as not- finished-uploading, and Moca waits to transmit the rest
 *    of the completed procedure at a later time. If the entire binary element
 *    is successfully transmitted, it moves on to the next element.
 * 6) It repeats steps 3-5 for subsequent elements, but instead of starting at
 *    the default chunk size for each transmission, it now has knowledge about
 *    the connection quality and uses the last successful transmission size from
 *    the last binary element as a starting point.
 */
public class MDSInterface {
	public static final String TAG = MDSInterface.class.toString();

	public static String[] savedProcedureProjection = new String[] {
		Encounters._ID, Encounters.PROCEDURE_ID,
		Encounters.PROCEDURE_STATE, Encounters.FINISHED,
		Encounters.UUID, Encounters.UPLOADED };

	/**
	 * Posts a single chunk of a binary file.
	 * 
	 * @param c current context
	 * @param savedProcedureId
	 * @param elementId
	 * @param type binary type (ie picture, sound, etc.)
	 * @param start first byte index in binary file (since this presumably is a chunk of a larger file)
	 * @param end last byte index in binary file of the chunk being uploaded
	 * @param byte_data a byte array containing the file chunk data
	 * @return true on successful upload, otherwise false
	 */
	private static boolean postBinary(Context c, MocaAPI api, String savedProcedureId, String elementId, String fileGuid, 
			ElementType type, int fileSize, int start, int end, byte byte_data[]) throws APIException {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(c);
		boolean hacksMode = preferences.getBoolean(Constants.PREFERENCE_UPLOAD_HACK, false);		
		
		Log.i(TAG, "postBinary() : " + (hacksMode ? "encoding binary as Base64 text" : "uploading binary data as a file"));
		
		boolean success = false;
		if (hacksMode) {
			success = api.transmitBinarySequenceAsBase64Text(savedProcedureId, elementId, 
					fileGuid, type.toString(), type.getFilename(), fileSize, start, end, byte_data);
		} else {
			success = api.transmitBinarySequence(savedProcedureId, elementId, 
					fileGuid, type.toString(), type.getFilename(), fileSize, start, end, byte_data);
		}
		Log.d(TAG, "The binary upload " + (success ? "succeeded" : "failed"));
		return success;
	}
	
	public static boolean isProcedureAlreadyUploaded(Uri uri, Context context) {
		Cursor cursor = context.getContentResolver().query(uri, savedProcedureProjection, null,
				null, null);
		// First get the saved procedure...
		cursor.moveToFirst();
		int procedureId = cursor.getInt(1);
		String answersJson = cursor.getString(2);
		boolean savedProcedureUploaded = cursor.getInt(5) != 0;
		cursor.deactivate();
		
		if (!savedProcedureUploaded) 
			return false;

		Uri procedureUri = ContentUris.withAppendedId(Procedures.CONTENT_URI, procedureId);
		Log.i(TAG, "Getting procedure " + procedureUri.toString());
		cursor = context.getContentResolver().query(procedureUri, new String[] { Procedures.PROCEDURE }, null, null, null);
		cursor.moveToFirst();
		String procedureXml = cursor.getString(0);
		cursor.deactivate();

		Map<String, Map<String,String>> elementMap = null;
		try {
			Procedure p = Procedure.fromXMLString(procedureXml);
			p.setInstanceUri(uri);

			JSONTokener tokener = new JSONTokener(answersJson);
			JSONObject answersDict = new JSONObject(tokener);

			Map<String,String> answersMap = new HashMap<String,String>();
			Iterator<?> it = answersDict.keys();
			while(it.hasNext()) {
				String key = (String)it.next();
				answersMap.put(key, answersDict.getString(key));
				Log.i(TAG, "onCreate() : answer '" + key + "' : '" + answersDict.getString(key) +"'");
			}
			Log.i(TAG, "onCreate() : restoreAnswers");
			p.restoreAnswers(answersMap);
			elementMap = p.toElementMap();

		} catch (IOException e2) {
			Log.e(TAG, e2.toString());
		} catch (ParserConfigurationException e2) {
			Log.e(TAG, e2.toString());
		} catch (SAXException e2) {
			Log.e(TAG, e2.toString());
		} catch (ProcedureParseException e2) {
			Log.e(TAG, e2.toString());
		} catch (JSONException e) {
			Log.e(TAG, e.toString());
		}

		if(elementMap == null) {
			Log.i(TAG, "Could not read questions and answers from " + uri + ". Not uploading.");
			return false;
		}

		class ElementAnswer {
			public String answer;
			public String type;
			public ElementAnswer(String id, String answer, String type) {
				this.answer = answer;
				this.type = type;
			}
		}

		int totalBinaries = 0;
		List<ElementAnswer> binaries = new ArrayList<ElementAnswer>();
		for(Entry<String,Map<String,String>> e : elementMap.entrySet()) {
			
			String id = e.getKey();
			String type = e.getValue().get("type");
			String answer = e.getValue().get("answer");

			// Find elements that require binary uploads
			if (type.equals(ElementType.PICTURE.toString()) ||
					type.equals(ElementType.BINARYFILE.toString()) ||
					type.equals(ElementType.SOUND.toString())) {
				binaries.add(new ElementAnswer(id, answer, type));
				if (!"".equals(answer)) {
					String[] ids = answer.split(",");
					totalBinaries += ids.length;
				}
			}
		}
		// upload each binary file
		for(ElementAnswer e : binaries) {

			if("".equals(e.answer))
				continue;

			String[] ids = e.answer.split(",");

			for(String binaryId : ids) {
				Uri binUri = null;
				ElementType type = ElementType.INVALID;
				try {
					type = ElementType.valueOf(e.type);
				} catch(IllegalArgumentException ex) {
				}

				if (type == ElementType.PICTURE) {
					binUri = ContentUris.withAppendedId(ImageSQLFormat.CONTENT_URI, Long.parseLong(binaryId));	
				} else if (type == ElementType.SOUND) {
					binUri = ContentUris.withAppendedId(SoundSQLFormat.CONTENT_URI, Long.parseLong(binaryId));
				} else if (type == ElementType.BINARYFILE) {
					binUri = Uri.fromFile(new File(e.answer));
					// We can't tell if a BINARYFILE has been uploaded before.
					// Maybe if we grab the mtime/filesize on the file and store
					// it when we upload it.
				}

				try {
					Log.i(TAG, "Checking if " + binUri + " has been uploaded");
					// reset the new packet size each time to the last successful transmission size
					boolean alreadyUploaded = false;
					Cursor cur;
					switch(type) {
					case PICTURE:
						cur = context.getContentResolver().query(binUri, new String[] { ImageSQLFormat.UPLOADED }, null, null, null);
						cur.moveToFirst();
						alreadyUploaded = cur.getInt(0) != 0;
						if (!alreadyUploaded) return false;
						cur.deactivate();
						break;
					case SOUND:
						cur = context.getContentResolver().query(binUri, new String[] { SoundSQLFormat.UPLOADED }, null, null, null);
						cur.moveToFirst();
						alreadyUploaded = cur.getInt(0) != 0;
						if (!alreadyUploaded) return false;
						cur.deactivate();
						break;
					case BINARYFILE:
					default:
						// Can't do anything since its not in the DB. Sigh.
						break;
					}
				} catch (Exception x) {
					Log.i(TAG, "Error checking if the binary files have been uploaded: " + x.toString());
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Send the entire completed procedure to the Sana Mobile Dispatch Server 
	 * (MDS). This procedure sends the answer/response pairs and all the binary 
	 * data (sounds, pictures, etc.) to the MDS in a packetized fashion.
	 * 
	 * @param uri uri of procedure in database
	 * @param context current context
	 * @return true if upload was successful, false if not
	 * @throws APIException 
	 */
	public static boolean postProcedureToDjangoServer(Uri uri, Context context) throws APIException{
		return submitCase(uri,context);
	}
	
	/**
	 * Send the entire completed procedure to the Moca Dispatch Server (MDS)
	 * This procedure sends the answer/response pairs and all the binary data (sounds, 
	 * pictures, etc.) to the MDS in a packetized fashion.
	 * 
	 * @param uri uri of procedure in database
	 * @param context current context
	 * @return true if upload was successful, false if not
	 */
	public static boolean submitCase(Uri uri, Context context) throws APIException {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		String mdsURL = preferences.getString(Constants.PREFERENCE_MDS_URL,
				Constants.DEFAULT_DISPATCH_SERVER);
		String phoneIdentifier = preferences.getString("s_phone_name", Constants.PHONE_ID);
		String username = preferences.getString(Constants.PREFERENCE_EMR_USERNAME, Constants.DEFAULT_USERNAME);
		String password = preferences.getString(Constants.PREFERENCE_EMR_PASSWORD, Constants.DEFAULT_PASSWORD);

		MocaAPI api = new MocaAPI(mdsURL, username, password);
		
		Log.i(TAG, "submitCase(" + uri.toString() + ")");
		
		Cursor cursor = context.getContentResolver().query(uri, savedProcedureProjection, null,
				null, null);
		// First get the saved procedure...
		cursor.moveToFirst();
		int savedProcedureId = cursor.getInt(0);
		int procedureId = cursor.getInt(1);
		String answersJson = cursor.getString(2);
		boolean finished = cursor.getInt(3) != 0;
		String savedProcedureGUID = cursor.getString(4);
		boolean savedProcedureUploaded = cursor.getInt(5) != 0;
		cursor.deactivate();

		Uri procedureUri = ContentUris.withAppendedId(Procedures.CONTENT_URI, procedureId);
		Log.i(TAG, "Getting procedure " + procedureUri.toString());
		cursor = context.getContentResolver().query(procedureUri, new String[] { Procedures.TITLE, Procedures.PROCEDURE }, null, null, null);
		cursor.moveToFirst();
		String procedureTitle = cursor.getString(cursor.getColumnIndex(Procedures.TITLE));
		String procedureXml = cursor.getString(cursor.getColumnIndex(Procedures.PROCEDURE));
		cursor.deactivate();

		//Log.i(TAG, "Procedure " + procedureXml);

		if(!finished) {
			Log.i(TAG, "Not finished. Not uploading. (just kidding)" + uri.toString());
			//return false;
		}
		Map<String, Map<String,String>> elementMap = null;
		try {
			Procedure p = Procedure.fromXMLString(procedureXml);
			p.setInstanceUri(uri);

			JSONTokener tokener = new JSONTokener(answersJson);
			JSONObject answersDict = new JSONObject(tokener);

			Map<String,String> answersMap = new HashMap<String,String>();
			Iterator<?> it = answersDict.keys();
			while(it.hasNext()) {
				String key = (String)it.next();
				answersMap.put(key, answersDict.getString(key));
				Log.i(TAG, "onCreate() : answer '" + key + "' : '" + answersDict.getString(key) +"'");
			}
			Log.i(TAG, "onCreate() : restoreAnswers");
			p.restoreAnswers(answersMap);
			elementMap = p.toElementMap();

		} catch (IOException e2) {
			Log.e(TAG, e2.toString());
		} catch (ParserConfigurationException e2) {
			Log.e(TAG, e2.toString());
		} catch (SAXException e2) {
			Log.e(TAG, e2.toString());
		} catch (ProcedureParseException e2) {
			Log.e(TAG, e2.toString());
		} catch (JSONException e) {
			Log.e(TAG, e.toString());
		}

		if(elementMap == null) {
			Log.i(TAG, "Could not read questions and answers from " + uri + ". Not uploading.");
			return false;
		}
		
		// Add in procedureTitle as a fake answer
		Map<String,String> titleMap = new HashMap<String,String>();
		titleMap.put("answer", procedureTitle);
		titleMap.put("id", "procedureTitle");
		titleMap.put("type", "HIDDEN");
		elementMap.put("procedureTitle", titleMap);

		class ElementAnswer {
			public String id;
			public String answer;
			public String type;
			public ElementAnswer(String id, String answer, String type) {
				this.id = id;
				this.answer = answer;
				this.type = type;
			}
		}

		JSONObject jsono = new JSONObject();
		int totalBinaries = 0;
		ArrayList<ElementAnswer> binaries = new ArrayList<ElementAnswer>();
		for(Entry<String,Map<String,String>> e : elementMap.entrySet()) {
			try {
				jsono.put(e.getKey(), new JSONObject(e.getValue()));
			} catch (JSONException e1) {
				Log.e(TAG, "Could not convert map " + e.getValue().toString() + " to JSON");
			}

			String id = e.getKey();
			String type = e.getValue().get("type");
			String answer = e.getValue().get("answer");
			
			if (id == null || type == null || answer == null)
				continue;

			// Find elements that require binary uploads
			if(type.equals(ElementType.PICTURE.toString()) ||
					type.equals(ElementType.BINARYFILE.toString()) ||
					type.equals(ElementType.SOUND.toString())) {
				binaries.add(new ElementAnswer(id, answer, type));
				if(!"".equals(answer)) {
					String[] ids = answer.split(",");
					totalBinaries += ids.length;
				}
			}
		}

		Log.i(TAG, "About to post responses.");

		if(savedProcedureUploaded) {
			Log.i(TAG, "Responses have already been sent to MDS, not posting.");
		} else {
			// upload the question and answer pairs text, without using packetization

			String json = jsono.toString();
			Log.i(TAG, "json string: " + json);

			int tries = 0;
			final int MAX_TRIES = 5;
			while(tries < MAX_TRIES) {
				boolean success = api.transmitCaseResponses(savedProcedureGUID, Integer.toString(0), phoneIdentifier, json);
				if (success) {
					// Mark the procedure text as uploaded in the database
					ContentValues cv = new ContentValues();
					cv.put(Encounters.UPLOADED, true);
					context.getContentResolver().update(uri, cv, null, null);
					Log.i(TAG, "Responses were uploaded successfully.");
					break;
				}
				tries++;
			}

			if(tries == MAX_TRIES) {
				Log.e(TAG, "Could not post responses, bailing.");
				return false;
			}

		}


		Log.i(TAG, "Posted responses, now sending " + totalBinaries + " binaries.");


		String sPacketSize = preferences.getString("s_packet_init_size", Integer.toString(Constants.DEFAULT_INIT_PACKET_SIZE));
		// lookup starting packet size
		int newPacketSize;
		try {
			newPacketSize = Integer.parseInt(sPacketSize);
		} catch (NumberFormatException e) {
			newPacketSize = Constants.DEFAULT_INIT_PACKET_SIZE;
		}
		// adjust from KB to bytes
		newPacketSize *= 1000;

		int totalProgress = 1+totalBinaries;
		int thisProgress = 2;

		// upload each binary file
		for(ElementAnswer e : binaries) {

			if("".equals(e.answer))
				continue;

			String[] ids = e.answer.split(",");

			for(String binaryId : ids) {


				Uri binUri = null;
				ElementType type = ElementType.INVALID;
				try {
					type = ElementType.valueOf(e.type);
				} catch(IllegalArgumentException ex) {

				}

				if (type == ElementType.PICTURE) {
					binUri = ContentUris.withAppendedId(ImageSQLFormat.CONTENT_URI, Long.parseLong(binaryId));	
				} else if (type == ElementType.SOUND) {
					binUri = ContentUris.withAppendedId(SoundSQLFormat.CONTENT_URI, Long.parseLong(binaryId));
				} else if (type == ElementType.BINARYFILE) {
					binUri = Uri.fromFile(new File(e.answer));
					// We can't tell if a BINARYFILE has been uploaded before.
					// Maybe if we grab the mtime/filesize on the file and store
					// it when we upload it.
				}

				try {
					Log.i(TAG, "Uploading " + binUri);
					// reset the new packet size each time to the last successful transmission size
					newPacketSize = transmitBinary(context, api, savedProcedureGUID, e.id, binaryId, type, binUri, newPacketSize);
				} catch (Exception x) {
					Log.i(TAG, "Uploading " + binUri + " failed : " + x.toString());
					return false;
				}
				thisProgress++;
			}
		}
		// TODO Tag entire procedure in db as done transmitting
		return true;   
	}
	
	/**
	 * Sends an entire binary file in a packetized fashion. This method is where the automatic 
	 * ramping packetization takes place.
	 * Does uploading in the background
	 * 
	 * @param c current context
	 * @param savedProcedureId
	 * @param elementId
	 * @param type binary type (ie picture, sound, etc.)
	 * @param binaryUri uri of the file to be transmitted
	 * @param startPacketSize the starting packet size for each chunk; this will be throttled up or down depending on connection strength
	 * @return the last successful chunk transmission size on success so that it can be used for future transmissions as the startPacketSize
	 * @throws Exception on upload failure
	 */
	private static int transmitBinary(Context c, MocaAPI api, String savedProcedureId, String elementId, String binaryGuid, ElementType type, Uri binaryUri, int startPacketSize) throws Exception {
		int packetSize, fileSize;
		ContentValues cv = new ContentValues();

		packetSize = startPacketSize;

		boolean alreadyUploaded = false;
		int currPosition = 0;
		Cursor cur;
		switch(type) {
		case PICTURE:
			cur = c.getContentResolver().query(binaryUri, new String[] { ImageSQLFormat.UPLOADED, ImageSQLFormat.UPLOAD_PROGRESS }, null, null, null);
			cur.moveToFirst();
			alreadyUploaded = cur.getInt(0) != 0;
			currPosition = cur.getInt(1);
			cur.deactivate();
			break;
		case SOUND:
			cur = c.getContentResolver().query(binaryUri, new String[] { SoundSQLFormat.UPLOADED, SoundSQLFormat.UPLOAD_PROGRESS }, null, null, null);
			cur.moveToFirst();
			alreadyUploaded = cur.getInt(0) != 0;
			currPosition = cur.getInt(1);
			cur.deactivate();
			break;
		case BINARYFILE:
		default:
			// Can't do anything since its not in the DB. Sigh.
			break;

		}

		if(alreadyUploaded) {
			Log.i(TAG, binaryUri + " was already uploaded. Skipping.");
			return startPacketSize;
		}

		InputStream is = c.getContentResolver().openInputStream(binaryUri);
		fileSize = is.available();

		// Skip forward by the progress we've made previously.
		is.skip(currPosition);

		int bytesRemaining = fileSize - currPosition;
		Log.i(TAG, "transmitBinary uploading " + binaryUri + " " + bytesRemaining + " total bytes remaining. Starting at " + packetSize + " packet size");

		// reference packet rate byte/msec
		double basePacketRate = 0.0;
		while(bytesRemaining > 0) {
			// get starting time of packet transmission
			long transmitStartTime = new Date().getTime();
			// if transmission rate is acceptable (comparison between currPacketRate and basePacketRate)
			boolean efficient = false;

			int bytesToRead = Math.min(packetSize, bytesRemaining);
			byte[] chunk = new byte[bytesToRead];
			int bytesRead = is.read(chunk, 0, bytesToRead);

			boolean success = false;
			while(!success) {
				Log.i(TAG, "Trying to upload " + bytesRead + " bytes for " + savedProcedureId + ":" + elementId + ".");
				success = postBinary(c, api, savedProcedureId, elementId, binaryGuid, type, fileSize, currPosition, currPosition+bytesRead, chunk);

				efficient = false;
				// new rate is compared to 80% of previous rate
				basePacketRate *= 0.8;
				if(success) {
					long transmitEndTime = new Date().getTime();
					// get new packet rate
					double currPacketRate = (double)packetSize/(double)(transmitEndTime-transmitStartTime);
					Log.i(TAG, "packet rate = (current) " + currPacketRate + ", (base) " + basePacketRate);
					if(currPacketRate > basePacketRate) {
						basePacketRate = currPacketRate;
						efficient = true;
					}
				}

				if(efficient) {
					packetSize *= 2;
					Log.i(TAG, "Shifting packet size *2 =" + packetSize);
				} else {
					packetSize /= 2;
					Log.i(TAG, "Shifting packet size /2 =" + packetSize);
				}

				if(packetSize < Constants.MIN_PACKET_SIZE * 1000) {
					// TODO(rryan) : fail at some point
					is.close();
					throw new IOException("Could not upload " + binaryUri +". failed after " + (fileSize-bytesRemaining) + " bytes.");
				}
			}

			bytesRemaining -= bytesRead;
			currPosition += bytesRead;

			// write current progress to database
			cv.clear();
			switch(type) {
			case PICTURE:
				cv.put(ImageSQLFormat.UPLOAD_PROGRESS, currPosition);
				c.getContentResolver().update(binaryUri, cv, null, null);
				break;
			case SOUND:
				cv.put(SoundSQLFormat.UPLOAD_PROGRESS, currPosition);
				c.getContentResolver().update(binaryUri, cv, null, null);
				break;
			}

		}

		// Mark file as uploaded in the database
		cv.clear();
		switch(type) {
		case PICTURE:
			cv.put(ImageSQLFormat.UPLOADED, currPosition);
			c.getContentResolver().update(binaryUri, cv, null, null);
			break;
		case SOUND:
			cv.put(SoundSQLFormat.UPLOADED, currPosition);
			c.getContentResolver().update(binaryUri, cv, null, null);
			break;
		}

		is.close();

		return packetSize;
	}

	public static boolean validateCredentials(Context c) throws APIException {
		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(c);
		String username = preferences.getString(
				Constants.PREFERENCE_EMR_USERNAME, Constants.DEFAULT_USERNAME);
		String password = preferences.getString(
				Constants.PREFERENCE_EMR_PASSWORD, Constants.DEFAULT_PASSWORD);
		String mdsURL = preferences.getString(Constants.PREFERENCE_MDS_URL,
				Constants.DEFAULT_DISPATCH_SERVER);
		boolean secure = preferences.getBoolean(Constants.PREFERENCE_SECURE_TRANSMISSION,
				false);
		Log.i(TAG, "validateCredentials()");
		MocaAPI api = new MocaAPI(mdsURL, username, password);
		boolean credentialsValid = api.validateCredentials();
		Log.d(TAG, "The user's credentials are " + (credentialsValid ? "valid" : "invalid"));
		return credentialsValid;
	}

	// Sync patient database on phone with MRS
	public static boolean updatePatientDatabase(Context c, ContentResolver cr) throws APIException {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(c);
		String password = preferences.getString(Constants.PREFERENCE_EMR_PASSWORD, Constants.DEFAULT_PASSWORD);
		String username = preferences.getString(Constants.PREFERENCE_EMR_USERNAME, Constants.DEFAULT_USERNAME);
		String mdsURL = preferences.getString(Constants.PREFERENCE_MDS_URL,
				Constants.DEFAULT_DISPATCH_SERVER);
		
		Log.i(TAG, "getPatientInformation()");
		MocaAPI api = new MocaAPI(mdsURL, username, password);
		String allPatients = api.getAllPatients();
		Log.d(TAG, "Patient download response looks like this: " + allPatients);
		
		MocaUtil.clearPatientData(c);
		PatientDatabase.importPatientData(cr, allPatients);
		
		return true;
	}
	
	public static String getUserInfo(Context c, String userid) throws APIException{
		return getPatientInformation(c,userid);
	}
	
	public static String getPatientInformation(Context c, String patientIdentifier) throws APIException {
		String info = null;
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(c);
		String password = preferences.getString(Constants.PREFERENCE_EMR_PASSWORD, Constants.DEFAULT_PASSWORD);
		String username = preferences.getString(Constants.PREFERENCE_EMR_USERNAME, Constants.DEFAULT_USERNAME);
		String mdsURL = preferences.getString(Constants.PREFERENCE_MDS_URL,
				Constants.DEFAULT_DISPATCH_SERVER);
		
		Log.i(TAG, "getPatientInfo(" + patientIdentifier + ")");
		MocaAPI api = new MocaAPI(mdsURL, username, password);
		String patientInfo = api.getPatientInformation(patientIdentifier);
		Log.d(TAG, "Request for patient info returned: " + patientInfo);
		return patientInfo;
	}
	
	public static boolean isNewPatientIdTaken(Context c, String patientIdentifier) throws APIException {
		Log.i(TAG, "isNewPatientIdTaken(" + patientIdentifier + ")");
		
		String userInfo = getPatientInformation(c, patientIdentifier);
		
		if (userInfo == null || "".equals(userInfo)) {
			Log.i(TAG, patientIdentifier + " not in use.");
			return true;
		}
		Log.i(TAG, patientIdentifier + " is in use.");
		return false;
	}
	
	public static List<ProcedureInfo> getAvailableProcedures(Context c) throws APIException {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(c);
		String password = preferences.getString(Constants.PREFERENCE_EMR_PASSWORD, Constants.DEFAULT_PASSWORD);
		String username = preferences.getString(Constants.PREFERENCE_EMR_USERNAME, Constants.DEFAULT_USERNAME);
		String mdsURL = preferences.getString(Constants.PREFERENCE_MDS_URL,
				Constants.DEFAULT_DISPATCH_SERVER);
		
		Log.i(TAG, "getAvailableProcedures()");
		MocaAPI api = new MocaAPI(mdsURL, username, password);
		List<ProcedureInfo> procedures = api.getAvailableProcedureList();
		Log.d(TAG, "Request for available procedures returned: " + procedures);
		return procedures;
	}
	
	public static String getProcedure(Context c, int procedureId) throws APIException {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(c);
		String password = preferences.getString(Constants.PREFERENCE_EMR_PASSWORD, Constants.DEFAULT_PASSWORD);
		String username = preferences.getString(Constants.PREFERENCE_EMR_USERNAME, Constants.DEFAULT_USERNAME);
		String mdsURL = preferences.getString(Constants.PREFERENCE_MDS_URL,
				Constants.DEFAULT_DISPATCH_SERVER);
		
		Log.i(TAG, "getProcedure(" + procedureId + ")");
		MocaAPI api = new MocaAPI(mdsURL, username, password);
		String procedureData = api.getProcedure(procedureId);
		Log.d(TAG, "Request for procedure text returned: " + procedureData);
		return procedureData;
	}
	

	/**
	 * Sends a list of events to the dispatch server
	 * 
	 * @param c the application Context
	 * @param eventsList a list of events
	 * @return true if successfully sent
	 */
	public static boolean submitEvents(Context c, List<Event> eventsList) {
		return false;
	}
	
}
