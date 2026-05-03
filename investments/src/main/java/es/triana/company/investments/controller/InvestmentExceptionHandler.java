package es.triana.company.investments.controller;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.triana.company.investments.model.api.ApiResponse;
import es.triana.company.investments.service.exception.CatalogNotFoundException;
import es.triana.company.investments.service.exception.InvestmentNotFoundException;
import es.triana.company.investments.service.exception.InvestmentValidationException;

@RestControllerAdvice(assignableTypes = {
    InvestmentsController.class,
    OperationsController.class,
    PriceController.class,
    InvestmentCatalogController.class })
public class InvestmentExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, message, null));
    }

    @ExceptionHandler(InvestmentValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvestmentValidationException(InvestmentValidationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, exception.getMessage(), null));
    }

    @ExceptionHandler(InvestmentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvestmentNotFoundException(InvestmentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, exception.getMessage(), null));
    }

    @ExceptionHandler(CatalogNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCatalogNotFoundException(CatalogNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, exception.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandledException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "Internal server error", null));
    }
}
