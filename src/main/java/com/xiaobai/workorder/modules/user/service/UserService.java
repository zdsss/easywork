package com.xiaobai.workorder.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.user.dto.CreateUserRequest;
import com.xiaobai.workorder.modules.user.dto.UserDTO;
import com.xiaobai.workorder.modules.user.entity.User;
import com.xiaobai.workorder.modules.user.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public User findByEmployeeNumber(String employeeNumber) {
        return userMapper.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() -> new BusinessException("User not found: " + employeeNumber));
    }

    public User findById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException("User not found: " + userId);
        }
        return user;
    }

    @Transactional
    public UserDTO createUser(CreateUserRequest request) {
        if (userMapper.findByEmployeeNumber(request.getEmployeeNumber()).isPresent()) {
            throw new BusinessException("Employee number already exists: " + request.getEmployeeNumber());
        }
        User user = new User();
        user.setEmployeeNumber(request.getEmployeeNumber());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole() != null ? request.getRole() : "WORKER");
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        return toDTO(user);
    }

    public List<UserDTO> listUsers(int page, int size) {
        Page<User> pageResult = userMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<User>().eq(User::getDeleted, 0).orderByAsc(User::getId));
        return pageResult.getRecords().stream().map(this::toDTO).toList();
    }

    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setEmployeeNumber(user.getEmployeeNumber());
        dto.setUsername(user.getUsername());
        dto.setRealName(user.getRealName());
        dto.setPhone(user.getPhone());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        return dto;
    }
}
