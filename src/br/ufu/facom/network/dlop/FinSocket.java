package br.ufu.facom.network.dlop;
import java.util.HashMap;
import java.util.Map;

import br.ufu.facom.network.dlop.message.DlopParser;
import br.ufu.facom.network.dlop.message.DlopMessage;
import br.ufu.facom.network.dlop.message.DlopSimpleParser;
import br.ufu.facom.network.etcp.message.EtcpService;
import br.ufu.facom.network.etcp.message.EtcpServiceMessage;
import br.ufu.facom.network.etcp.message.EtcpServiceRequest;
import br.ufu.facom.network.etcp.message.EtcpServiceResponse;
import br.ufu.facom.network.etcp.parser.EtcpSerializedParser;
import br.ufu.facom.network.etcp.parser.EtcpServiceMessageParser;
import br.ufu.facom.network.etcp.util.DtsConstants;

public class FinSocket {
	public static String interfaceDefault;
	public static boolean printDebug=true;
	
	
	//Carrega a biblioteca libFinSocket.so
    static {  
    	System.loadLibrary("FinSocket");  
    }

	//Functional
	private int sock = -1;
	private boolean promisc = false;

	//Non-Function
	private boolean registered;
	private String title="guest"+((int)(Math.random()*1000));
	private HashMap<String,Integer> workspaces;

	private int ifIndex = -1;
	private String ifName = null;
	
	//Campos disprezíveis
	private int dtsVlanDefault=2;
	
	//Parser
	private static DlopParser parser = new DlopSimpleParser();
	private static EtcpServiceMessageParser serviceParser = new EtcpSerializedParser();

	private boolean reading;
	
	//Métodos nativos
	private native int finOpen();

	private native boolean finClose(int sock);

	private native boolean finWrite(int ifIndex, int sock, byte[] data, int offset, int len);

	private native int finRead(int sock, byte[] data, int offset, int len);

	private native boolean setPromiscousMode(String ifName, int sock);
	
	private native Map<Integer,String> getNetIfs();
	
    //Interface
	public boolean close(){
		return finClose(sock);
	}

	public boolean open(){
		this.sock = finOpen();
		this.workspaces = new HashMap<String,Integer>();
		
		Map<Integer,String> ifs = getNetIfs();
		for(Integer index : ifs.keySet()){
			String name = ifs.get(index); 
			if(!name.startsWith("lo")){
				if(interfaceDefault == null || interfaceDefault.equalsIgnoreCase(name)){
					ifIndex = index;
					ifName = name;
					debug("Interface utilizada: "+ifIndex+" - "+ifName);
					break;
				}
			}
		}
		
		return isOpenned();
	}
	
	private boolean isOpenned() {
		return this.sock >= 0 && ifIndex >=0;  
	}

	public boolean write(String destin, byte[] msg){
		if(workspaces.containsKey(destin) || destin.equals("DTS")){
			for(DlopMessage message : parser.fragmentMessage(this.title,destin.equals("DTS")?dtsVlanDefault:workspaces.get(destin),destin,msg)){
				if(!write(message))
					return false;
			}
			return true;
		}else{
			throw new RuntimeException("Destino invalido!");
		}
	}
	
	public boolean write(DlopMessage message){
		if(isOpenned()){
			String destin = message.getDestination();
			
			if(workspaces.containsKey(destin) || destin.equals("DTS")){
				debug("Sending FinFrame:"+message.getSequence());
				byte[] bytes = parser.parse(message);
				return finWrite(ifIndex, sock, bytes, 0, bytes.length);
			}else{
				throw new RuntimeException("Workspace invalido!");
			}
		}else{
			throw new RuntimeException("FinSocket não aberto!");
		}
	}


	private int writeBuffer(byte[] buffer, int bufferIndex, byte[] target, int targetIndex, int targetSize) {
		for(int i=targetIndex; i<targetSize; i++ ){
			buffer[bufferIndex++] = target[i];
		}
		return bufferIndex;
	}
	
	public DlopMessage read(){
		reading = true;
		if(isOpenned()){
			if(!promisc)
				if(!setPromiscousMode(ifName,sock)){
					System.err.println("Não foi possível colocar a interface em modo promíscuo.");
					return null;
				}else
					promisc = true;
	
			DlopMessage msgObj = null;
			
			while(reading){
				byte bytes[] = new byte[DlopMessage.MAX_FRAME_SIZE];
				
				int offset = finRead(sock,bytes,0,DlopMessage.MAX_FRAME_SIZE);
	
				if(parser.validStartMessage(bytes)){
					if(parser.validEndMessage(bytes,offset)){ // Message Complete
						DlopMessage currentMsgObj = parser.parseMessage(bytes);
						
						if(currentMsgObj != null && (workspaces.containsKey(currentMsgObj.getDestination()) || title.equals(currentMsgObj.getDestination())) ){
							if(msgObj != null){
								debug("Merging message... FinFrame:"+currentMsgObj.getSequence());
								msgObj.merge(currentMsgObj);
							}else
								msgObj = currentMsgObj;
							
							if(!msgObj.isFragmented()){
								debug("Receiving Message... FinFrame:"+msgObj.getSequence());
								return msgObj;
							}
						}else{
							if(currentMsgObj == null)
								debug("Parse Fail!");
							else
								debug("Workspace fail : " + currentMsgObj.getDestination());
							currentMsgObj=null;
						}
					}else{
						debug("Incorrect Message!");
					}
				}else{ // Não é uma mensagem FinLan
					debug("Non-Finlan message!");
				}
			}
		}else{
			throw new RuntimeException("FinSocket não aberto!");
		}
		
		return null;
	}
	
