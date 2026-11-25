package com.source.open.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.source.open.payload.FileListJson;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class NetworkUtil {

	private RestClient client;

//	THIS DEVICE IP LIST - IP & BOOLEAN INDICATING IF IT IS REACHABLE FROM LAN
	private final ConcurrentHashMap<String, Boolean> localIpList = new ConcurrentHashMap<>();

	private final int serverPort;

	public NetworkUtil(@Value("${server.port}") Integer serverPort) {
		this.serverPort = serverPort;
		client = RestClient.create();
	}

	public Map<String, Boolean> getLocalIpList() {
		if (localIpList.isEmpty()) {
			return fetchLocalIpList();
		}
		return localIpList;
	}

//	FETCH ALL LOCAL IP AND STORE FOR LATER USE
	public Map<String, Boolean> fetchLocalIpList() {

		Map<String, Boolean> map = new HashMap<>();

		try {
			Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

			while (en.hasMoreElements()) {
				NetworkInterface ni = en.nextElement();

				Enumeration<InetAddress> ipList = ni.getInetAddresses();

				while (ipList.hasMoreElements()) {
					InetAddress ip = ipList.nextElement();

					if (ip.isSiteLocalAddress())
						map.put(ip.getHostAddress(), false);
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}

		if (map.isEmpty())
			System.out.println("No site local ip found. Are you connected to any access point?");

		localIpList.clear();
		localIpList.putAll(map);

		System.out.println("IP found in LAN\n" + localIpList);

		return map;
	}

	public Map<String, String> getSystemIpMap() {

		Map<String, String> ipMap = new LinkedHashMap<>();

		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

			while (interfaces.hasMoreElements()) {
				NetworkInterface ni = interfaces.nextElement();

				// Skip down or loopback interfaces
				if (!ni.isUp() || ni.isLoopback()) {
					continue;
				}

				Enumeration<InetAddress> addresses = ni.getInetAddresses();

				while (addresses.hasMoreElements()) {
					InetAddress addr = addresses.nextElement();

					// Skip IPv6 if you don't want them
					if (addr instanceof Inet6Address) {
						continue;
					}

					ipMap.put(ni.getDisplayName(), addr.getHostAddress());
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return ipMap;
	}

	public FileListJson fetchFileList(String url) {

		return client.get().uri(url + "/files/refresh").accept(MediaType.APPLICATION_JSON).retrieve()
				.body(FileListJson.class);
	}

}
