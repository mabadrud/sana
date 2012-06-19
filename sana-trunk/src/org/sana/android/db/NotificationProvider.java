package org.sana.android.db;

import java.util.HashMap;

import org.sana.android.db.DispatchableContract.DatabaseHelper;
import org.sana.android.db.DispatchableContract.Notifications;

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
 * Content provider for notifications.
 * 
 * @author Sana Development Team
 *
 */
public class NotificationProvider extends ContentProvider {
    private static final String TAG = "NotificationProvider";
 
    private static final String NOTIFICATION_TABLE_NAME = "notifications";
    
    private static final int NOTIFICATIONS = 1;
    private static final int NOTIFICATION_ID = 2;
    
    private DatabaseHelper mOpenHelper;
    private static final UriMatcher sUriMatcher;
    private static HashMap<String,String> sNotificationProjectionMap;

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
        qb.setTables(NOTIFICATION_TABLE_NAME);
        
        switch(sUriMatcher.match(uri)) {
        case NOTIFICATIONS:    
            break;
        case NOTIFICATION_ID:
            qb.appendWhere(Notifications._ID + "=" 
            		+ uri.getPathSegments().get(1));
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
        String orderBy;
        if(TextUtils.isEmpty(sortOrder)) {
            orderBy = Notifications.DEFAULT_SORT_ORDER;
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
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = 0; 
        
        switch(sUriMatcher.match(uri)) {
        case NOTIFICATIONS:
            count = db.update(NOTIFICATION_TABLE_NAME, values, selection, 
            		selectionArgs);
            break;
            
        case NOTIFICATION_ID:
            String procedureId = uri.getPathSegments().get(1);
            count = db.update(NOTIFICATION_TABLE_NAME, values, 
            		Notifications._ID + "=" + procedureId 
            		+ (!TextUtils.isEmpty(selection) ? " AND (" + selection 
            				+ ")" : ""), selectionArgs);
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
        case NOTIFICATIONS:
            count = db.delete(NOTIFICATION_TABLE_NAME, selection,selectionArgs);
            break;
        case NOTIFICATION_ID:
            String imageId = uri.getPathSegments().get(1); 
            count = db.delete(NOTIFICATION_TABLE_NAME, Notifications._ID
            		+ "=" + imageId + (!TextUtils.isEmpty(selection) 
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
    public Uri insert(Uri uri, ContentValues initialValues) {
        if (sUriMatcher.match(uri) != NOTIFICATIONS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
        ContentValues values;
        if(initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }
        
        Long now = Long.valueOf(System.currentTimeMillis());
        
        if(values.containsKey(Notifications.CREATED_DATE) == false) {
            values.put(Notifications.CREATED_DATE, now);
        }
        
        if(values.containsKey(Notifications.MODIFIED_DATE) == false) {
            values.put(Notifications.MODIFIED_DATE, now);
        }
        
        if(values.containsKey(Notifications.NOTIFICATION_GUID) ==false){
            values.put(Notifications.NOTIFICATION_GUID, "");
        }
        
        if(values.containsKey(Notifications.PATIENT_ID) == false) {
            values.put(Notifications.PATIENT_ID, "");
        }
        
        if(values.containsKey(Notifications.PROCEDURE_ID) == false) {
            values.put(Notifications.PROCEDURE_ID, "");
        }
        
        if(values.containsKey(Notifications.MESSAGE) == false) {
            values.put(Notifications.MESSAGE, "");
        }
        
        if(values.containsKey(Notifications.FULL_MESSAGE) == false) {
            values.put(Notifications.FULL_MESSAGE, "");
        }
        
        if(values.containsKey(Notifications.DOWNLOADED) == false) {
            values.put(Notifications.DOWNLOADED, 0);
        }
        
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(NOTIFICATION_TABLE_NAME, 
        		Notifications.PROCEDURE_ID, values);
        if(rowId > 0) {
            Uri notificationUri = ContentUris.withAppendedId(
            		Notifications.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(
            		notificationUri,null);
            return notificationUri;
        }
        
        throw new SQLException("Failed to insert row into " + uri);
    }

    /** {@inheritDoc} */
    @Override
    public String getType(Uri uri) {
        Log.i(TAG, "getType(uri="+uri.toString()+")");
        switch(sUriMatcher.match(uri)) {
        case NOTIFICATIONS:
            return Notifications.CONTENT_TYPE;
        case NOTIFICATION_ID:
            return Notifications.CONTENT_ITEM_TYPE;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Creates the table.
     * @param db the database to create the table in.
     */
    public static void onCreateDatabase(SQLiteDatabase db) {
        Log.i(TAG, "Creating Image Table");
        db.execSQL("CREATE TABLE " + NOTIFICATION_TABLE_NAME + " ("
                + Notifications._ID + " INTEGER PRIMARY KEY,"
                + Notifications.NOTIFICATION_GUID + " TEXT,"
                + Notifications.PATIENT_ID + " TEXT,"
                + Notifications.PROCEDURE_ID + " TEXT,"
                + Notifications.MESSAGE + " TEXT,"
                + Notifications.FULL_MESSAGE + " TEXT,"
                + Notifications.DOWNLOADED + " INTEGER,"
                + Notifications.CREATED_DATE + " INTEGER,"
                + Notifications.MODIFIED_DATE + " INTEGER"
                + ");");
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
        sUriMatcher.addURI(DispatchableContract.NOTIFICATION_AUTHORITY, "notifications", NOTIFICATIONS);
        sUriMatcher.addURI(DispatchableContract.NOTIFICATION_AUTHORITY, "notifications/#", NOTIFICATION_ID);
        
        sNotificationProjectionMap = new HashMap<String, String>();
        sNotificationProjectionMap.put(Notifications._ID, Notifications._ID);
        sNotificationProjectionMap.put(Notifications.NOTIFICATION_GUID, Notifications.NOTIFICATION_GUID);
        sNotificationProjectionMap.put(Notifications.PATIENT_ID, Notifications.PATIENT_ID);
        sNotificationProjectionMap.put(Notifications.PROCEDURE_ID, Notifications.PROCEDURE_ID);
        sNotificationProjectionMap.put(Notifications.MESSAGE, Notifications.MESSAGE);
        sNotificationProjectionMap.put(Notifications.FULL_MESSAGE, Notifications.FULL_MESSAGE);
        sNotificationProjectionMap.put(Notifications.DOWNLOADED, Notifications.DOWNLOADED);
        sNotificationProjectionMap.put(Notifications.CREATED_DATE, Notifications.CREATED_DATE);
        sNotificationProjectionMap.put(Notifications.MODIFIED_DATE, Notifications.MODIFIED_DATE);
    }
    
    
    
}
