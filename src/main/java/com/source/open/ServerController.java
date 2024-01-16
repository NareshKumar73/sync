package com.source.open;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.source.open.payload.ApiMessage;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@CrossOrigin("*")
@RequiredArgsConstructor
@RestController
public class ServerController {

	
	@GetMapping("/server")
	public Mono<ApiMessage> sync() {
		return Mono.just(new ApiMessage("SERVER DISCOVERY SERVICE", true));
	}
}
