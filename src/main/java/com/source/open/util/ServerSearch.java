package com.source.open.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class ServerSearch implements Runnable {

	private final NetworkUtil nu;

	private byte[] buf = new byte[5];

	public ServerSearch(NetworkUtil nu) {
		this.nu = nu;
	}

	@Override
	public void run() {

		DatagramPacket dp = null;
//		GET ALL LOCAL IP FOR OTHER SERVER DISCOVERY OVER ALL PATHS

		while (!nu.getUdpServer().isClosed()) {
			try {
				dp = new DatagramPacket(buf, buf.length);

				nu.getUdpServer().receive(dp);

				System.out.println("Received: " + new String(dp.getData(), StandardCharsets.US_ASCII));

			} catch (IOException e) {
				e.printStackTrace();
//				IF ERROR OCCURED THEN NO FURTHEN PROCESSING JUST 
				continue;
			}

			InetAddress remoteAddress = dp.getAddress();

			String remoteIp = remoteAddress.getHostAddress();

			if (nu.getLocalIpList().containsKey(remoteIp)) {
				nu.getLocalIpList().put(remoteIp, true);
				System.out.println("It's my ip: " + remoteIp);
			}

			if (!nu.getActiveNodes().containsKey(remoteIp)) {

				nu.getActiveNodes().put(remoteIp, Integer.parseInt(new String(dp.getData(), 0, dp.getLength(), StandardCharsets.US_ASCII)));

				byte[] msg = nu.getMsg();

				dp = new DatagramPacket(msg, msg.length, remoteAddress, nu.getUdpPort());

				try {
					nu.getUdpServer().send(dp);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			System.out.println("SERVERS: " + nu.getActiveNodes());
		}
		
		System.out.println("EXITED SUCCESSFULLY");

		nu.getUdpServer().close();
	}

}
