package br.ufu.facom.network.dlop.message;

public class DlopMessage {
	//Constants
	//public static final int HEADER_SIZE=22; //6 MAC_SRC, 6 MAC_DEST, 8 802.1Q, 2 ETHER-TYPE
	public static final int MAX_FRAME_SIZE=1500;

	private String label;
	private String source;
	private String destination;
	private byte[] payload;
	private int vlan;
	private String sequence;
	private boolean fragmented;
	
	public DlopMessage(String source, String destination, int  vlan, byte[] payload, boolean fragmented, String sequence, String label){
		this.label = label;
		this.source = source;
		this.destination = destination;
		this.payload = payload;
		this.vlan = vlan;
		this.sequence = sequence;
		this.fragmented = fragmented;
		this.sequence = sequence;
	}
	
	
	public DlopMessage(String source, String destination,int vlan, byte[] payload, String label) {
		this(source,destination,vlan, payload,false,"0",label);
	}

	public DlopMessage(String source, String destination,int vlan, byte[] payload, boolean fragmented) {
		this(source,destination,vlan, payload,fragmented,"0","FinMessage");
	}

	public DlopMessage(String source, String destination, byte[] payload, boolean fragmented) {
		this(source,destination,0, payload,fragmented,"0","FinMessage");
	}

	public DlopMessage(String source, String destination, byte[] payload, boolean fragmented, String sequence) {
		this(source,destination,0, payload,fragmented,sequence,"FinMessage");
	}

	public DlopMessage(String source, String destination,int vlan, byte[] payload, boolean fragmented, String sequence){
		this(source,destination,vlan, payload,fragmented,sequence,"FinMessage");
	}

	public DlopMessage(String source, String destination, byte[] payload) {
		this(source,destination,0, payload,false,"0","FinMessage");
	}


	public DlopMessage(String source, String destination, int vlan, byte[] payload) {
		this(source,destination,vlan, payload,false,"0","FinMessage");
	}


	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public byte[] getPayload() {
		return payload;
	}

	public void setPayload(byte[] payload) {
		this.payload = payload;
	}

	public int getVlan() {
		return vlan;
	}
	
	public void setVlan(int vlan) {
		this.vlan = vlan;
	}

	public void merge(DlopMessage currentMsgObj) {
		byte[] payload1 = this.getPayload();
		byte[] payload2 = currentMsgObj.getPayload();
		byte payloadMerged[] = new byte[payload1.length + payload2.length];
		
		System.arraycopy(payload1, 0, payloadMerged, 0, payload1.length);
		System.arraycopy(payload2, 0, payloadMerged, payload1.length, payload2.length);
		
		this.payload = payloadMerged;
		this.fragmented = currentMsgObj.fragmented;
	}

	public void setFragmented(boolean fragmented) {
		this.fragmented = fragmented;
	}
	
	public boolean isFragmented() {
		return fragmented;
	}

	public String getSequence(){
		return this.sequence;
	}
	
	public void setSequence(String sequence){
		this.sequence = sequence;
	}
}
