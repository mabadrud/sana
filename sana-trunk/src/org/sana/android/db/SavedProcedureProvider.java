package org.sana.android.db;

import java.util.ArrayList;
import java.util.HashMap;

import org.sana.android.db.DispatchableContract.BinarySQLFormat;
import org.sana.android.db.DispatchableContract.DatabaseHelper;
import org.sana.android.db.DispatchableContract.ImageSQLFormat;
import org.sana.android.db.DispatchableContract.Encounters;
import org.sana.android.db.DispatchableContract.SoundSQLFormat;
import org.sana.android.util.BackgroundUploader;
import org.sana.android.util.MocaUtil;

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

public class SavedProcedureProvider extends ContentProvider {
	
    private static final String TAG = SavedProcedureProvider.class.toString();

    private static final String SAVED_PROCEDURE_TABLE_NAME = "saved_procedures";
    
    private static final int SAVED_PROCEDURES = 1;
    private static final int SAVED_PROCEDURE_ID = 2;
    
    private DatabaseHelper mOpenHelper;
    private static final UriMatcher sUriMatcher;
    private static HashMap<String,String> sSavedProcedureProjectionMap;
    
    @Override
    public boolean onCreate() {
        Log.i(TAG, "onCreate()");
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }
    
    private void deleteRelated(String savedProcedureId) {
		getContext().getContentResolver().delete(ImageSQLFormat.CONTENT_URI,
				ImageSQLFormat.SAVED_PROCEDURE_ID + " = ?",
				new String[] { savedProcedureId });
		getContext().getContentResolver().delete(SoundSQLFormat.CONTENT_URI,
				SoundSQLFormat.SAVED_PROCEDURE_ID + " = ?",
				new String[] { savedProcedureId });
		// TODO notifications too?
	}

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        Log.d(TAG, "query() uri="+uri.toString() + " projection=" + TextUtils.join(",",projection));
        
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(SAVED_PROCEDURE_TABLE_NAME);
        
        switch(sUriMatcher.match(uri)) {
        case SAVED_PROCEDURES:    
            break;
        case SAVED_PROCEDURE_ID:
            qb.appendWhere(Encounters._ID + "=" + uri.getPathSegments().get(1));
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
        String orderBy;
        if(TextUtils.isEmpty(sortOrder)) {
            orderBy = Encounters.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }
        
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = 0; 
        
        switch(sUriMatcher.match(uri)) {
        case SAVED_PROCEDURES:
            count = db.update(SAVED_PROCEDURE_TABLE_NAME, values, selection, selectionArgs);
            break;
            
        case SAVED_PROCEDURE_ID:
            String procedureId = uri.getPathSegments().get(1);
            count = db.update(SAVED_PROCEDURE_TABLE_NAME, values, Encounters._ID + "=" + procedureId + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ")" : ""), selectionArgs);
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
    	Log.i(TAG, "delete: " + uri);
    	
    	//If its in the queue, remove it
    	if (BackgroundUploader.isInQueue(uri)) BackgroundUploader.removeFromQueue(uri);
    	
    	
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case SAVED_PROCEDURES:
        	
        	Cursor c = query(Encounters.CONTENT_URI, new String[] { Encounters._ID }, selection, selectionArgs, null);
        	ArrayList<String> idList = new ArrayList<String>(c.getCount());
        	if(c.moveToFirst()) {
        		while(!c.isAfterLast()) {
        			String id = c.getString(c.getColumnIndex(Encounters._ID));
        			idList.add(id);
        			c.moveToNext();
        		}
        	}
        	c.deactivate();
        	
            count = db.delete(SAVED_PROCEDURE_TABLE_NAME, selection, selectionArgs);

            // Do this after so that SavedProcedures remain consistent, while everything else does not. 
            for(String id : idList) {
            	deleteRelated(id);
            }
            
            break;
        case SAVED_PROCEDURE_ID:
            String procedureId = uri.getPathSegments().get(1);
            count = db.delete(SAVED_PROCEDURE_TABLE_NAME, Encounters._ID + "=" + procedureId + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ")" : ""), selectionArgs);

