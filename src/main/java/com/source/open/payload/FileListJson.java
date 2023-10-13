package com.source.open.payload;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class FileListJson {

	private List<FileMeta> files;
	private int totalElements;
	
}
