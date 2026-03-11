package com.xiaobai.workorder.modules.user.service;

import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.user.dto.CreateUserRequest;
import com.xiaobai.workorder.modules.user.dto.UserDTO;
import com.xiaobai.workorder.modules.user.entity.User;
import com.xiaobai.workorder.modules.user.repository.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserService userService;

    @Test
    void createUser_newEmployee_returnsUserDTO() {
        when(userMapper.findByEmployeeNumber("EMP002")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        doAnswer(inv -> { ((User) inv.getArgument(0)).setId(2L); return null; })
                .when(userMapper).insert(any(User.class));

        CreateUserRequest req = new CreateUserRequest();
        req.setEmployeeNumber("EMP002");
        req.setUsername("emp002");
        req.setPassword("password");
        req.setRealName("Employee Two");
        req.setRole("WORKER");

        UserDTO dto = userService.createUser(req);

        assertThat(dto.getEmployeeNumber()).isEqualTo("EMP002");
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void createUser_duplicateEmployeeNumber_throwsBusinessException() {
        User existing = new User();
        existing.setEmployeeNumber("EMP001");
        when(userMapper.findByEmployeeNumber("EMP001")).thenReturn(Optional.of(existing));

        CreateUserRequest req = new CreateUserRequest();
        req.setEmployeeNumber("EMP001");
        req.setUsername("emp001");
        req.setPassword("password");

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void findByEmployeeNumber_existingEmployee_returnsUser() {
        User user = new User();
        user.setId(1L);
        user.setEmployeeNumber("EMP001");
        when(userMapper.findByEmployeeNumber("EMP001")).thenReturn(Optional.of(user));

        User result = userService.findByEmployeeNumber("EMP001");

        assertThat(result.getEmployeeNumber()).isEqualTo("EMP001");
    }

    @Test
    void findByEmployeeNumber_notFound_throwsBusinessException() {
        when(userMapper.findByEmployeeNumber("EMP999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByEmployeeNumber("EMP999"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void findById_deletedUser_throwsBusinessException() {
        User user = new User();
        user.setId(5L);
        user.setDeleted(1);
        when(userMapper.selectById(5L)).thenReturn(user);

        assertThatThrownBy(() -> userService.findById(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("User not found");
    }
}
