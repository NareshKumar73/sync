package com.source.open.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.source.open.exception.ResourceNotFoundException;
import com.source.open.payload.ApiMessage;
import com.source.open.payload.InstanceNode;
import com.source.open.util.NetworkUtil;
import com.source.open.util.InstanceNodeRepository;
import com.source.open.util.SyncService;
import com.source.open.payload.FileMeta;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@CrossOrigin("*")
@RequiredArgsConstructor
@RestController
public class NetworkController {
	
	private final NetworkUtil nu;
	private final InstanceNodeRepository nodeRepository;
	private final SyncService syncService;
	private final RestClient restClient = RestClient.create();

	@GetMapping("/ip")
	public ResponseEntity<Map<String, String>> getLocalIP() {
		String ip = nu.getLocalIpList()
				.entrySet().stream()
				.filter(Map.Entry::getValue)
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

	@GetMapping("/ip/system")
	public ResponseEntity<Map<String, String>> getSystemIpMap() {
		return ResponseEntity.ok(nu.getSystemIpMap());
	}

	@GetMapping("/api/nodes")
	public ResponseEntity<List<InstanceNode>> getNodes() {
		return ResponseEntity.ok(nodeRepository.findAll());
	}

	@PostMapping("/api/nodes")
	public ResponseEntity<?> addNode(@RequestBody InstanceNode node) {
		Optional<InstanceNode> existing = nodeRepository.findByIpAddressAndPort(node.getIpAddress(), node.getPort());
		if (existing.isPresent()) {
			return ResponseEntity.badRequest().body(new ApiMessage("Node already exists.", false));
		}
		
		boolean isWorking = testConnection(node.getIpAddress(), node.getPort());
		node.setIsWorking(isWorking);
		if (isWorking) {
			node.setLastActive(LocalDateTime.now());
		}
		
		InstanceNode saved = nodeRepository.save(node);
		return ResponseEntity.ok(saved);
	}

	@DeleteMapping("/api/nodes/{id}")
	public ResponseEntity<ApiMessage> deleteNode(@PathVariable Long id) {
		nodeRepository.deleteById(id);
		return ResponseEntity.ok(new ApiMessage("Node deleted successfully.", true));
	}

	@PostMapping("/api/nodes/test")
	public ResponseEntity<ApiMessage> testNode(@RequestBody InstanceNode node) {
		boolean isWorking = testConnection(node.getIpAddress(), node.getPort());
		if (node.getId() != null) {
			nodeRepository.findById(node.getId()).ifPresent(existing -> {
				existing.setIsWorking(isWorking);
				if (isWorking) {
					existing.setLastActive(LocalDateTime.now());
				}
				nodeRepository.save(existing);
			});
		}
		if (isWorking) {
			return ResponseEntity.ok(new ApiMessage("Connection successful.", true));
		} else {
			return ResponseEntity.ok(new ApiMessage("Connection failed.", false));
		}
	}

	private boolean testConnection(String ip, Integer port) {
		try {
			String url = "http://" + ip + ":" + port + "/api/ping";
			ResponseEntity<String> response = restClient.get()
					.uri(url)
					.retrieve()
					.toEntity(String.class);
			return response.getStatusCode().is2xxSuccessful();
		} catch (Exception e) {
			return false;
		}
	}

	@GetMapping("/api/ping")
	public ResponseEntity<String> ping() {
		return ResponseEntity.ok("pong");
	}

	@PostMapping("/api/sync/trigger")
	public ResponseEntity<ApiMessage> triggerSync() {
		syncService.triggerSync();
		return ResponseEntity.ok(new ApiMessage("Sync triggered.", true));
	}

	@PostMapping("/api/sync/toggle")
	public ResponseEntity<ApiMessage> toggleAutoSync(@RequestParam boolean enabled) {
		syncService.setAutoSync(enabled);
		return ResponseEntity.ok(new ApiMessage("Auto sync " + (enabled ? "enabled" : "disabled") + ".", true));
	}

	@GetMapping("/api/sync/status")
	public ResponseEntity<Map<String, Boolean>> getSyncStatus() {
		return ResponseEntity.ok(Collections.singletonMap("enabled", syncService.isAutoSyncEnabled()));
	}

	@GetMapping("/api/sync/conflicts")
	public ResponseEntity<Map<String, FileMeta>> getConflicts() {
		return ResponseEntity.ok(syncService.getConflicts());
	}

	@PostMapping("/api/sync/resolve")
	public ResponseEntity<ApiMessage> resolveConflict(@RequestParam String relativePath, @RequestParam String baseUrl) {
		syncService.resolveConflict(relativePath, baseUrl);
		return ResponseEntity.ok(new ApiMessage("Conflict resolved.", true));
	}

	@GetMapping("/api/speedtest/download")
	public ResponseEntity<StreamingResponseBody> speedTestDownload(@RequestParam(defaultValue = "10") int sizeMB) {
		int totalBytes = sizeMB * 1024 * 1024;
		StreamingResponseBody stream = out -> {
			byte[] buffer = new byte[8192];
			int written = 0;
			while (written < totalBytes) {
				int toWrite = Math.min(buffer.length, totalBytes - written);
				out.write(buffer, 0, toWrite);
				written += toWrite;
			}
		};
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
				.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(totalBytes))
				.body(stream);
	}

	@PostMapping("/api/speedtest/upload")
	public ResponseEntity<ApiMessage> speedTestUpload(@RequestParam("file") MultipartFile file) {
		// Spring automatically buffers to temp and cleans up the multipart file after request completes.
		return ResponseEntity.ok(new ApiMessage("Upload speed test successful, temp data discarded.", true));
	}
}