            // Do this after so that SavedProcedures remain consistent, while everything else does not. 
            deleteRelated(procedureId);
            
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        if (sUriMatcher.match(uri) != SAVED_PROCEDURES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
        ContentValues values;
        if(initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }
        
        Long now = Long.valueOf(System.currentTimeMillis());
        
        if(values.containsKey(Encounters.CREATED_DATE) == false) {
            values.put(Encounters.CREATED_DATE, now);
        }
        
        if(values.containsKey(Encounters.MODIFIED_DATE) == false) {
            values.put(Encounters.MODIFIED_DATE, now);
        }
        
        if(values.containsKey(Encounters.UUID) == false) {
        	values.put(Encounters.UUID, MocaUtil.randomString("SP", 20));
        }
        
        if(values.containsKey(Encounters.PROCEDURE_ID) == false) {
            values.put(Encounters.PROCEDURE_ID, -1);
        }
        
        if(values.containsKey(Encounters.PROCEDURE_STATE) == false) {
            values.put(Encounters.PROCEDURE_STATE, "");
        }
        
        if(values.containsKey(Encounters.FINISHED) == false) {
            values.put(Encounters.FINISHED, false);
        }
        
        if(values.containsKey(Encounters.UPLOADED) == false) {
            values.put(Encounters.UPLOADED, false);
        }
        
        if(values.containsKey(Encounters.UPLOAD_STATUS) == false) {
            values.put(Encounters.UPLOAD_STATUS, -1);
        }

		if (values.containsKey(Encounters.UPLOAD_QUEUE) == false) {
			values.put(Encounters.UPLOAD_QUEUE, -1);
			if (values.containsKey(Encounters.SUBJECT) == false) {
				values.put(Encounters.SUBJECT,
						Uri.EMPTY.toString());
			}
			if (values.containsKey(Encounters.OBSERVER) == false) {
				values.put(Encounters.OBSERVER,
						Uri.EMPTY.toString());
			}
			if (values.containsKey(Encounters.PROCEDURE) == false) {
				values.put(Encounters.PROCEDURE,
						Uri.EMPTY.toString());
			}
		}

		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		long rowId = db.insert(SAVED_PROCEDURE_TABLE_NAME,
				Encounters.PROCEDURE_STATE, values);
		if (rowId > 0) {
			Uri savedProcedureUri = ContentUris.withAppendedId(
					Encounters.CONTENT_URI, rowId);
			getContext().getContentResolver().notifyChange(savedProcedureUri,
					null);
			return savedProcedureUri;
		}

		throw new SQLException("Failed to insert row into " + uri);
	}

	@Override
	public String getType(Uri uri) {
		Log.i(TAG, "getType(uri=" + uri.toString() + ")");
		switch (sUriMatcher.match(uri)) {
		case SAVED_PROCEDURES:
			return Encounters.CONTENT_TYPE;
		case SAVED_PROCEDURE_ID:
			return Encounters.CONTENT_ITEM_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	public static void onCreateDatabase(SQLiteDatabase db) {
        Log.i(TAG, "Creating Saved Procedure Table");
        db.execSQL("CREATE TABLE " + SAVED_PROCEDURE_TABLE_NAME + " ("
                + Encounters._ID + " INTEGER PRIMARY KEY,"
                + Encounters.UUID + " TEXT,"
                + Encounters.PROCEDURE_ID + " INTEGER,"
                + Encounters.PROCEDURE_STATE + " TEXT,"
                + Encounters.FINISHED + " INTEGER,"
                + Encounters.UPLOADED + " INTEGER,"
                + Encounters.UPLOAD_STATUS + " TEXT,"
                + Encounters.UPLOAD_QUEUE + " TEXT,"
                + Encounters.CREATED_DATE + " INTEGER,"
                + Encounters.MODIFIED_DATE + " INTEGER,"
                + Encounters.SUBJECT + " TEXT,"
                + Encounters.OBSERVER + " TEXT,"
                + Encounters.PROCEDURE + " TEXT"
                + ");");
    }
    
    public static void onUpgradeDatabase(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data");
        if (oldVersion <= 2) {
            Log.w(TAG, "onUpgradeDatabase(): Performing Upgrade");
        	String format = "ALTER TABLE %s ADD COLUMN %s TEXT;";
        	// Columns to hold uri references to:
        	//   subject, observer, and procedure
        	db.execSQL(String.format(format, SAVED_PROCEDURE_TABLE_NAME,
        				Encounters.SUBJECT));
        	db.execSQL(String.format(format, SAVED_PROCEDURE_TABLE_NAME,
    				Encounters.OBSERVER));
        	db.execSQL(String.format(format, SAVED_PROCEDURE_TABLE_NAME,
    				Encounters.PROCEDURE));
        }
    }


    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(DispatchableContract.SAVED_PROCEDURE_AUTHORITY, "savedProcedures", SAVED_PROCEDURES);
        sUriMatcher.addURI(DispatchableContract.SAVED_PROCEDURE_AUTHORITY, "savedProcedures/#", SAVED_PROCEDURE_ID);
        
        sSavedProcedureProjectionMap = new HashMap<String, String>();
        sSavedProcedureProjectionMap.put(Encounters._ID, Encounters._ID);
        sSavedProcedureProjectionMap.put(Encounters.PROCEDURE_ID, Encounters.PROCEDURE_ID);
        sSavedProcedureProjectionMap.put(Encounters.UUID, Encounters.UUID);
        sSavedProcedureProjectionMap.put(Encounters.PROCEDURE_STATE, Encounters.PROCEDURE_STATE);
        sSavedProcedureProjectionMap.put(Encounters.FINISHED, Encounters.FINISHED);
        sSavedProcedureProjectionMap.put(Encounters.UPLOADED, Encounters.UPLOADED);
        sSavedProcedureProjectionMap.put(Encounters.UPLOAD_STATUS, Encounters.UPLOAD_STATUS);
        sSavedProcedureProjectionMap.put(Encounters.UPLOAD_QUEUE, Encounters.UPLOAD_QUEUE);
        sSavedProcedureProjectionMap.put(Encounters.CREATED_DATE, Encounters.CREATED_DATE);
        sSavedProcedureProjectionMap.put(Encounters.MODIFIED_DATE, Encounters.MODIFIED_DATE);
        sSavedProcedureProjectionMap.put(Encounters.SUBJECT, Encounters.SUBJECT);
        sSavedProcedureProjectionMap.put(Encounters.OBSERVER, Encounters.OBSERVER);
        sSavedProcedureProjectionMap.put(Encounters.PROCEDURE, Encounters.PROCEDURE);
    }
}
