package com.xiaobai.workorder.modules.auth.controller;

import com.xiaobai.workorder.common.response.ApiResponse;
import com.xiaobai.workorder.modules.auth.dto.LoginRequest;
import com.xiaobai.workorder.modules.auth.dto.LoginResponse;
import com.xiaobai.workorder.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Authentication APIs")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Web/Admin login")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
