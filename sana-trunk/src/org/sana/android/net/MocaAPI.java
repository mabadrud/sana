package org.sana.android.net;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.NameValuePair;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.google.gson.reflect.TypeToken;

public class MocaAPI {
	public static final String TAG = MocaAPI.class.getSimpleName();
	
	private String apiUri;
	private String username;
	private String password;
	
	public MocaAPI(String host, String username, String password) {
		this(host,username,password, false);
	}
	
	public MocaAPI(String host, String username, String password, boolean secure){
		this.apiUri = ((secure) ? "https://": "http://")+ host;
		this.username = username;
		this.password = password;
		
	}
	
	protected boolean doPost(HttpEntity entity, String uri) throws APIException {
		HttpPost post = new HttpPost(URI.create(uri ));
	    post.setEntity(entity);
	    
	    // mds specific part
	    Type returnType = new TypeToken<MDSResponse<String>>() {}.getType();
	    APIResponse<String> response = APIUtil.<String>doApiRequest(post, returnType);
	    int status = response.getStatus();
	    MDSResponse<String> result = response.getResult();
	    
	    if (status == 200 && result != null) {
		  // TODO(XXX) throw failure specific error code 
		  return result.succeeded();
	    } else 
		throw new APIException(APIResultCode.INVALID_REQUEST, 
		    "Could not connect to server.");
	}
	
	protected <T> T doGet( List<NameValuePair> qparams, String url) throws APIException{
		String query = URLEncodedUtils.format(qparams, "UTF-8");
	    HttpGet get = new HttpGet(URI.create(url + "?" + query));
	    
	    // mds specific part
	    Type returnType = new TypeToken<MDSResponse<T>>() {}.getType();
	    APIResponse<String> response = APIUtil.<String>doApiRequest(get, returnType);
	    int status = response.getStatus();
	    MDSResponse<T> result = (MDSResponse<T>)response.getResult();		
	    
	    if (status == 200 && result != null) {
		// TODO(XXX) throw failure specific error code
	    	return result.getData();
	    } else
		throw new APIException(APIResultCode.INVALID_REQUEST, 
		    "Could not connect to server.");
	}
	
