package com.xiaobai.workorder.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Employee number is required")
    private String employeeNumber;

    @NotBlank(message = "Password is required")
    private String password;

    private String deviceCode;
}
