package org.sana.android.db;

import java.io.FileNotFoundException;
import java.util.HashMap;

import org.sana.android.R;
import org.sana.android.db.DispatchableContract.*;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.support.v4.database.DatabaseUtilsCompat;
import android.text.TextUtils;
import android.util.Log;
//TODO
/**
 * 
 * @author Sana Development
 *
 */
public class DispatchableProvider extends ContentProvider {
	static final String TAG = DispatchableProvider.class.getSimpleName();
	
	public static final String AUTHORITY = "org.sana.provider";
	public static final String DEFAULT_SORT_ORDER = "modified DESC";

	static final String DB = "moca.db";
	
	//TODO finish breaking the backward compatibility
	private static final String PROCEDURE_TABLE = "procedures";
	private static final String OBSERVER_TABLE = "observer";
	private static final String CONCEPT_TABLE = "concept";
	private static final String RELATIONSHIP_TABLE = "concept_relationship";
	private static final String SUBJECT_TABLE = "patients";
	private static final String ENCOUNTER_TABLE = "saved_procedures";
	private static final String OBSERVATION_TABLE = "observation";
	private static final String EVENT_LOG_TABLE = "events";
	private static final String MESSAGE_TABLE = "notifications";

    private DBOpenHelper mOpenHelper;
    
