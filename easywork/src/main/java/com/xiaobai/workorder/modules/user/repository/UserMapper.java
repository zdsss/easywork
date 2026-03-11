package com.xiaobai.workorder.modules.user.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaobai.workorder.modules.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    default Optional<User> findByEmployeeNumber(String employeeNumber) {
        return Optional.ofNullable(
                selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getEmployeeNumber, employeeNumber)
                        .eq(User::getDeleted, 0)));
    }
}
