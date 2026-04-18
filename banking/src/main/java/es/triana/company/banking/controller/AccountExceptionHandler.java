package es.triana.company.banking.controller;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.service.exception.AccountNotFoundException;
import es.triana.company.banking.service.exception.AccountTypeNotFoundException;
import es.triana.company.banking.service.exception.AccountValidationException;
import es.triana.company.banking.service.exception.DuplicateAccountIbanException;
import es.triana.company.banking.service.exception.InstitutionNotFoundException;
import es.triana.company.banking.service.exception.TenantMismatchException;

@RestControllerAdvice(assignableTypes = AccountsController.class)
public class AccountExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AccountExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ApiResponse<Void> response = new ApiResponse<>(400, message, null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AccountValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountValidationException(AccountValidationException exception) {
        ApiResponse<Void> response = new ApiResponse<>(400, exception.getMessage(), null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountNotFoundException(AccountNotFoundException exception) {
        ApiResponse<Void> response = new ApiResponse<>(404, exception.getMessage(), null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler({AccountTypeNotFoundException.class, InstitutionNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleReferenceNotFoundExceptions(RuntimeException exception) {
        ApiResponse<Void> response = new ApiResponse<>(404, exception.getMessage(), null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(DuplicateAccountIbanException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateAccountIbanException(DuplicateAccountIbanException exception) {
        ApiResponse<Void> response = new ApiResponse<>(409, exception.getMessage(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(TenantMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantMismatchException(TenantMismatchException exception) {
        ApiResponse<Void> response = new ApiResponse<>(403, exception.getMessage(), null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException exception) {
        ApiResponse<Void> response = new ApiResponse<>(403, exception.getMessage(), null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException exception) {
        ApiResponse<Void> response = new ApiResponse<>(403, "Access denied", null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandledException(Exception exception) {
        LOG.error("Unhandled exception in AccountsController", exception);
        ApiResponse<Void> response = new ApiResponse<>(500, "Internal server error", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}