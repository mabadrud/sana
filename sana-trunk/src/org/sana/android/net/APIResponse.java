package org.sana.android.net;

public class APIResponse <T> {
	private int status;
	private MDSResponse<T> result;
	
	APIResponse(int status, MDSResponse<T> result) {
		this.status = status;
		this.result = result;
	}
	
	public int getStatus() {
		return status;
	}
	
	public MDSResponse<T> getResult() {
		return result;
	}
}
