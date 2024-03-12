package com.source.open.exception;

import java.io.FileNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.resource.NoResourceFoundException;

import com.fasterxml.jackson.core.JacksonException;
import com.source.open.payload.ApiMessage;

@RestControllerAdvice
public class GlobalExceptionHandler {
	
	@ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)	
	public ResponseEntity<ApiMessage> noResourceFoundExceptionHandler(NoResourceFoundException ex) {

		String message = ex.getMessage();

		ApiMessage apiMessage = new ApiMessage(message, false);
		return new ResponseEntity<>(apiMessage, HttpStatus.NOT_FOUND);
	}


	@ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)	
	public ResponseEntity<ApiMessage> resourceNotFoundExceptionHandler(ResourceNotFoundException ex) {

		String message = ex.getMessage();

		ApiMessage apiMessage = new ApiMessage(message, false);
		return new ResponseEntity<>(apiMessage, HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(FileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)	
	public ResponseEntity<ApiMessage> fileNotFoundExceptionHandler(FileNotFoundException ex) {

		ApiMessage response = new ApiMessage();

		response.setMessage(ex.getMessage());

		return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(JacksonException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)	
	public ResponseEntity<ApiMessage> jsonProcessingExceptionHandler(JacksonException ex) {

		String message = ex.getMessage();

		ApiMessage apiMessage = new ApiMessage(message, false);
		return new ResponseEntity<>(apiMessage, HttpStatus.BAD_REQUEST);
	}

}
