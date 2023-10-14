package com.source.open;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.RequiredArgsConstructor;

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
		model.addAttribute("files", fs.getLocalFilesList());
		return "index";
	}

	@GetMapping("/upload-page")
	public String upload() {
		return "file-upload";
	}

}
