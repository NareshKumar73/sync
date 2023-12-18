package com.source.open;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class Ping {
	
	public static void main(String[] args) throws IOException {
//		Server Configuration
		DatagramChannel server = DatagramChannel.open();
		
		server.setOption(StandardSocketOptions.SO_BROADCAST, true);
		
		InetAddress localHost = InetAddress.getLocalHost();
		
		InetSocketAddress serverAddress = new InetSocketAddress(localHost, 12345);
		
		server.bind(serverAddress);
		
//		Client Configuration
		DatagramChannel client = DatagramChannel.open();

//		As client doesn't need to listen for incoming packets then we bind it to null
		client.bind(null);
		
//		Sending Message
		String message = "WE ARE ONE";
		
	    ByteBuffer clientBuffer = ByteBuffer.wrap(message.getBytes());

	    System.out.println("SENDING MESSAGE: " + message);
	    
		InetSocketAddress broadcastAddress = new InetSocketAddress("255.255.255.255", 12345);
		
		client.setOption(StandardSocketOptions.SO_BROADCAST, true);
	    
	    client.send(clientBuffer, broadcastAddress);	
	    
//	    Receiving Message
	    ByteBuffer serverBuffer = ByteBuffer.allocate(1024);
	    
	    SocketAddress sender = server.receive(serverBuffer);
	    
	    serverBuffer.flip();
	    
	    byte[] bytes = new byte[serverBuffer.remaining()];
	    
	    serverBuffer.get(bytes);
	    
	    String notification = new String(bytes);
	    
	    System.out.println("Client at #" + sender + "  sent: " + notification);
	    
	    client.close();
	    server.close();
	}
	
//	public static void main(String[] args) throws IOException {
////		Server Configuration
//		DatagramChannel server = DatagramChannel.open();
//		
//		InetAddress localHost = InetAddress.getLocalHost();
//		
//		InetSocketAddress serverAddress = new InetSocketAddress(localHost, 12345);
//		
//		server.bind(serverAddress);
//		
////		Client Configuration
//		DatagramChannel client = DatagramChannel.open();
//
////		As client doesn't need to listen for incoming packets then we bind it to null
//		client.bind(null);
//		
////		Sending Message
//		String message = "WE ARE ONE";
//		
//	    ByteBuffer clientBuffer = ByteBuffer.wrap(message.getBytes());
//
//	    System.out.println("SENDING MESSAGE: " + message);
//	    
//	    client.send(clientBuffer, serverAddress);
//	    
//	    
////	    Receiving Message
//	    ByteBuffer serverBuffer = ByteBuffer.allocate(1024);
//	    
//	    SocketAddress sender = server.receive(serverBuffer);
//	    
//	    serverBuffer.flip();
//	    
//	    byte[] bytes = new byte[serverBuffer.remaining()];
//	    
//	    serverBuffer.get(bytes);
//	    
//	    String notification = new String(bytes);
//	    
//	    System.out.println("Client at #" + sender + "  sent: " + notification);
//	    
//	    client.close();
//	    server.close();
//	}


}
