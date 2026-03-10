package com.xiaobai.workorder.modules.team.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaobai.workorder.modules.team.entity.TeamMember;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TeamMemberMapper extends BaseMapper<TeamMember> {

    default List<TeamMember> findByTeamId(Long teamId) {
        return selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getDeleted, 0));
    }

    default List<TeamMember> findByUserId(Long userId) {
        return selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getDeleted, 0));
    }
}
