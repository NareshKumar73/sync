package com.source.open.payload;

import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class FileMeta {

	private String url;

	private String name;
	private String code;

	private boolean directory;

	private String size;
	private long sizeInBytes;

	private String lastModified;
	private long lastModifiedEpoch;

	private String relativePath;

	@JsonIgnore
	private Path path;

	@JsonFormat(shape = Shape.STRING)
	private String fileType;
}
