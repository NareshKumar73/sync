package com.source.open.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

import com.source.open.util.FileService;

import lombok.RequiredArgsConstructor;

@CrossOrigin("*")
@RequiredArgsConstructor
@Controller
public class PageController {
	
	private final FileService fs;
	
	@GetMapping("/d")
	public String download(Model model) {
		model.addAttribute("files", fs.refreshFileList());
		return "index";
	}
	
	@GetMapping("/u")
	public String upload() {
		return "upload";
	}


}
