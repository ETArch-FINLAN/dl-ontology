package br.ufu.facom.network.dlop.message;

import java.util.List;


public interface DlopParser {
	byte[] parse(DlopMessage message);
	
	DlopMessage parseMessage(byte[] data);

	boolean validStartMessage(byte[] bytes);
	boolean validEndMessage(byte[] bytes, int offset);
	List<DlopMessage> fragmentMessage(String title, int vlan, String destin, byte[] msg);
}
