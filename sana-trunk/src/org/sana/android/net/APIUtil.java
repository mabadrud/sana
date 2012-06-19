package org.sana.android.net;

import java.io.IOException;
import java.lang.reflect.Type;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.sana.android.Constants;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public class APIUtil {
	private static final String TAG = APIUtil.class.getSimpleName();

	public static String constructValidateCredentialsURL(String mdsURL) {
		return mdsURL +Constants.VALIDATE_CREDENTIALS_PATTERN;
	}

	public static String constructProcedureSubmitURL(String mdsURL) {
		return mdsURL + Constants.PROCEDURE_SUBMIT_PATTERN;
	}

	public static String constructBinaryChunkSubmitURL(String mdsURL) {
		return mdsURL + Constants.BINARYCHUNK_SUBMIT_PATTERN;
	}

	public static String constructBinaryChunkHackSubmitURL(String mdsURL) {
		return mdsURL + Constants.BINARYCHUNK_HACK_SUBMIT_PATTERN;
	}

	public static String constructPatientDatabaseDownloadURL(String mdsURL) {
		return mdsURL + Constants.DATABASE_DOWNLOAD_PATTERN;
	}
	
	public static String constructUserInfoURL(String mdsURL, String id) {
		return mdsURL + Constants.USERINFO_DOWNLOAD_PATTERN + id;
	}
	
	public static String constructProcedureListURL(String mdsURL) {
		return mdsURL + Constants.PROCEDURELIST_DOWNLOAD_PATTERN;
	}
	
	public static String constructProcedureDownloadURL(String mdsURL, int procedureId) {
		return mdsURL + Constants.PROCEDURELIST_DOWNLOAD_PATTERN + procedureId;
	}
	
	static <T> APIResponse<T> doApiRequest(HttpUriRequest request, Type returnType) {
		final String methodTag = "doApiRequest()";
		int responseCode = 0;
		HttpResponse response = null;
		MDSResponse<T> responseObj = null;
		String responseString = "";
		
		// TODO better null check!
		if(request == null){
		    Log.e(TAG, "{ class_method : " + methodTag + ", request : null }");
		    // TODO raise an exception?
		    return new APIResponse<T>(responseCode, responseObj);
		}

		// DEPRECATED
		// This is how you would set a timeout on a request. We don't set timeouts yet, though.
		//HttpConnectionManager manager = client.getHttpConnectionManager();
		//HttpConnectionManagerParams params = manager.getParams();
		//params.setConnectionTimeout(1000);
		
		// request OK. debugging info
		Log.d(TAG, "{ class_method : " + methodTag + ", request: { method: " + request.getMethod() 
		    + ", uri : " + request.getURI() + " }}");
		HttpClient client = new DefaultHttpClient();
		try {
			
			//TODO Refactor and reimplement SSL layer
			response = client.execute(request); 
			responseCode = response.getStatusLine().getStatusCode();
			responseString = EntityUtils.toString(response.getEntity());
			
			Log.d(TAG, "{ class_method : " + methodTag 
			    + ", response: { status: " + responseCode + ", response_string: " + responseString +" }");
			// On successful response try to parse into MDSResponse object
			if (responseCode == 200) {
			    try{
				    Gson gson = new Gson();
				    responseObj = gson.fromJson(responseString, returnType);
			    } catch (JsonParseException e) {
				Log.e(TAG, "{ class_method : " + methodTag  
				    + ", exception: { exception_type: " + e.getClass().getSimpleName() + ", message: " + e.getMessage()+" }" 
				    + ", response: { status: " + responseCode + ", response_string: " + responseString +" }");
			    }
			} else {
			    Log.e(TAG, "{ class_method : " + methodTag  
				    + ", response: { status: " + responseCode + ", response_string: " + responseString +" }");
			}

		} catch (ClientProtocolException e) {
			Log.e(TAG, "{ class_method : " + methodTag  
				    + ", exception: { exception_type: " + e.getClass().getSimpleName() 
					+ ", message: " + e.getMessage()+" }}");	
		} catch (IOException e) {
			Log.e(TAG, "{ class_method : " + methodTag  
				    + ", exception: { exception_type: " + e.getClass().getSimpleName() 
					+ ", message: " + e.getMessage()+" }}");
		} finally {
			;//client.releaseConnection();
		}
		return new APIResponse<T>(responseCode, responseObj);
	}
}
