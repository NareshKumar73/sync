package com.source.open.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.source.open.exception.ResourceNotFoundException;
import com.source.open.payload.ApiMessage;
import com.source.open.util.NetworkUtil;

import lombok.RequiredArgsConstructor;

@CrossOrigin("*")
@RequiredArgsConstructor
@RestController
public class NetworkController {
	
	private final NetworkUtil nu;

	@GetMapping("/ip")
	public ResponseEntity<Map<String, String>> getLocalIP() {
		
		String ip =  nu.getLocalIpList()
						.entrySet().stream()
						.filter(entry -> entry.getValue() == true)
						.findFirst()
						.orElseThrow(() -> new ResourceNotFoundException("Couldn't confirm any ip as active."))
						.getKey();
		
		return ResponseEntity.ok(Collections.singletonMap("ip", ip));
	}

	@GetMapping("/ip/list")
	public ResponseEntity<Map<String,Boolean>> getLocalIPList() {
		return ResponseEntity.ok(nu.getLocalIpList());
	}

	@GetMapping("/ip/refresh")
	public ResponseEntity<Map<String,Boolean>> fetchLocalIp() {
		return ResponseEntity.ok(nu.fetchLocalIpList());
	}

	@GetMapping("/search")
	public ResponseEntity<ApiMessage> search() {
		
		nu.refreshServerList();
		
		return ResponseEntity.ok(new ApiMessage("Sending broadcast and waiting for reply.", true));
	}
	
	@GetMapping("/servers")
	public ResponseEntity<ApiMessage> servers() {
		
		List<String> serverUrls = new ArrayList<>();
		
		nu.getActiveNodes().forEach((host,port) -> {
			serverUrls.add("http://" + host + ":" + port + "/d");
		});
		
		return ResponseEntity.ok(new ApiMessage(serverUrls, true));
	}

//	@GetMapping("/check")
//	public ResponseEntity<ApiMessage> checkDownload() {
//		
//		return nu.downloadFileReactively("http://localhost:9005/resource?filecode=Mjg1NjU5X21hcmtlcl9tYXBfaWNvbi5zdmc", "map.svg")
//				.then(new ApiMessage("download complete", true));
//	}


}
