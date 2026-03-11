package com.xiaobai.workorder.common.exception;

import com.xiaobai.workorder.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleBusinessException_returns400WithMessage() {
        BusinessException ex = new BusinessException("Something went wrong");
        ApiResponse<Void> response = handler.handleBusinessException(ex);
        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("Something went wrong");
    }

    @Test
    void handleBusinessException_withCustomCode_returnsCustomCode() {
        BusinessException ex = new BusinessException(403, "Forbidden action");
        ApiResponse<Void> response = handler.handleBusinessException(ex);
        assertThat(response.getCode()).isEqualTo(403);
        assertThat(response.getMessage()).isEqualTo("Forbidden action");
    }

    @Test
    void handleValidationException_returns400WithFieldErrors() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "employeeNumber", "Employee number is required"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ApiResponse<Map<String, String>> response = handler.handleValidationException(ex);
        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("Validation failed");
    }

    @Test
    void handleGenericException_returns500() {
        RuntimeException ex = new RuntimeException("Unexpected failure");
        ApiResponse<Void> response = handler.handleException(ex);
        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("Internal server error");
    }
}
