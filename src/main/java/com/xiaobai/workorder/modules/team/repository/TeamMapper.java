package com.xiaobai.workorder.modules.team.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaobai.workorder.modules.team.entity.Team;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface TeamMapper extends BaseMapper<Team> {

    default Optional<Team> findByTeamCode(String teamCode) {
        return Optional.ofNullable(
                selectOne(new LambdaQueryWrapper<Team>()
                        .eq(Team::getTeamCode, teamCode)
                        .eq(Team::getDeleted, 0)));
    }
}
