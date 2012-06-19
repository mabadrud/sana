package test.sana.android.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.sana.android.db.DispatchableContract.*;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * Collection of utilities for generating local test data. With the 
 * methods contained here, you can create entries for common data model
 * objects.
 * 
 * @author Sana Development
 *
 */
public final class DispatchableTests {

	private DispatchableTests(){}
	
	static final Locale LOCALE = Locale.US;
	
	/** Translates digits 0-9 to string vals */
	static final Map<Integer,String> NUMBER_MAP = new HashMap<Integer,String>();
	static{
		NUMBER_MAP.put(0, "ZERO");
		NUMBER_MAP.put(1, "ONE");
		NUMBER_MAP.put(2, "TWO");
		NUMBER_MAP.put(3, "THREE");
		NUMBER_MAP.put(4, "FOUR");
		NUMBER_MAP.put(5, "FIVE");
		NUMBER_MAP.put(6, "SIX");
		NUMBER_MAP.put(7, "SEVEN");
		NUMBER_MAP.put(8, "EIGHT");
		NUMBER_MAP.put(9, "NINE");
	}
	
	/** 
	 * Formats name as TEST_base_digit_digit_digit where digit is the String
	 * representation of a 0-9 int value
	 */
	static String formatName(String base, int index){
	    int ones = index % 10;
	    int tens = ((index - ones) / 10) % 10;
	    int hundreds = (index - 10*tens - ones)/100;
		return String.format("TEST_%s_%s_%s_%s", 
				base.toUpperCase(),
				NUMBER_MAP.get(hundreds),
				NUMBER_MAP.get(tens), 
				NUMBER_MAP.get(ones));
	}
	
	/** @see java.util.UUID#randomUUID() */
	static String genUUID(){
		return UUID.randomUUID().toString();
	}
	
	/**
	 * Returns a new Uri with the _id randomized between 0 and count - 1.
	 * 
	 * @param uri a CONTENT_URI to use for building the randomized Uri
	 * @param count The max 
	 * @return
	 */
	static Uri randomUri(Uri contentUri, int count){
		Double id =  Math.floor(Math.random()*count);
		return ContentUris.withAppendedId(contentUri, id.longValue());
	}
	
	/**
	 * Returns the number of row entries in the application database for
	 * the provided content Uri.
	 * @param resolver
	 * @param contentUri
	 * @return
	 */
	static int getTableSize(ContentResolver resolver, Uri contentUri){
		Cursor c = null;
		// TODO make more efficient
		c = resolver.query(Subjects.CONTENT_URI, null, null, null, null);
		int count = c.getCount();
		c.close();
		return count;
		
	}
	
	/** 
	 * @see android.content.ContentResolver#bulkInsert(Uri, ContentValues[])
	 * @param resolver
	 * @param uri
	 * @param values
	 */
	static void insert(ContentResolver resolver, Uri uri, 
			List<ContentValues> values)
	{
		resolver.bulkInsert(uri, (ContentValues[]) values.toArray());
	}

	/**
	 * Creates a set of text based Concepts in the application database. Test 
	 * Concepts will have the following values incremented as indicated.
	 * @param resolver
	 * @param count
	 * @throws IllegalArgumentException if count > 999
	 */
	public static void createTextConcepts(ContentResolver resolver, int count){
		if(count > 999) 
			throw new IllegalArgumentException("Count must be < 1000");
		List<ContentValues> list = new ArrayList<ContentValues>(count);
		// No Uri dependencies
		int index = 0;
		for(ContentValues values:list){
			values.put(Concepts.DATA_TYPE, "string");
			values.put(Concepts.DESCRIPTION, "Test text concept");
			values.put(Concepts.MEDIA_TYPE, "text/plain");
			values.put(Concepts.TYPE, "simple");
			values.put(Concepts.NAME, formatName("CONCEPT", index));
			values.put(Concepts.UUID, genUUID());
			index++;
		}
		insert(resolver,Concepts.CONTENT_URI,list);
	}

