package com.source.open.controller;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.source.open.payload.FileListJson;
import com.source.open.payload.FileMeta;
import com.source.open.payload.FileRequest;
import com.source.open.util.FileService;
import com.source.open.util.NetworkUtil;
import com.source.open.util.TransferHistoryRepository;
import com.source.open.payload.TransferHistory;

import lombok.RequiredArgsConstructor;

@CrossOrigin("*")
@RequiredArgsConstructor
@Controller
public class PageController {

	private final FileService fs;

	private final NetworkUtil nu;

	private final TransferHistoryRepository transferHistoryRepository;
	private final com.source.open.util.NetworkTrafficRepository networkTrafficRepository;

	@GetMapping({ "/", "/d" })
	public String browse(@RequestParam(required = false) String path, Model model) {

		List<FileMeta> list = fs.listDirectory(path);

		FileListJson json = new FileListJson(list, list.size());

		Map<String, String> ipMap = nu.getSystemIpMap();

		System.out.println(ipMap);

		model.addAttribute("local", json);
		model.addAttribute("form", new FileRequest());
		model.addAttribute("ips", ipMap);
		model.addAttribute("pwd", path == null ? "" : path);

		return "index";
	}

	@GetMapping("/history")
	public String transferHistory(Model model) {
		List<TransferHistory> rawHistory = transferHistoryRepository.findAllByOrderByTimestampDesc();
		
		List<TransferHistory> historyList = new java.util.ArrayList<>();
		for (TransferHistory th : rawHistory) {
			boolean merged = false;
			for (TransferHistory c : historyList) {
				if (java.util.Objects.equals(c.getIpAddress(), th.getIpAddress()) &&
					java.util.Objects.equals(c.getFilename(), th.getFilename()) &&
					java.util.Objects.equals(c.getType(), th.getType()) && 
					c.getTimestamp() != null && th.getTimestamp() != null) {
					
					long hoursDiff = java.time.Duration.between(th.getTimestamp(), c.getTimestamp()).toHours();
					if (Math.abs(hoursDiff) <= 1) {
						long newSize = (c.getFileSize() != null ? c.getFileSize() : 0) + (th.getFileSize() != null ? th.getFileSize() : 0);
						c.setFileSize(newSize);
						merged = true;
						break;
					}
				}
			}
			if (!merged) {
				TransferHistory clone = new TransferHistory();
				clone.setId(th.getId());
				clone.setType(th.getType());
				clone.setFilename(th.getFilename());
				clone.setIpAddress(th.getIpAddress());
				clone.setFileSize(th.getFileSize() != null ? th.getFileSize() : 0);
				clone.setTimestamp(th.getTimestamp());
				historyList.add(clone);
			}
		}

		Map<String, Long> ipTransfers = new java.util.HashMap<>();
		Map<String, Long> fileDownloads = new java.util.HashMap<>();
		long totalUploaded = 0;
		long totalDownloaded = 0;

		for (TransferHistory th : historyList) {
			if (th.getFileSize() != null) {
				ipTransfers.put(th.getIpAddress(), ipTransfers.getOrDefault(th.getIpAddress(), 0L) + th.getFileSize());
				if ("MANUAL_DOWNLOAD".equals(th.getType()) || "SYNC_JOB".equals(th.getType())) {
					fileDownloads.put(th.getFilename(), fileDownloads.getOrDefault(th.getFilename(), 0L) + th.getFileSize());
					totalDownloaded += th.getFileSize();
				} else if ("MANUAL_UPLOAD".equals(th.getType())) {
					totalUploaded += th.getFileSize();
				}
			}
		}

		List<Map<String, String>> topIpsFormatted = ipTransfers.entrySet().stream()
			.sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
			.limit(5)
			.map(e -> Map.of("ip", e.getKey(), "size", formatSize(e.getValue())))
			.collect(java.util.stream.Collectors.toList());

		List<Map<String, String>> topFilesFormatted = fileDownloads.entrySet().stream()
			.sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
			.limit(5)
			.map(e -> Map.of("filename", e.getKey(), "size", formatSize(e.getValue())))
			.collect(java.util.stream.Collectors.toList());

		List<com.source.open.payload.NetworkTraffic> networkTrafficList = networkTrafficRepository.findAll();
		List<Map<String, Object>> networkTrafficFormatted = networkTrafficList.stream().map(t -> {
			Map<String, Object> map = new java.util.HashMap<>();
			map.put("id", t.getId());
			map.put("ipAddress", t.getIpAddress());
			map.put("bytesSentFormatted", formatSize(t.getBytesSent() != null ? t.getBytesSent() : 0));
			map.put("bytesReceivedFormatted", formatSize(t.getBytesReceived() != null ? t.getBytesReceived() : 0));
			map.put("lastActive", t.getLastActive());
			return map;
		}).collect(java.util.stream.Collectors.toList());

		model.addAttribute("historyList", historyList);
		model.addAttribute("rawHistoryList", rawHistory);
		model.addAttribute("networkTrafficList", networkTrafficFormatted);
		model.addAttribute("topIps", topIpsFormatted);
		model.addAttribute("topFiles", topFilesFormatted);
		model.addAttribute("totalUploaded", formatSize(totalUploaded));
		model.addAttribute("totalDownloaded", formatSize(totalDownloaded));
		model.addAttribute("totalTransfers", historyList.size());
		model.addAttribute("uniqueIps", ipTransfers.size());

		return "history";
	}

	private String formatSize(long size) {
		if (size <= 0) return "0 B";
		if (size < 1024) return size + " B";
		int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
		return String.format("%.1f %sB", (double)size / (1L << (z * 10)), " KMGTPE".charAt(z));
	}



}
