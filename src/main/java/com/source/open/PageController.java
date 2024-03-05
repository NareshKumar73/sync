package com.source.open;

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
	
	@GetMapping("/")
	public String home() {
		return "index";
	}
	
	@GetMapping("/download-page")
	public String download(Model model) {
		model.addAttribute("files", fs.refreshFileList());
		return "index";
	}

	@GetMapping("/upload-page")
	public String upload() {
		return "file-upload";
	}

	@GetMapping("/simple")
	public String simpleUpload() {
		return "simple-upload";
	}

	@GetMapping("/drop")
	public String drop() {
		return "file-drop1";
	}
	
	@GetMapping("/u")
	public String u() {
		return "upload";
	}


}
