package com.source.open.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.Setter;

@Getter
@Service
public class NetworkUtil {


//	THIS DEVICE IP LIST - IP & IS IT REACHABLE FROM LAN
	private final ConcurrentHashMap<String, Boolean> localIpList = new ConcurrentHashMap<>();

//	DEVICE OVER LAN WITH THIS SOFTWARE - IP & PORT
	private final ConcurrentHashMap<String, Integer> activeNodes = new ConcurrentHashMap<>();
	
//	DEVICE OVER LAN WITH THIS SOFTWARE INSTALLED
	private final ConcurrentLinkedQueue<String> syncServers = new ConcurrentLinkedQueue<>();

	private DatagramSocket udpServer;

	private InetAddress broadcastIp;

	private Integer udpPort;

	@Value("${server.port}")
	private Integer serverPort;

	@Setter
	private boolean needed = true;
	

	public NetworkUtil(@Value("${udp.port:50505}") Integer udpPort) {
		try {
			this.udpPort = udpPort;
			udpServer = new DatagramSocket(udpPort);
			udpServer.setSoTimeout(15000);
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
	

//	public DatagramSocket getUdpServer() {
//
//		if (udpServer == null) {
//			try {
//				udpServer = new DatagramSocket(udpPort);
//				udpServer.setSoTimeout(15000);
//			} catch (SocketException e) {
//				e.printStackTrace();
//			}
//		}
//
//		return udpServer;
//	}

	public InetAddress getBroadcastIp() {
		if (broadcastIp == null)
			try {
				broadcastIp = InetAddress.getByAddress(new byte[] { (byte) 255, (byte) 255, (byte) 255, (byte) 255 });
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}

		return broadcastIp;
	}

	public void sendEcho() {
		try {
			byte[] msg = (udpPort + "").getBytes();
			getUdpServer().send(new DatagramPacket(msg, msg.length, getBroadcastIp(), udpPort));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

//	FETCH ALL LOCAL IP AND STORE FOR LATER USE
	public void discoverLocalIpList() {

		try {
			Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

			while (en.hasMoreElements()) {
				NetworkInterface ni = en.nextElement();

				Enumeration<InetAddress> ipList = ni.getInetAddresses();

				while (ipList.hasMoreElements()) {
					InetAddress ip = ipList.nextElement();

					if (ip.isSiteLocalAddress())
						localIpList.put(ip.getHostAddress(), false);
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}

		System.out.println("IP found in LAN\n" + localIpList);
	}

}