    // uri match constants
    private static final int PROCEDURE_DIR = 0;
    private static final int PROCEDURE = 1;
    private static final int OBSERVER_DIR = 2;
    private static final int OBSERVER = 3;
    private static final int CONCEPT_DIR = 4;
    private static final int CONCEPT = 5;
    private static final int RELATIONSHIP_DIR = 8;
    private static final int RELATIONSHIP = 9;
    private static final int ENCOUNTER_DIR = 10;
    private static final int ENCOUNTER = 11;
    private static final int OBSERVATION_DIR = 12;
    private static final int OBSERVATION = 13;
    private static final int SUBJECT_DIR = 14;
    private static final int SUBJECT = 15;
    private static final int EVENT_LOG_DIR = 16;
    private static final int EVENT_LOG = 17;
    private static final int MESSAGE_DIR = 18;
    private static final int MESSAGE = 19;
    
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private static final HashMap<String,String> mProjMap = new HashMap<String, String>();
    static{
    	uriMatcher.addURI(AUTHORITY,"procedure",PROCEDURE_DIR);
    	uriMatcher.addURI(AUTHORITY,"procedure/#",PROCEDURE);
    	uriMatcher.addURI(AUTHORITY,"observer",OBSERVER_DIR);
    	uriMatcher.addURI(AUTHORITY,"observer/#",OBSERVER);
    	uriMatcher.addURI(AUTHORITY,"concept",CONCEPT_DIR);
    	uriMatcher.addURI(AUTHORITY,"concept/#",CONCEPT);
    	uriMatcher.addURI(AUTHORITY,"concept/relationship",RELATIONSHIP_DIR);
    	uriMatcher.addURI(AUTHORITY,"concept/relationship/#",RELATIONSHIP);
    	uriMatcher.addURI(AUTHORITY,"encounter", ENCOUNTER_DIR);
    	uriMatcher.addURI(AUTHORITY,"encounter/#",ENCOUNTER);
    	uriMatcher.addURI(AUTHORITY,"encounter/observation",OBSERVATION_DIR);
    	uriMatcher.addURI(AUTHORITY,"encounter/observation/#",OBSERVATION);
    	uriMatcher.addURI(AUTHORITY,"subject",SUBJECT_DIR);
    	uriMatcher.addURI(AUTHORITY,"subject/#",SUBJECT);
    	uriMatcher.addURI(AUTHORITY,"events",EVENT_LOG_DIR);
    	uriMatcher.addURI(AUTHORITY,"events/#",EVENT_LOG);
    	uriMatcher.addURI(AUTHORITY,"notifications",MESSAGE_DIR);
    	uriMatcher.addURI(AUTHORITY,"notifications/#",MESSAGE);
    }
    
    
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.d(TAG, "delete() uri="+uri.toString());
		// select table based on match
        String table = getTable(uri);
        String whereClause = getWhereWithIdOrReturn(uri, selection);
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count = db.delete(table,whereClause,selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	@Override
	public String getType(Uri uri) {
        Log.d(TAG, "delete() uri="+uri.toString());
		// select type based on uri match
		switch (uriMatcher.match(uri)) {
		case (PROCEDURE_DIR):
            return Procedures.CONTENT_TYPE;
		case (PROCEDURE):
            return Procedures.CONTENT_ITEM_TYPE;
		case (OBSERVER_DIR):
			return Observers.CONTENT_TYPE;
		case (OBSERVER):
			return Observers.CONTENT_ITEM_TYPE;
		case (CONCEPT_DIR):
			return Concepts.CONTENT_TYPE;
		case (CONCEPT):
			return Concepts.CONTENT_ITEM_TYPE;
		case (RELATIONSHIP_DIR):
			return ConceptRelationships.CONTENT_TYPE;
		case (RELATIONSHIP):
			return ConceptRelationships.CONTENT_ITEM_TYPE;
		case (ENCOUNTER_DIR):
			return Encounters.CONTENT_TYPE;
		case (ENCOUNTER):
			return Encounters.CONTENT_ITEM_TYPE;
		case (OBSERVATION_DIR):
			return Observations.CONTENT_TYPE;
		case (OBSERVATION):
			return Observations.CONTENT_ITEM_TYPE;
		case (SUBJECT_DIR):
			return Subjects.CONTENT_TYPE;
		case (SUBJECT):
			return Subjects.CONTENT_ITEM_TYPE;
		case (EVENT_LOG_DIR):
			return Events.CONTENT_TYPE;
		case (EVENT_LOG):
			return Events.CONTENT_ITEM_TYPE;
		case (MESSAGE_DIR):
			return Notifications.CONTENT_TYPE;
		case (MESSAGE):
			return Notifications.CONTENT_ITEM_TYPE;
		 default:
	            throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		Log.d(TAG, "insert() uri="+uri);
		String table = getTable(uri);
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		
		//allow created and modified to be set manually here.
		Long now = Long.valueOf(System.currentTimeMillis());
		if(values.containsKey(Columns.CREATED_DATE) == false) {
            values.put(Columns.CREATED_DATE, now);
        }
        if(values.containsKey(Columns.MODIFIED_DATE) == false) {
            values.put(Columns.MODIFIED_DATE, now);
        }
        
		db.insert(table, null, values);
		getContext().getContentResolver().notifyChange(uri, null);
		return null;
	}

	@Override
    public boolean onCreate() {
        Log.d(TAG, "onCreate()");
        int version = getContext().getResources().getInteger(R.integer.db_version);
        mOpenHelper = new DBOpenHelper(getContext(), version);
        return true;
    }
	
	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode){
		switch(uriMatcher.match(uri)){
		case(OBSERVATION):
			try {
				return openFileHelper(uri,mode);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				throw new IllegalArgumentException(
						"DispatchableProvider.openFile(): "+e.getMessage());
			}
		default:
			throw new IllegalArgumentException(
					"openFile(): Not a valid file uri: " + uri);
		}
		
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
        String table = getTable(uri);
        
		String orderBy;
        if(TextUtils.isEmpty(sortOrder)) {
            orderBy = DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }
		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(table);
		String whereClause = getWhereWithIdOrReturn(uri, selection);
        Cursor c = qb.query(db, projection, whereClause, selectionArgs, null, 
        		null, orderBy);
		return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
        String table = getTable(uri);
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		String whereClause = getWhereWithIdOrReturn(uri, selection);
		
		// Always update modified time on update
		Long now = Long.valueOf(System.currentTimeMillis());
		values.put(Columns.MODIFIED_DATE, now);
		
		db.update(table, values, whereClause, selectionArgs);
		getContext().getContentResolver().notifyChange(uri, null);
		return 0;
	}

	/**
	 * Returns internal table name based on the values loaded into the 
	 * UriMathcer
	 * @param uri
	 * @return
	 */
	String getTable(Uri uri){
		String table = "";
		switch (uriMatcher.match(uri)) {
		case (PROCEDURE_DIR):
			table = PROCEDURE_TABLE;
			break;
		case (PROCEDURE):
			table = PROCEDURE_TABLE;
			break;
		case (OBSERVER_DIR):
			table = OBSERVER_TABLE;
			break;
		case (OBSERVER):
			table = OBSERVER_TABLE;
			break;
		case (CONCEPT_DIR):
			table = CONCEPT_TABLE;
			break;
		case (CONCEPT):
			table = CONCEPT_TABLE;
			break;
		case (RELATIONSHIP_DIR):
			table = RELATIONSHIP_TABLE;
			break;
		case (RELATIONSHIP):
			table = RELATIONSHIP_TABLE;
			break;
		case (ENCOUNTER_DIR):
			table = ENCOUNTER_TABLE;
			break;
		case (ENCOUNTER):
			table = ENCOUNTER_TABLE;
			break;
		case (OBSERVATION_DIR):
			table = OBSERVATION_TABLE;
			break;
		case (OBSERVATION):
			table = OBSERVATION_TABLE;
			break;
		case (SUBJECT_DIR):
			table = SUBJECT_TABLE;
			break;
		case (SUBJECT):
			table = SUBJECT_TABLE;
			break;
		case (EVENT_LOG_DIR):
			table = EVENT_LOG_TABLE;
			break;
		case (EVENT_LOG):
			table = EVENT_LOG_TABLE;
			break;
		case (MESSAGE_DIR):
			table = MESSAGE_TABLE;
			break;
		case (MESSAGE):
			table = MESSAGE_TABLE;
			break;
		 default:
	            throw new IllegalArgumentException("Unknown URI " + uri);
		}
		return table;
	}

	/**
	 * Returns the selection statement as 
	 *  ( _ID = "uri.getPathSegments().get(1)" ) AND ( selection )
	 * or as the original based on whether the uri match was a dir or item.
	 * Relies on matcher values for *.dir being even integers.
	 * @param uri
	 * @param selection
	 * @return
	 */
	String getWhereWithIdOrReturn(Uri uri, String selection){
		String select = selection;
		int match = uriMatcher.match(uri);
		if((match & 1) != 0)
			select = DatabaseUtilsCompat.concatenateWhere(selection, 
					BaseColumns._ID + " = " + uri.getPathSegments().get(1));
		return select;
	}
	

    
    class DBOpenHelper extends SQLiteOpenHelper{
    
    	public DBOpenHelper(Context context, int version) {
			super(context, DB, null, version);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			// TODO Fill in create statements here
			
			for(String stmt: CREATES){
				db.execSQL(stmt);
			}
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// TODO Do something
			if(oldVersion < newVersion){
				
			}
		}	
    }
	
    // static create statements 
    //TODO finish implementation so that we can read from a schema
    private static final String CREATE_PROCEDURE = 
    		"CREATE TABLE " + PROCEDURE_TABLE + " ("
            + Columns._ID + " INTEGER PRIMARY KEY,"
            + Procedures.TITLE + " TEXT,"
            + Procedures.AUTHOR + " TEXT,"
            + Procedures.UUID + " TEXT,"
            + Procedures.PROCEDURE + " TEXT,"
            + Columns.CREATED_DATE + " INTEGER,"
            + Columns.MODIFIED_DATE + " INTEGER"
            + ");";

    private static final String CREATE_OBSERVER  = 
    		"CREATE TABLE " + OBSERVER_TABLE + " ("
            + Columns._ID + " INTEGER PRIMARY KEY,"
            + Observers.UUID + " TEXT,"
            + Observers.NAME + " TEXT,"
            + Observers.PASSWORD + " TEXT,"
            + Columns.CREATED_DATE + " INTEGER,"
            + Columns.MODIFIED_DATE + " INTEGER"
            + ");";

    private static final String CREATE_CONCEPT = 
    		"CREATE TABLE " + CONCEPT_TABLE + " ("
            + Columns._ID + " INTEGER PRIMARY KEY,"
            + Concepts.UUID + " TEXT,"
            + Concepts.NAME + " TEXT,"
            + Concepts.DESCRIPTION + " TEXT,"
            + Concepts.TYPE + " TEXT,"
            + Concepts.DATA_TYPE + " TEXT,"
            + Concepts.MEDIA_TYPE + " TEXT,"
            + Concepts.CONSTRAINT + " TEXT,"
            + Columns.CREATED_DATE + " INTEGER,"
            + Columns.MODIFIED_DATE + " INTEGER"
            + ");";

    private static final String CREATE_RELATIONSHIP = 
    		"CREATE TABLE " + RELATIONSHIP_TABLE + " ("
            + Columns._ID + " INTEGER PRIMARY KEY,"
            + ConceptRelationships.RELATES_FROM + " TEXT,"
            + ConceptRelationships.RELATIONSHIP + " TEXT,"
            + ConceptRelationships.RELATES_TO + " TEXT,"
            + ConceptRelationships.CONSTRAINED_BY + " TEXT,"
            + Columns.CREATED_DATE + " INTEGER,"
            + Columns.MODIFIED_DATE + " INTEGER"
            + ");";
    
    private static final String CREATE_ENCOUNTER = 
			"CREATE TABLE " + ENCOUNTER_TABLE + " ("
            + Columns._ID + " INTEGER PRIMARY KEY,"
            + Encounters.UUID + " TEXT,"
            + Encounters.PROCEDURE_ID + " INTEGER,"
            + Encounters.PROCEDURE_STATE + " TEXT,"
            + Encounters.FINISHED + " INTEGER,"
            + Encounters.UPLOADED + " INTEGER,"
            + Encounters.UPLOAD_STATUS + " TEXT,"
            + Encounters.UPLOAD_QUEUE + " TEXT,"
            + Encounters.SUBJECT + " TEXT,"
            + Encounters.OBSERVER + " TEXT,"
            + Encounters.PROCEDURE + " TEXT,"
            + Columns.CREATED_DATE + " INTEGER,"
            + Columns.MODIFIED_DATE + " INTEGER"
            + ");";

    private static final String CREATE_OBSERVATION  = 
    		"CREATE TABLE " + OBSERVER_TABLE + " ("
            + Columns._ID + " INTEGER PRIMARY KEY,"
            + Observations.ENCOUNTER + " TEXT,"
            + Observations.CONCEPT + " TEXT,"
            + Observations.NODE_ID + " TEXT,"
            + Observations.VALUE + " TEXT,"
            + Observations.VALUE_COMPLEX + " TEXT,"
            + Observations.UPLOAD_PROGRESS + " INTEGER,"
            + Observations.UPLOADED + " TEXT,"
            + Columns.CREATED_DATE + " INTEGER,"
            + Columns.MODIFIED_DATE + " INTEGER"
            + ");";
    
    private static final String CREATE_SUBJECT = 
    		"CREATE TABLE " + SUBJECT_TABLE + " ("
			+ Columns._ID + " INTEGER PRIMARY KEY,"
			+ Subjects.UUID + " TEXT,"
			+ Subjects.GIVEN_NAME + " TEXT,"
			+ Subjects.FAMILY_NAME + " TEXT,"
			+ Subjects.GENDER + " TEXT,"
			+ Subjects.DATE_OF_BIRTH + " INTEGER,"
	        + Columns.CREATED_DATE + " INTEGER,"
	        + Columns.MODIFIED_DATE + " INTEGER"
			+ ");";

    private static final String CREATE_EVENTS = 
    		"CREATE TABLE " + EVENT_LOG_TABLE + " ("
			+ Columns._ID + " INTEGER PRIMARY KEY,"
			+ Events.EVENT_TYPE + " TEXT, "
			+ Events.EVENT_VALUE + " TEXT, " 
			+ Events.ENCOUNTER_REFERENCE + " TEXT, "
			+ Events.PATIENT_REFERENCE + " TEXT, "
			+ Events.USER_REFERENCE + " TEXT, "
			+ Events.UPLOADED + " INTEGER, "
            + Columns.CREATED_DATE + " INTEGER,"
            + Columns.MODIFIED_DATE + " INTEGER"
			+ ");";
    
    private static final String CREATE_MESSAGE = 
    		"CREATE TABLE " + MESSAGE_TABLE + " ("
            + Notifications._ID + " INTEGER PRIMARY KEY,"
            + Notifications.NOTIFICATION_GUID + " TEXT,"
            + Notifications.PATIENT_ID + " TEXT,"
            + Notifications.PROCEDURE_ID + " TEXT,"
            + Notifications.MESSAGE + " TEXT,"
            + Notifications.FULL_MESSAGE + " TEXT,"
            + Notifications.DOWNLOADED + " INTEGER,"
            + Notifications.CREATED_DATE + " INTEGER,"
            + Notifications.MODIFIED_DATE + " INTEGER"
            + ");";
    private static final String[] CREATES = new String[]{
			CREATE_PROCEDURE,
			CREATE_OBSERVER,
			CREATE_CONCEPT,
			CREATE_RELATIONSHIP,
			CREATE_ENCOUNTER,
			CREATE_OBSERVATION,
			CREATE_SUBJECT,
			CREATE_EVENTS,
			CREATE_MESSAGE
	}; 
}
