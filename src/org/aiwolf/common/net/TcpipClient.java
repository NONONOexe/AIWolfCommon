package org.aiwolf.common.net;

import java.io.BufferedReader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

//import net.arnx.jsonic.JSON;

import org.aiwolf.common.AIWolfRuntimeException;
import org.aiwolf.common.NoReturnObjectException;
import org.aiwolf.common.data.Player;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Talk;

/**
 * Client Using TCP/IP Connection
 * @author tori
 *
 */
public class TcpipClient implements Runnable, GameClient{

	String host;
	int port;
	
	Socket socket;
	
	Player player;
	Role requestRole;
	
	private boolean isRunning;
	
	GameInfo lastGameInfo;
	
	/**
	 * 
	 * @param host
	 * @param port
	 */
	public TcpipClient(String host, int port) {
		this.host = host;
		this.port = port;
	}
	
	/**
	 * 
	 * @param host
	 * @param port
	 * @param requestRole
	 */
	public TcpipClient(String host, int port, Role requestRole) {
		this.host = host;
		this.port = port;
		this.requestRole = requestRole;
	}
	
	public boolean connect(Player player){
		this.player = player;
		
//	    String name = "Agent"+System.currentTimeMillis()%1000;

	    try {
	        // ソケットを作成してサーバに接続する。
	        socket = new Socket();
	        socket.connect(new InetSocketAddress(host, port));

	        Thread th = new Thread(this);
	        th.start();
	        
	        // サーバと接続されたソケットを利用して処理を行う。
//	        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
//	        bw.append("test "+name);
//	        bw.close();
	        
	        return true;
	        
	    } catch (Exception e) {
	       e.printStackTrace();
	       return false;
	    }
	    
	}

	@Override
	public void run() {

		try{
	        // サーバと接続されたソケットを利用して処理を行う。
	        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
	        BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	        String line;
	        isRunning = true;
	        while((line = br.readLine()) != null && isRunning){
//	        	System.out.println(line);
	    		Packet packet = DataConverter.getInstance().toPacket(line);
	    		
	        	Object obj = recieve(packet);
	        	if(packet.getRequest().hasReturn()){
	        		if(obj == null){
	        			bw.append("\n");
//	        			throw new NoReturnObjectException(player+" "+obj);
	        		}
	        		else if(obj instanceof String){
	        			bw.append(obj+"\n");
	        		}
	        		else{
//	    	        	System.err.println(packet.getRequest());
	        			bw.append(DataConverter.getInstance().convert(obj)+"\n");
	        		}
	        		bw.flush();
	        	}
	        }
	        System.out.println("Close connectoin of "+player);
	        br.close();
	        bw.close();
	        socket.close();
		}catch(IOException e){
			throw new AIWolfRuntimeException(e);
		}finally{
			isRunning = false;
		}
	}

	/* (非 Javadoc)
	 * @see org.aiwolf.server.sc.LocalConnectServer#recieve(org.aiwolf.server.sc.Packet)
	 */
	@Override
	public Object recieve(Packet packet) {
		
		GameInfo gameInfo = lastGameInfo;
		GameSetting gameSetting = packet.getGameSetting();

		if(packet.getGameInfo() != null){
			gameInfo = packet.getGameInfo().toGameInfo();
			lastGameInfo = gameInfo;
		}
		
		if(packet.getTalkHistory() != null){
			Talk lastTalk = null;
			if(gameInfo.getTalkList() != null && !gameInfo.getTalkList().isEmpty()){
				lastTalk = gameInfo.getTalkList().get(gameInfo.getTalkList().size()-1);
			}
			for(TalkToSend talk:packet.getTalkHistory()){
				if(isAfter(talk, lastTalk)){
					gameInfo.talkList.add(talk.toTalk());
				}
			}
		}
		
		if(packet.getWhisperHistory() != null){
			Talk lastWhisper = null;
			if(gameInfo.getWhisperList() != null && !gameInfo.getWhisperList().isEmpty()){
				lastWhisper = gameInfo.getWhisperList().get(gameInfo.getWhisperList().size()-1);
			}
			for(TalkToSend whisper:packet.getWhisperHistory()){
				if(isAfter(whisper, lastWhisper)){
					gameInfo.whisperList.add(whisper.toTalk());
				}
			}
		}

		
		Object returnObject = null;
		switch(packet.getRequest()){
		case INITIALIZE:
			player.initialize(gameInfo, gameSetting);
//			player.update(gameInfo);
			break;
		case DAILY_INITIALIZE:
			player.update(gameInfo);
			player.dayStart();
			break;
		case DAILY_FINISH:
			player.update(gameInfo);
			break;
		case NAME:
			returnObject = player.getName();
			break;
		case ROLE:
			if(requestRole != null){
				returnObject = requestRole.toString();
			}
			else{
				returnObject = "none";
			}
			break;
		case ATTACK:
			player.update(gameInfo);
			returnObject = player.attack();
			break;
		case TALK:
			player.update(gameInfo);
			returnObject = player.talk();
			if(returnObject == null){
				returnObject = Talk.SKIP;
			}
			break;
		case WHISPER:
			player.update(gameInfo);
			returnObject = player.whisper();
			if(returnObject == null){
				returnObject = Talk.SKIP;
			}
			break;
		case DIVINE:
			player.update(gameInfo);
			returnObject = player.divine();
			break;
		case GUARD:
			player.update(gameInfo);
			returnObject = player.guard();
			break;
		case VOTE:
			player.update(gameInfo);
			returnObject = player.vote();
			break;
		case FINISH:
			player.update(gameInfo);
			finish();
		default:
			break;
		}
		return returnObject;
	}

	/**
	 * Check is talk after lastTalk.<br>
	 * If it is same, return false;
	 * @param talk
	 * @param lastTalk
	 * @return
	 */
	private boolean isAfter(TalkToSend talk, Talk lastTalk) {
		if(lastTalk != null){
			if(talk.getDay() < lastTalk.getDay()){
				return false;
			}
			if(talk.getDay() == lastTalk.getDay() && talk.getIdx() <= lastTalk.getIdx()){
				return false;
			}
		}
		return true;
	}

	protected void finish() {
		isRunning = false;
		player.finish();
	}

	/**
	 * @return host
	 */
	public String getHost() {
		return host;
	}

	/**
	 * @param host セットする host
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * @return port
	 */
	public int getPort() {
		return port;
	}

	/**
	 * @param port セットする port
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * @return requestRole
	 */
	public Role getRequestRole() {
		return requestRole;
	}

	/**
	 * @param requestRole セットする requestRole
	 */
	public void setRequestRole(Role requestRole) {
		this.requestRole = requestRole;
	}


	
	
	
}
