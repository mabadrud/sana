package org.sana.android.db;

import java.util.HashMap;

import org.sana.android.db.DispatchableContract.DatabaseHelper;
import org.sana.android.db.DispatchableContract.Subjects;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 * Used to store data for patient authentication, birthdates are stored as 
 * integers with the year then month then day appended together, i.e. 19880922 
 * corresponds to September 22, 1988
 * 
 * @author Sana Development Team
 */
public class PatientProvider extends ContentProvider {

	private static final String TAG = "PatientProvider";

	private static final String PATIENT_TABLE_NAME = "patients";

	private static final int PATIENTS = 1;
	private static final int PATIENTS_ID = 2;

	private DatabaseHelper mOpenHelper;
	private static final UriMatcher sUriMatcher;
	private static HashMap<String,String> sPatientProjectionMap;

    /** {@inheritDoc} */
	@Override
	public boolean onCreate() {
		Log.i(TAG, "onCreate()");
		mOpenHelper = new DatabaseHelper(getContext());
		return true;
	}

    /** {@inheritDoc} */
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		Log.i(TAG, "query() uri="+uri.toString() + " projection=" 
				+ TextUtils.join(",",projection));
		
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(PATIENT_TABLE_NAME);

		switch(sUriMatcher.match(uri)) {
		case PATIENTS:    
			break;
		case PATIENTS_ID:
			qb.appendWhere(Subjects._ID + "=" 
					+ uri.getPathSegments().get(1));
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		String orderBy;
		if(TextUtils.isEmpty(sortOrder)) {
			orderBy = Subjects.DEFAULT_SORT_ORDER;
		} else {
			orderBy = sortOrder;
		}
		
		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		Cursor c = qb.query(db, projection, selection, selectionArgs, null, 
				null, sortOrder);
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

    /** {@inheritDoc} */
	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count = 0; 

		switch(sUriMatcher.match(uri)) {
		case PATIENTS:
			count = db.update(PATIENT_TABLE_NAME, values, selection, 
					selectionArgs);
			break;
		case PATIENTS_ID:
			String patientId = uri.getPathSegments().get(1);
			count = db.update(PATIENT_TABLE_NAME, values, Subjects._ID 
					+ "=" + patientId + (!TextUtils.isEmpty(selection) 
							? " AND (" + selection + ")" : ""), selectionArgs);
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

    /** {@inheritDoc} */
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count;
		switch (sUriMatcher.match(uri)) {
		case PATIENTS:
			count = db.delete(PATIENT_TABLE_NAME, selection, selectionArgs);
			break;
		case PATIENTS_ID:
			String patientId = uri.getPathSegments().get(1); 
			count = db.delete(PATIENT_TABLE_NAME, Subjects._ID + "=" 
					+ patientId + (!TextUtils.isEmpty(selection) ? " AND (" 
							+ selection + ")" : ""), selectionArgs);
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

    /** {@inheritDoc} */
	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
		Log.i(TAG,"starting insert method");
		if (sUriMatcher.match(uri) != PATIENTS) {
			Log.i(TAG, "Throwing IllegalArgumentException");
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		ContentValues values;
		if(initialValues != null) {
			Log.i(TAG,"do we get to this point?");
			values = new ContentValues(initialValues);
		} else {
			values = new ContentValues();
		}

		Long now = Long.valueOf(System.currentTimeMillis());

		if(values.containsKey(Subjects.GIVEN_NAME) == false) {
			values.put(Subjects.GIVEN_NAME, "");
		}
		
		if(values.containsKey(Subjects.FAMILY_NAME) == false) {
			values.put(Subjects.FAMILY_NAME, "");
		}

		if(values.containsKey(Subjects.DATE_OF_BIRTH) == false) {
			values.put(Subjects.DATE_OF_BIRTH, "");
		}

		if(values.containsKey(Subjects.UUID) == false) {
			values.put(Subjects.UUID, now);
		}
		
		if(values.containsKey(Subjects.GENDER) == false) {
			values.put(Subjects.GENDER, "");
		}
 
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
 
		try {
			long rowId = db.insertOrThrow(PATIENT_TABLE_NAME, 
					Subjects.GIVEN_NAME, values);
			if(rowId > 0) {
				Uri patientUri = ContentUris.withAppendedId(
						Subjects.CONTENT_URI, rowId);
				getContext().getContentResolver().notifyChange(patientUri, 
						null);
				return patientUri;
			}
		} catch (Exception e) {
			Log.i(TAG,e.getClass().toString());
			Log.i(TAG, e.getMessage());
		}
 
		throw new SQLException("Failed to insert row into " + uri);
	}

    /** {@inheritDoc} */
	@Override
	public String getType(Uri uri) {
		Log.i(TAG, "getType(uri="+uri.toString()+")");
		switch(sUriMatcher.match(uri)) {
		case PATIENTS:
			return Subjects.CONTENT_TYPE;
		case PATIENTS_ID:
			return Subjects.CONTENT_ITEM_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

    /**
     * Creates the table.
     * @param db the database to create the table in.
     */
	public static void onCreateDatabase(SQLiteDatabase db) {
		Log.i(TAG, "Creating Patient Data Table");
		db.execSQL("CREATE TABLE " + PATIENT_TABLE_NAME + " ("
				+ Subjects._ID + " INTEGER PRIMARY KEY,"
				+ Subjects.UUID + " TEXT,"
				+ Subjects.GIVEN_NAME + " TEXT,"
				+ Subjects.FAMILY_NAME + " TEXT,"
				+ Subjects.GENDER + " TEXT,"
				+ Subjects.DATE_OF_BIRTH + " INTEGER"
				+ ");");
		Log.i(TAG, "Finished Creating Patient Data TAble");
		
	}

    /**
     * Updates this providers table
     * @param db the db to update in 
     * @param oldVersion the current db version
     * @param newVersion the new db version
     */
	public static void onUpgradeDatabase(SQLiteDatabase db, int oldVersion, 
			int newVersion) {
		Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
				+ newVersion);
        if (oldVersion == 1 && newVersion == 2) {
        	// Do nothing
        }
	}

	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(DispatchableContract.PATIENT_AUTHORITY, "patients", PATIENTS);
		sUriMatcher.addURI(DispatchableContract.PATIENT_AUTHORITY, "patients/#", PATIENTS_ID);

		sPatientProjectionMap = new HashMap<String, String>();
		sPatientProjectionMap.put(Subjects._ID, Subjects._ID);
		sPatientProjectionMap.put(Subjects.UUID, Subjects.UUID);
		sPatientProjectionMap.put(Subjects.GIVEN_NAME, Subjects.GIVEN_NAME);
		sPatientProjectionMap.put(Subjects.FAMILY_NAME, Subjects.FAMILY_NAME);
		sPatientProjectionMap.put(Subjects.DATE_OF_BIRTH, Subjects.DATE_OF_BIRTH);
		sPatientProjectionMap.put(Subjects.GENDER, Subjects.GENDER);
	}
}
