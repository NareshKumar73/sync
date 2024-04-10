package com.source.open.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

import com.source.open.payload.FileListJson;
import com.source.open.payload.FileMeta;
import com.source.open.payload.FileRequest;
import com.source.open.util.FileService;

import lombok.RequiredArgsConstructor;

@CrossOrigin("*")
@RequiredArgsConstructor
@Controller
public class PageController {
	
	private final FileService fs;
	
	@GetMapping("/d")
	public String download(Model model) {
		
		List<FileMeta> list = fs.refreshFileList();
		
		FileListJson json = new FileListJson(list, list.size());
		
		model.addAttribute("local", json);
		model.addAttribute("form", new FileRequest());
		
		return "index";
	}
	
	@GetMapping("/u")
	public String upload() {
		return "upload";
	}


}
