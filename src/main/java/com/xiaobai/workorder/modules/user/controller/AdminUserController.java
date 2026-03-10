package com.xiaobai.workorder.modules.user.controller;

import com.xiaobai.workorder.common.response.ApiResponse;
import com.xiaobai.workorder.modules.user.dto.CreateUserRequest;
import com.xiaobai.workorder.modules.user.dto.UserDTO;
import com.xiaobai.workorder.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Users", description = "Admin APIs for user management")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "Create a new user")
    @PostMapping
    public ApiResponse<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success(userService.createUser(request));
    }

    @Operation(summary = "List all users")
    @GetMapping
    public ApiResponse<List<UserDTO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(userService.listUsers(page, size));
    }
}
