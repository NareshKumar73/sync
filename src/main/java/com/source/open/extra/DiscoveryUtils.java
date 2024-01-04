package com.source.open.extra;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
public class DiscoveryUtils {

	private final int port;

	@Getter
	@Setter
	private DatagramSocket ds;
	
	private InetAddress broadcastIp;
	
	public DiscoveryUtils(@Value("${server.port}") int port) {
		this.port = port + 1;
	}	

	public DatagramSocket getRunningServer() {
		
		if (ds == null) {
			try {
				if (broadcastIp == null)
					broadcastIp = InetAddress.getByAddress(new byte[] { (byte) 255, (byte) 255, (byte) 255, (byte) 255 });
				
				ds = new DatagramSocket(port);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return ds;
	}
	
	public DatagramPacket startListening() {
		
		DatagramPacket dp = new DatagramPacket(new byte[16], 16);
		
		DatagramSocket ds = getRunningServer();

		try {
			ds.receive(dp);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return dp;
	}
	
	
	public void sendEcho() {
		DatagramSocket ds = getRunningServer();
		
		byte[] msg  = (port + "").getBytes();
		
		try {
			ds.send(new DatagramPacket(msg, msg.length, broadcastIp, port));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