	/**
	 * Creates a set of Encounters in the application database. Test Encounters
	 * will have the following values incremented as indicated.
	 * @param resolver
	 * @param count
	 * @throws IllegalArgumentException if count > 999
	 */
	public static void createEncounters(ContentResolver resolver, int count){
		if(count > 999) 
			throw new IllegalArgumentException("Count must be < 1000");
		List<ContentValues> list = new ArrayList<ContentValues>(count);

		//TODO get counts for randomized Uris
		int subjectCount = getTableSize(resolver, Subjects.CONTENT_URI);
		int procedureCount = getTableSize(resolver, Procedures.CONTENT_URI);
		int observerCount = getTableSize(resolver, Observers.CONTENT_URI);
		
		int index = 0;
		for(ContentValues values:list){
			values.put(Encounters.OBSERVER, randomUri(
					Observers.CONTENT_URI, observerCount).toString());
			values.put(Encounters.SUBJECT, randomUri(
					Subjects.CONTENT_URI, subjectCount).toString());
			values.put(Encounters.PROCEDURE, randomUri(
					Procedures.CONTENT_URI, procedureCount).toString());
			//TODO fill in the remaining values
			values.put(Encounters.UUID, genUUID());
			index++;
		}
		insert(resolver,Encounters.CONTENT_URI,list);
	}	
	
	/**
	 * Creates a set of Observations in the application database. Test 
	 * Observations will have the following values incremented as indicated.
	 * @param resolver
	 * @param count
	 * @throws IllegalArgumentException if count > 999
	 */
	public static void createObservations(ContentResolver resolver, int count){
		if(count > 999) 
			throw new IllegalArgumentException("Count must be < 1000");
		List<ContentValues> list = new ArrayList<ContentValues>(count);
		
		//TODO get counts for randomized Uris
		int conceptCount = getTableSize(resolver, Concepts.CONTENT_URI);
		int encounterCount = getTableSize(resolver, Encounters.CONTENT_URI);
		int random = 0;
		
		int index = 0;
		for(ContentValues values:list){
			//TODO fill in the remaining values
			values.put(Observations.CONCEPT, randomUri(
					Observations.CONTENT_URI, conceptCount).toString());
			values.put(Observations.ENCOUNTER, randomUri(
					Encounters.CONTENT_URI, encounterCount).toString());
			values.put(Observations.NODE_ID,formatName("Observation", index));
			// TODO randomize value
			values.put(Observations.VALUE, String.valueOf(random));
			index++;
		}
		insert(resolver,Observations.CONTENT_URI,list);
	}
	
	/**
	 * Creates a set of Observers in the application database. Test Observers
	 * will have the following values incremented as indicated.
	 * @param resolver
	 * @param count
	 * @throws IllegalArgumentException if count > 999
	 */
	public static void createObservers(ContentResolver resolver, int count){
		if(count > 999) 
			throw new IllegalArgumentException("Count must be < 1000");
		List<ContentValues> list = new ArrayList<ContentValues>(count);
		// No Uri dependencies
		int index = 0;
		for(ContentValues values:list){
			String uuid = genUUID();
			values.put(Observers.PASSWORD, String.valueOf(uuid.hashCode()));
			values.put(Observers.NAME, formatName("OBSERVER", index));
			values.put(Observers.UUID, uuid);
			index++;
		}
		insert(resolver,Observers.CONTENT_URI,list);
	}

	/**
	 * Creates a set of Procedures in the application database. Test Procedures
	 * will have the following values incremented as indicated.
	 * @param resolver
	 * @param count
	 * @throws IllegalArgumentException if count > 999
	 */
	public static void createProcedures(ContentResolver resolver, int count){
		if(count > 999) 
			throw new IllegalArgumentException("Count must be < 1000");
		List<ContentValues> list = new ArrayList<ContentValues>(count);
		// No Uri dependencies
		int index = 0;
		for(ContentValues values:list){
			values.put(Procedures.TITLE, formatName("PROCEDURE", index));
			values.put(Procedures.UUID, genUUID());
			index++;
		}
		insert(resolver,Procedures.CONTENT_URI,list);
	}

