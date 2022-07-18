package com.maxis.ossisdp.controller;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.maxis.ossisdp.payload.Response;
import com.maxis.ossisdp.util.ApplicationConstants;

import lombok.extern.log4j.Log4j2;

@ControllerAdvice
@Log4j2
public class CustomRestExceptionHandler extends ResponseEntityExceptionHandler {

	 @Override
	    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(final HttpRequestMethodNotSupportedException ex, final HttpHeaders headers, final HttpStatus status, final WebRequest request) {
	        log.info(((ServletWebRequest)request).getRequest().getRequestURI().toString());
	        Response maxisResponse = new Response();
			maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
			maxisResponse.setReturnDescription("HTTP Method Not supported");
			SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
			maxisResponse.setReturnDateTime(format.format(new Date()));
	        return new ResponseEntity<Object>(maxisResponse, new HttpHeaders(), HttpStatus.METHOD_NOT_ALLOWED);
	    }
}
