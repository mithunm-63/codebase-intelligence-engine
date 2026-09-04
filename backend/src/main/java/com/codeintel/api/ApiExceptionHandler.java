package com.codeintel.api;

import java.util.Map;

import com.codeintel.ingestion.IngestionException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, String> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return Map.of("error", "Uploaded repository exceeds the configured request size limit.");
    }

    @ExceptionHandler(IngestionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIngestion(IngestionException ex) {
        return Map.of("error", ex.getMessage());
    }
}
