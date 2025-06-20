package com.source.open.exception;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

@Getter
@Setter
public class ResourceNotFoundException extends RuntimeException {

    /**
     * @author Naresh Kumar
     */
    @Serial
    private static final long serialVersionUID = 1L;

	String resourceName;
	String fieldName;
	String fieldValue;
	
	public ResourceNotFoundException(String message) {
		super(message);
		this.resourceName = message;
	}

	public ResourceNotFoundException(String resourceName, String fieldName, long fieldValue) {
		super("%s not found with %s: %d".formatted(resourceName, fieldName, fieldValue));
		this.resourceName = resourceName;
		this.fieldName = fieldName;
		this.fieldValue = fieldValue + "";
	}

	public ResourceNotFoundException(String resourceName, String fieldName, String fieldValue) {
		super("%s not found with %s: %s".formatted(resourceName, fieldName, fieldValue));
		this.resourceName = resourceName;
		this.fieldName = fieldName;
		this.fieldValue = fieldValue;
	}

}
