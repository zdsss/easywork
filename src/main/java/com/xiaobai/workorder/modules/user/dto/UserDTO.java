package com.xiaobai.workorder.modules.user.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String employeeNumber;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String role;
    private String status;
}
