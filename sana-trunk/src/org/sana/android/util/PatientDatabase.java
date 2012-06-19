package org.sana.android.util;
import org.sana.android.db.DispatchableContract.Subjects;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.util.Log;

public class PatientDatabase {

    public static final String TAG = PatientDatabase.class.toString();
    
	public static void importPatientData(ContentResolver cr, String users) {
		users = users.trim();
		ContentValues newuser = new ContentValues();

		String[] data = users.split("##");
		
		for (String record : data) {
			record = record.trim();
			if ("".equals(record)) {
				continue;
			}
			
			Log.i(TAG, "Processing:" + record);
			try {
				String gender = record.substring(record.length() - 1);
				record = record.substring(0, record.length() - 1);
				String[] findname = record.split("[0-9]+");
				String firstname = findname[0];
				String lastname = findname[1];
				String[] findrest = record.split("[A-Za-z]+");
				int birthdate = 0;
				try {
					birthdate = Integer.parseInt(findrest[1]);
				} catch (Exception e) {

				}
				String id = findrest[2];

				Log.i(TAG, "firstname is " + firstname);
				Log.i(TAG, "lastname is " + lastname);
				Log.i(TAG, "birthdate is " + birthdate);
				Log.i(TAG, "gender is " + gender);
				Log.i(TAG, "id is " + id);

				// add new user to database
				newuser.put(Subjects.GIVEN_NAME, firstname);
				newuser.put(Subjects.FAMILY_NAME, lastname);
				newuser.put(Subjects.DATE_OF_BIRTH, birthdate);
				newuser.put(Subjects.UUID, id);
				newuser.put(Subjects.GENDER, gender);
				cr.insert(Subjects.CONTENT_URI, newuser);
				newuser.clear();
				Log.i(TAG, "added new patient to database");
			} catch (Exception e) {
				Log.i(TAG, "Exception while processing:" + record + " : "
						+ e.toString());
			}
		}
	}
	
	//returns array with firstname + lastname + gender + birthdate
	public static String[] parsePatientInfo(String record) {
		record = record.trim();
		String[] data = new String[5];
		String gender = record.substring(record.length()-1);
		record = record.substring(0, record.length()-1);
		String[] findname = record.split("[0-9]+");
		String firstname = findname[0];
		String lastname = findname[1];
		String[] findrest = record.split("[A-Za-z]+");
		int birthdate = 0;
		try {
			birthdate = Integer.parseInt(findrest[1]);
		} catch (Exception e) {

		}
		data[0] = firstname;
		data[1] = lastname;
		data[2] = gender;
		data[3] = String.valueOf(birthdate);
		//System.out.println(firstname + " " + lastname + " " + birthdate + " " + gender + " " +id); 
		return data;
		
	}

}