	//DTS Interaction
	public boolean register(String title){
		EtcpServiceRequest request = new EtcpServiceRequest(EtcpService.ENTITY_REGISTER);
		request.putProperty("title",title);
		
	
		if(write(DtsConstants.dtsWorkspace,serviceParser.unmarshall(request))){
			
			while(true){
				DlopMessage inMessage = read();
			
				EtcpServiceMessage serviceMessage = serviceParser.marshall(inMessage.getPayload());
				if(serviceMessage != null && serviceMessage instanceof EtcpServiceResponse){
					if(((EtcpServiceResponse)serviceMessage).isSucess()){
						this.title = title;
						this.registered = true;
						
						debug("Register sucess");
						
						return true;
					}else{
						debug("Register fail: "+((EtcpServiceResponse)serviceMessage).getMessage());
					}
					return false;
				}else{
					debug("Not Service response");
				}
				
			}
		}
		
		return false;
	}
	
	public boolean unregister(){
		EtcpServiceRequest request = new EtcpServiceRequest(EtcpService.ENTITY_UNREGISTER);
		request.putProperty("title",title);
		
	
		if(write(DtsConstants.dtsWorkspace,serviceParser.unmarshall(request))){
			while(true){
				DlopMessage inMessage = read();
			
				EtcpServiceMessage serviceMessage = serviceParser.marshall(inMessage.getPayload());
				if(serviceMessage != null && serviceMessage instanceof EtcpServiceResponse){
					if(((EtcpServiceResponse)serviceMessage).isSucess()){
						debug("Unregister sucess");
						this.title = "";
						this.registered = false;
					}else{
						debug("Unregister fail: "+((EtcpServiceResponse)serviceMessage).getMessage());
					}
					
					return false;
				}else{
					debug("Not Service response");
				}
			}
		}
		
		return false;
	}
	
	public boolean joinWorkspace(String workspace){
		if(!this.workspaces.containsKey(workspace)){
			EtcpServiceRequest request = new EtcpServiceRequest(EtcpService.WORKSPACE_ATTACH);
			request.putProperty("title",workspace);
			
		
			if(write(DtsConstants.dtsWorkspace,serviceParser.unmarshall(request))){
				while(true){
					DlopMessage inMessage = read();
				
					EtcpServiceMessage serviceMessage = serviceParser.marshall(inMessage.getPayload());
					if(serviceMessage != null && serviceMessage instanceof EtcpServiceResponse){
						if(((EtcpServiceResponse)serviceMessage).isSucess()){
							workspaces.put(workspace, Integer.parseInt(((EtcpServiceResponse)serviceMessage).getReturnValue().toString()));
							return true;
						}
						
						return false;
					}
				}
			}
			
		}
		return false;
	}
	
	public boolean disjoinWorkspace(String workspace){
		if(this.workspaces.containsKey(workspace)){
			EtcpServiceRequest request = new EtcpServiceRequest(EtcpService.WORKSPACE_DETACH);
			request.putProperty("title",workspace);
		
		
			if(write(DtsConstants.dtsWorkspace,serviceParser.unmarshall(request))){
				while(true){
					DlopMessage inMessage = read();
				
					EtcpServiceMessage serviceMessage = serviceParser.marshall(inMessage.getPayload());
					if(serviceMessage != null && serviceMessage instanceof EtcpServiceResponse){
						if(((EtcpServiceResponse)serviceMessage).isSucess()){
							this.workspaces.remove(workspace);
							return true;
						}
						
						return false;
					}
				}
			}
		}
		return false;
	}
	
	public boolean createWorkspace(String workspace){
		EtcpServiceRequest request = new EtcpServiceRequest(EtcpService.WORKSPACE_CREATE);
		request.putProperty("title",workspace);
		
	
		if(write(DtsConstants.dtsWorkspace,serviceParser.unmarshall(request))){
			while(true){
				DlopMessage inMessage = read();
			
				EtcpServiceMessage serviceMessage = serviceParser.marshall(inMessage.getPayload());
				if(serviceMessage != null && serviceMessage instanceof EtcpServiceResponse){
					if(((EtcpServiceResponse)serviceMessage).isSucess()){
						return true;
					}
					
					return false;
				}
			}
		}
			
		return false;
	}
	
	public boolean deleteWorkspace(String workspace){
		EtcpServiceRequest request = new EtcpServiceRequest(EtcpService.WORKSPACE_DELETE);
		request.putProperty("title",workspace);
		
	
		if(write(DtsConstants.dtsWorkspace,serviceParser.unmarshall(request))){
			while(true){
				DlopMessage inMessage = read();
			
				EtcpServiceMessage serviceMessage = serviceParser.marshall(inMessage.getPayload());
				if(serviceMessage != null && serviceMessage instanceof EtcpServiceResponse){
					if(((EtcpServiceResponse)serviceMessage).isSucess()){
						return true;
					}
					return false;
				}
			}
		}
			
		return false;
	}

	//Function to print the debug
	private void debug(String info) {
		if(printDebug)
			System.out.println(info);
	}

	/**
	 * Mocks 
	 */
	public void setTitle(String title) {
		this.title = title;
		this.registered = true;
	}

	public void addWorkspace(String titleWs) {
		this.workspaces.put(titleWs,2);
	}
	
	public void setStopRead(){
		reading=false;
	}
} 