	public boolean transmitCaseResponses(String savedProcedureGuid, 
	    String procedureGuid, String phoneIdentifier, 
	    String jsonResponses) throws APIException 
	{

		try{
		//TODO Replace param names with String Constants
		  List<NameValuePair> form = new ArrayList<NameValuePair>();
		  form.add(new BasicNameValuePair("username", username));
		  form.add(new BasicNameValuePair("password", password));
		  form.add(new BasicNameValuePair("savedproc_guid", savedProcedureGuid));
		  form.add(new BasicNameValuePair("procedure_guid", procedureGuid));
		  form.add(new BasicNameValuePair("phone", phoneIdentifier));
		  form.add(new BasicNameValuePair("responses", jsonResponses));
		  UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, "UTF-8");
		
		  // Create Request
		  String uri = APIUtil.constructProcedureSubmitURL(apiUri);
		  return doPost(entity, uri);
		} catch(UnsupportedEncodingException e){
		    throw new APIException(APIResultCode.INVALID_REQUEST, e.getMessage());
		}
	}
	
	public boolean transmitBinarySequence(String savedProcedureId, 
		String elementId, String fileGuid, String element_type, String element_filename, 
		int fileSize, int start, int end, byte byte_data[]) throws APIException 
	{
	    try{
		// TODO Replace param names with constants
		MultipartEntity entity = new MultipartEntity();
		entity.addPart("procedure_guid", new StringBody(savedProcedureId));
		entity.addPart("element_id", new StringBody(elementId));
		entity.addPart("binary_guid", new StringBody(fileGuid));
		entity.addPart("element_type", new StringBody(element_type));
		entity.addPart("file_size", new StringBody(Integer.toString(fileSize)));
		entity.addPart("byte_start", new StringBody(Integer.toString(start)));
		entity.addPart("byte_end", new StringBody(Integer.toString(end)));
		entity.addPart("byte_data", new ByteArrayBody(byte_data, element_filename));
		
		String uri = APIUtil.constructBinaryChunkSubmitURL(apiUri);
		return doPost(entity, uri);
		} catch(UnsupportedEncodingException e){
		  throw new APIException(APIResultCode.INVALID_REQUEST, e.getMessage());
		}
	}
	
	public boolean transmitBinarySequenceAsBase64Text(String savedProcedureId, 
	    String elementId, String fileGuid, String element_type, String element_filename, 
	    int fileSize, int start, int end, byte byte_data[]) throws APIException 
	{
		// TODO Replace param names with constants
		try{
		  List<NameValuePair> form = new ArrayList<NameValuePair>();
		  form.add(new BasicNameValuePair("procedure_guid", savedProcedureId));
		  form.add(new BasicNameValuePair("element_id", elementId));
		  form.add(new BasicNameValuePair("binary_guid", fileGuid));
		  form.add(new BasicNameValuePair("element_type", element_type));
		  form.add(new BasicNameValuePair("file_size", Integer.toString(fileSize)));
		  form.add(new BasicNameValuePair("byte_start", Integer.toString(start)));
		  form.add(new BasicNameValuePair("byte_end", Integer.toString(end)));

		  // Encode byte_data in Base64
		  byte[] encoded_data = new Base64().encode(byte_data);
		  form.add(new BasicNameValuePair("byte_data", new String(encoded_data)));
		  UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, "UTF-8");

		  String uri = APIUtil.constructBinaryChunkHackSubmitURL(apiUri);
		  return doPost(entity,uri);
		} catch(UnsupportedEncodingException e){
		    throw new APIException(APIResultCode.INVALID_REQUEST, e.getMessage());
		}
	}
	
	public boolean validateCredentials() throws APIException {
		// TODO Replace param names with constants
		List<NameValuePair> qparams = new ArrayList<NameValuePair>(2);
		qparams.add(new BasicNameValuePair("username", username));
		qparams.add(new BasicNameValuePair("password", password));
		try {
			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(qparams,
					"UTF-8");
			String uri = APIUtil.constructValidateCredentialsURL(apiUri);
			boolean result = doPost(entity, uri);
			return result;
		} catch (UnsupportedEncodingException e) {
			throw new APIException(APIResultCode.INVALID_REQUEST,
					e.getMessage());
		}
	}
	
	public String getAllPatients() throws APIException {
		// TODO Replace param names with constants
		List<NameValuePair> qparams = new ArrayList<NameValuePair>(2);
		qparams.add(new BasicNameValuePair("username", username));
		qparams.add(new BasicNameValuePair("password", password));

		String uri = APIUtil.constructPatientDatabaseDownloadURL(apiUri);
		String result = doGet(qparams, uri);
		return result;
	}
	
	public String getPatientInformation(String patientIdentifier) throws APIException {
		// TODO Replace param names with constants
		List<NameValuePair> qparams = new ArrayList<NameValuePair>(2);
		qparams.add(new BasicNameValuePair("username", username));
		qparams.add(new BasicNameValuePair("password", password));

		String uri = APIUtil.constructUserInfoURL(apiUri, patientIdentifier);
		String result =  doGet(qparams, uri);
		return result;
	}
	
	public List<ProcedureInfo> getAvailableProcedureList() throws APIException {
		// TODO Replace param names with constants
		List<NameValuePair> qparams = new ArrayList<NameValuePair>(2);
		qparams.add(new BasicNameValuePair("username", username));
		qparams.add(new BasicNameValuePair("password", password));

		String uri = APIUtil.constructProcedureListURL(apiUri);
		List<ProcedureInfo> result = doGet(qparams, uri);
		return result;
	}
	

	public String getProcedure(int procedureId) throws APIException {
		// TODO Replace param names with constants
		List<NameValuePair> qparams = new ArrayList<NameValuePair>(2);
		qparams.add(new BasicNameValuePair("username", username));
		qparams.add(new BasicNameValuePair("password", password));
		qparams.add(new BasicNameValuePair("procedure_id", Integer.toString(procedureId)));

		String uri = APIUtil.constructProcedureDownloadURL(apiUri, procedureId);
		String result = doGet(qparams, uri);
		return result;
	}
}
