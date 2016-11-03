package de.fhg.ids.comm.ws.protocol.rat.tpm.objects;

public abstract class TPMU_SYM_MODE extends StandardTPMStruct {

	/*
	 * TPMU_SYM_MODE Union
	 * typedef union {
	 *     TPMI_ALG_SYM_MODE aes;
	 *     TPMI_ALG_SYM_MODE SM4;
	 *     TPMI_ALG_SYM_MODE sym;
	 * } TPMU_SYM_MODE;
	 */
	
	@Override
	public abstract byte[] toBytes();

	@Override
	public abstract void fromBytes(byte[] source, int offset);
	
	@Override
    public abstract String toString();	
}