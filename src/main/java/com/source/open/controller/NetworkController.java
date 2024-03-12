package com.source.open.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.source.open.exception.ResourceNotFoundException;
import com.source.open.payload.ApiMessage;
import com.source.open.util.NetworkUtil;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@CrossOrigin("*")
@RequiredArgsConstructor
@RestController
public class NetworkController {
	
	private final NetworkUtil nu;

	@GetMapping("/ip")
	public Mono<Map<String, String>> getLocalIP() {
		
		String ip =  nu.getLocalIpList()
						.entrySet().stream()
						.filter(entry -> entry.getValue() == true)
						.findFirst()
						.orElseThrow(() -> new ResourceNotFoundException("Couldn't confirm any ip as active."))
						.getKey();
		
		return Mono.just(Collections.singletonMap("ip", ip));
	}

	@GetMapping("/ip/list")
	public Mono<Map<String,Boolean>> getLocalIPList() {
		return Mono.just(nu.getLocalIpList());
	}

	@GetMapping("/ip/refresh")
	public Mono<Map<String,Boolean>> fetchLocalIp() {
		return Mono.just(nu.fetchLocalIpList());
	}

	@GetMapping("/search")
	public Mono<ApiMessage> search() {
		
		nu.refreshServerList();
		
		return Mono.just(new ApiMessage("Sending broadcast and waiting for reply.", true));
	}
	
	@GetMapping("/servers")
	public Mono<ApiMessage> servers() {
		
		List<String> serverUrls = new ArrayList<>();
		
		nu.getActiveNodes().forEach((host,port) -> {
			serverUrls.add("http://" + host + ":" + port + "/d");
		});
		
		return Mono.just(new ApiMessage(serverUrls, true));
	}

	@GetMapping("/check")
	public Mono<ApiMessage> checkDownload() {
		
		return nu.downloadFileReactively("http://localhost:9005/resource?filecode=Mjg1NjU5X21hcmtlcl9tYXBfaWNvbi5zdmc", "map.svg")
				.then(Mono.just(new ApiMessage("download complete", true)));
	}


}
