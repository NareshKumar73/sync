package com.source.open.payload;

import java.nio.file.Path;

import org.springframework.http.MediaType;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class FileMeta {

	private String url;
	private String code;
	private String name;
	private String relativePath;
	private String size;
	private String lastModified;

	private long sizeInBytes;

	private long lastModifiedEpoch;

	private MediaType fileType;

	@JsonIgnore
	private Path path;
}
