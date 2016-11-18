package de.fhg.ids.comm.ws.protocol.rat;

import java.util.LinkedHashMap;
import java.util.Map;

import de.fhg.aisec.ids.messages.Idscp.Pcr;

public class PcrMessage {

	private String nonce;
	private Map<Integer, String> values = new LinkedHashMap<Integer, String>();
	private boolean success = false;

	public PcrMessage(String freshNonce, Pcr[] pcrValues) {
		this.nonce = freshNonce;
		this.setValues(pcrValues);
	}
	
	public String getNonce() {
		return nonce;
	}

	public void setNonce(String nonce) {
		this.nonce = nonce;
	}

	public Map<Integer, String> getValues() {
		return values;
	}

	public void setValues(Map<Integer, String> localValues) {
		this.values = localValues;
	}

	public void setValues(Pcr[] newValues) {
		for(int i = 0; i < newValues.length; i++) {
			values.put(newValues[i].getNumber(), newValues[i].getValue());
		}
	}
	
}
