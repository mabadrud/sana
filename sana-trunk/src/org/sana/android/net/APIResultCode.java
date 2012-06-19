package org.sana.android.net;

import java.util.HashMap;
import java.util.Map;

public enum APIResultCode {
	SUCCEED(0),
	FAIL(5),
	LOGIN_SUCCESSFUL(10),
	LOGIN_FAILED(15),
	REGISTER_SUCCESSFUL(20),
	REGISTER_FAILED(25),
	INVALID_REQUEST(35),
	SAVE_SUCCESSFUL(40),
	SAVE_FAILED(45),
	SUCCESSFUL(50),
	FAILURE(55),

	REQUEST_FAILED(996),
	REQUEST_TIMEOUT(997),
	MDS_UNREACHABLE(998),
	NO_CODE(999);
	
	private static Map<Integer, APIResultCode> codeMap = new HashMap<Integer, APIResultCode>();
	private int code;
	
	APIResultCode(int code) {
		this.code = code;
	}
	
	public String toString() {
		return "" + this.code;
	}
	
	private static void addMapping(int iCode, APIResultCode eCode) {
		codeMap.put(iCode, eCode);
	}
	
	public static APIResultCode parseMDSCode(String code) {
		try {
			int iCode = Integer.parseInt(code);
			if (codeMap.containsKey(iCode)) {
				return codeMap.get(iCode);
			}
		} catch (NumberFormatException e) {
		} catch (NullPointerException e) {
		} catch (Exception e) {
		}
		return NO_CODE;
	}

	// Must initialize map in a static context because e.g. calling addMapping
	// in the Enum's constructor would result in an NPE since it is initialized
	// before the static context.
	static {
		for (APIResultCode code : APIResultCode.values()) {
			addMapping(code.code, code);
		}
	}
}
