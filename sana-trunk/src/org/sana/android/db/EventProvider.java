package org.sana.android.db;

import java.util.HashMap;

import org.sana.android.db.DispatchableContract.DatabaseHelper;
import org.sana.android.db.DispatchableContract.Events;

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
 * Content provider for events.
 * 
 * @author Sana Development Team
 *
 */
public class EventProvider extends ContentProvider {
	private static final String TAG = EventProvider.class.getSimpleName();
	
	private static final String EVENTS_TABLE_NAME = "events";
    
    private static final int EVENTS = 1;
    private static final int EVENT_ID = 2;
    
    private DatabaseHelper mOpenHelper;
    private static final UriMatcher sUriMatcher;
    private static HashMap<String,String> sCounterProjectionMap;

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
			String[] selectionArgs, String sortOrder) 
	{
		Log.i(TAG, "query() uri="+uri.toString() + " projection=" 
				+ TextUtils.join(",",projection));
        
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(EVENTS_TABLE_NAME);
        
        switch(sUriMatcher.match(uri)) {
        case EVENTS:    
            break;
        case EVENT_ID:
            qb.appendWhere(Events._ID + "=" 
            		+ uri.getPathSegments().get(1));
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
        String orderBy;
        if(TextUtils.isEmpty(sortOrder)) {
            orderBy = Events.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }
        
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, 
        		null, orderBy);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
	}

    /** {@inheritDoc} */
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		Log.i(TAG, "delete: " + uri);
    	
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case EVENTS:
            count = db.delete(EVENTS_TABLE_NAME, selection, selectionArgs);
            break;
        case EVENT_ID:
        	String eventId = uri.getPathSegments().get(1);
            count = db.delete(EVENTS_TABLE_NAME, Events._ID + "=" 
            		+ eventId + (!TextUtils.isEmpty(selection) ? " AND (" 
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
	public String getType(Uri uri) {
		Log.i(TAG, "getType(uri="+uri.toString()+")");
        switch(sUriMatcher.match(uri)) {
        case EVENTS:
            return Events.CONTENT_TYPE;
        case EVENT_ID:
            return Events.CONTENT_ITEM_TYPE;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
	}
	
	private void insertDefault(ContentValues values, String key, 
			boolean defaultValue) 
	{
		if (!values.containsKey(key)) {
			values.put(key, defaultValue);
		}
	}
	
	private void insertDefault(ContentValues values, String key, 
			Long defaultValue) 
	{
		if (!values.containsKey(key)) {
			values.put(key, defaultValue);
		}
	}
	
	private void insertDefault(ContentValues values, String key, 
			String defaultValue) 
	{
		if (!values.containsKey(key)) {
			values.put(key, defaultValue);
		}
	}

    /** {@inheritDoc} */
	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
		if (sUriMatcher.match(uri) != EVENTS) {
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	        
		
		ContentValues values;
        if(initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }
        
        Long now = Long.valueOf(System.currentTimeMillis());
        
        insertDefault(values, Events.EVENT_TYPE, "");
        insertDefault(values, Events.EVENT_VALUE, "");
        insertDefault(values, Events.CREATED_DATE, now);
        insertDefault(values, Events.MODIFIED_DATE, now);
        insertDefault(values, Events.UPLOADED, false);
        insertDefault(values, Events.PATIENT_REFERENCE, "");
        insertDefault(values, Events.ENCOUNTER_REFERENCE, "");
        insertDefault(values, Events.USER_REFERENCE, "");

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(EVENTS_TABLE_NAME, null, values);
        
        if(rowId > 0) {
            Uri eventUri = ContentUris.withAppendedId(
            		Events.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(eventUri, null);
            return eventUri;
        }
       
        throw new SQLException("Failed to insert row into " + uri);
	}

    /** {@inheritDoc} */
	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = 0; 
        
        switch(sUriMatcher.match(uri)) {
        case EVENTS:
            count = db.update(EVENTS_TABLE_NAME, values, selection, 
            		selectionArgs);
            break;
            
        case EVENT_ID:
            String procedureId = uri.getPathSegments().get(1);
            count = db.update(EVENTS_TABLE_NAME, values, Events._ID 
            		+ "=" + procedureId + (!TextUtils.isEmpty(selection) 
            				? " AND (" + selection + ")" : ""), selectionArgs);
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
	}

    /**
     * Creates the table.
     * @param db the database to create the table in.
     */
	public static void onCreateDatabase(SQLiteDatabase db) {
		Log.i(TAG, "Creating Events Table");
		db.execSQL("CREATE TABLE " + EVENTS_TABLE_NAME + " ("
				+ Events._ID + " INTEGER PRIMARY KEY,"
				+ Events.EVENT_TYPE + " TEXT, "
				+ Events.EVENT_VALUE + " TEXT, " 
				+ Events.ENCOUNTER_REFERENCE + " TEXT, "
				+ Events.PATIENT_REFERENCE + " TEXT, "
				+ Events.USER_REFERENCE + " TEXT, "
				+ Events.UPLOADED + " INTEGER, "
                + Events.CREATED_DATE + " INTEGER,"
                + Events.MODIFIED_DATE + " INTEGER"
				+ ");");
	}

    /**
     * Updates this providers table
     * @param db the db to update in 
     * @param oldVersion the current db version
     * @param newVersion the new db version
     */
	public static void onUpgradeDatabase(SQLiteDatabase db, int oldVersion, 
			int newVersion) 
	{
		Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
				+ newVersion);
		
		if (oldVersion == 1 && newVersion == 2) {
			// This table is created in version 2.
			onCreateDatabase(db);
		}
	}
	
	static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(DispatchableContract.EVENT_AUTHORITY, "events", EVENTS);
        sUriMatcher.addURI(DispatchableContract.EVENT_AUTHORITY, "events/#", EVENT_ID);
        
        sCounterProjectionMap = new HashMap<String, String>();
        sCounterProjectionMap.put(Events._ID, Events._ID);
        sCounterProjectionMap.put(Events.EVENT_TYPE, Events.EVENT_TYPE);
        sCounterProjectionMap.put(Events.EVENT_VALUE, Events.EVENT_VALUE);
        sCounterProjectionMap.put(Events.ENCOUNTER_REFERENCE, Events.ENCOUNTER_REFERENCE);
        sCounterProjectionMap.put(Events.PATIENT_REFERENCE, Events.PATIENT_REFERENCE);
        sCounterProjectionMap.put(Events.USER_REFERENCE, Events.USER_REFERENCE);
        sCounterProjectionMap.put(Events.MODIFIED_DATE, Events.MODIFIED_DATE);
        sCounterProjectionMap.put(Events.CREATED_DATE, Events.CREATED_DATE);
    }
    
	
	
}
