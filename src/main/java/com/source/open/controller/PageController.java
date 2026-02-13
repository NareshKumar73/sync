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

import lombok.RequiredArgsConstructor;

@CrossOrigin("*")
@RequiredArgsConstructor
@Controller
public class PageController {

	private final FileService fs;

	private final NetworkUtil nu;

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

	@GetMapping("/u")
	public String upload() {
		return "upload";
	}

}
