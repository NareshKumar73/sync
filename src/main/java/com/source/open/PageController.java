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
	
//	private final String content = 
//			"Superman and Supergirl take on the cybernetic being " + 
//			"known as Brainiac, who boasts that he possesses the knowledge " + 
//			"and strength of 10,000 worlds.";
	
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

	
//	@GetMapping(value = "/piece", produces = MediaType.TEXT_PLAIN_VALUE)
//	public Mono<ServerResponse> piece(ServerHttpRequest request) {
//
//		System.out.println("REQUEST:\n" + request.getHeaders());
//
//		List<HttpRange> rangeList = request.getHeaders().getRange();
//
//		System.out.println("RANGE: " + rangeList);
//
//		HttpHeaders headers = new HttpHeaders();
//
//		headers.setContentType(MediaType.TEXT_PLAIN);
//		headers.add("Accept-Ranges", "bytes");
//
////		headers.setAccessControlAllowOrigin("*");
//
//		if (!rangeList.isEmpty()) {
//			
//			
//			HttpRange hr = rangeList.get(0);
//			
//			int start = Long.valueOf(hr.getRangeStart(content.length())).intValue();
//			int end   = Long.valueOf(hr.getRangeEnd(content.length())).intValue();
//
//			headers.add("Content-Range", "bytes " + start  + "-" + end  + "/" + content.length());
//
//			headers.setContentLength(end - start + 1);
//			
////			Flux<DataBuffer> flux = DataBufferUtils.read( new ByteArrayResource(content.getBytes()), response.bufferFactory(), 2048);
//
//			System.out.println("RESPONSE PARTIAL:\n" + headers);
//
//			return ServerResponse.status(206).headers(h -> h.addAll(headers)).bodyValue(content.substring(start, end+1));
//
////			return ResponseEntity
////					.status(HttpStatus.PARTIAL_CONTENT)
////					.headers(response.getHeaders())
////					.body(resourceRegions.get(0));
//		}
//		
//		headers.add("Content-Range", "bytes 0-" + (content.length() - 1) + "/" + content.length());
//
//		headers.setContentLength(content.length());
//
//		System.out.println("RESPONSE FULL:\n" + headers);
//
//		return ServerResponse.status(200).headers(h -> h.addAll(headers)).bodyValue(content.substring(0, content.length()));
//
////		return response
////				.writeWith(Mono.just(response.bufferFactory().wrap(content.getBytes())));
//		
////		return ResponseEntity.ok(new ResourceRegion(new ByteArrayResource(content.getBytes()), 0, content.length()));
//	}

}