	/**
	 * Creates a set of subjects in the application database. Test Subjects
	 * will have the following values incremented as indicated.
	 * @param resolver
	 * @param count
	 * @throws IllegalArgumentException if count > 999
	 */
	public static void createSubjects(ContentResolver resolver, int count){
		if(count > 999) 
			throw new IllegalArgumentException("Count must be < 1000");
		List<ContentValues> list = new ArrayList<ContentValues>(count);
		// No Uri dependencies
		// TODO Randomize
		String gender = "M";
		String dob = "1900-01-01";
		
		int index = 0;
		for(ContentValues values:list){
			values.put(Subjects.GENDER, gender);
			values.put(Subjects.DATE_OF_BIRTH, dob);
			values.put(Subjects.GIVEN_NAME,
					formatName("SUBJECT_GIVEN", index));
			values.put(Subjects.FAMILY_NAME, 
					formatName("SUBJECT_FAMILY", index));
			values.put(Subjects.UUID, genUUID());
			index++;
		}
		insert(resolver,Subjects.CONTENT_URI,list);
	}
	

	/**
	 * Creates a default set of randomized test data comprised of:
	 * <ul>
	 *  <li>10 concepts</li>
	 *  <li>10 subjects</li>
	 *  <li>10 procedures</li>
	 *  <li>10 observers</li>
	 *  <li>10 encounters</li>
	 *  <li>100 observations</li>
	 * </ul>  
	 * @param resolver
	 */
	public static void setUpTestData(ContentResolver resolver){
		int concepts = 10;
		int subjects = 10;
		int procedures = 10;
		int encounters = 10;
		int observers = 10;
		int observations = 100;
		
		setUpTestData(resolver, concepts, subjects, procedures, observers, 
				encounters, observations);	
	}
	
	/**
	 * Creates a set of randomized test data comprised of a number of objects
	 * defined by the parameters.
	 * 
	 * @param resolver
	 * @param concepts The number of Concept entries
	 * @param subjects The number of Subject entries
	 * @param procedures The number of Procedure entries
	 * @param observers The number of Observer entries
	 * @param encounters The number of Encounter entries
	 * @param observations The number of Observation entries
	 * @throws IllegalArgumentException if any of the int params > 999
	 */
	public static void setUpTestData(ContentResolver resolver, int concepts,
		int subjects, int procedures, int observers, int encounters,
		int observations)
	{	
		createTextConcepts(resolver, concepts);
		createSubjects(resolver, subjects);
		createProcedures(resolver, procedures);
		createObservers(resolver, observers);
		createEncounters(resolver, encounters);
		createObservations(resolver, observations);	
	}
	
	/**
	 * Removes a set of test data. 
	 * <b>WARNING:</b> The current implementation removes all data from the
	 * Concept, Subject, Procedure, Encounter, and Observation tables.
	 * 
	 * @param resolver
	 */
	public static void tearDownTestData(ContentResolver resolver){
		//TODO ?Maybe update these to just remove names like TEST_*? 
		String where = null;
		String[] selectionArgs = null;
		resolver.delete(Concepts.CONTENT_URI, where, selectionArgs);
		resolver.delete(Subjects.CONTENT_URI, where, selectionArgs);
		resolver.delete(Procedures.CONTENT_URI, where, selectionArgs);
		resolver.delete(Observers.CONTENT_URI, where, selectionArgs);
		resolver.delete(Encounters.CONTENT_URI, where, selectionArgs);
		resolver.delete(Observations.CONTENT_URI, where, selectionArgs);
	}
}
