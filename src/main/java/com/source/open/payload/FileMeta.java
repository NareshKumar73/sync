package com.source.open.payload;

import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class FileMeta {
	
	private String filecode;
	private String name;
	private String size;
	private String LastModified;	

	private long sizeInBytes;
	@JsonIgnore
	private long lastModifiedEpoch;
	
	@JsonIgnore
	private Path path;
}
