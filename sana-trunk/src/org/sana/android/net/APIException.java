package org.sana.android.net;

public class APIException extends Exception {
	private APIResultCode code;
	
	public APIException(APIResultCode errorCode, String message) {
		super(message);
		this.code = errorCode;
	}
	
	public APIResultCode getCode() {
		return code;
	}
}
