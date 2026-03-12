package com.xiaobai.workorder.modules.team.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.team.dto.CreateTeamRequest;
import com.xiaobai.workorder.modules.team.dto.TeamDTO;
import com.xiaobai.workorder.modules.team.entity.Team;
import com.xiaobai.workorder.modules.team.entity.TeamMember;
import com.xiaobai.workorder.modules.team.repository.TeamMapper;
import com.xiaobai.workorder.modules.team.repository.TeamMemberMapper;
import com.xiaobai.workorder.modules.user.entity.User;
import com.xiaobai.workorder.modules.user.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;

    @Transactional
    public TeamDTO createTeam(CreateTeamRequest request, Long createdBy) {
        if (teamMapper.findByTeamCode(request.getTeamCode()).isPresent()) {
            throw new BusinessException("Team code already exists: " + request.getTeamCode());
        }
        Team team = new Team();
        team.setTeamCode(request.getTeamCode());
        team.setTeamName(request.getTeamName());
        team.setDescription(request.getDescription());
        team.setLeaderId(request.getLeaderId());
        team.setStatus("ACTIVE");
        team.setCreatedBy(createdBy);
        teamMapper.insert(team);

        if (request.getMemberIds() != null) {
            for (Long memberId : request.getMemberIds()) {
                TeamMember member = new TeamMember();
                member.setTeamId(team.getId());
                member.setUserId(memberId);
                member.setJoinedAt(LocalDateTime.now());
                teamMemberMapper.insert(member);
            }
        }

        log.info("Team {} created by user {}", team.getTeamCode(), createdBy);
        return toDTO(team);
    }

    @Transactional
    public void addMembers(Long teamId, List<Long> userIds) {
        Team team = teamMapper.selectById(teamId);
        if (team == null || team.getDeleted() == 1) {
            throw new BusinessException("Team not found: " + teamId);
        }
        for (Long userId : userIds) {
            boolean exists = !teamMemberMapper.selectList(
                    new LambdaQueryWrapper<TeamMember>()
                            .eq(TeamMember::getTeamId, teamId)
                            .eq(TeamMember::getUserId, userId)
                            .eq(TeamMember::getDeleted, 0)).isEmpty();
            if (!exists) {
                TeamMember member = new TeamMember();
                member.setTeamId(teamId);
                member.setUserId(userId);
                member.setJoinedAt(LocalDateTime.now());
                teamMemberMapper.insert(member);
            }
        }
    }

    public List<TeamDTO> listTeams() {
        List<Team> teams = teamMapper.selectList(
                new LambdaQueryWrapper<Team>().eq(Team::getDeleted, 0));
        return teams.stream().map(this::toDTO).toList();
    }

    @Transactional
    public void removeMember(Long teamId, Long userId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null || team.getDeleted() == 1) {
            throw new BusinessException("Team not found: " + teamId);
        }
        teamMemberMapper.delete(
            new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
        );
    }

    private TeamDTO toDTO(Team team) {
        TeamDTO dto = new TeamDTO();
        dto.setId(team.getId());
        dto.setTeamCode(team.getTeamCode());
        dto.setTeamName(team.getTeamName());
        dto.setDescription(team.getDescription());
        dto.setLeaderId(team.getLeaderId());
        dto.setStatus(team.getStatus());

        if (team.getLeaderId() != null) {
            User leader = userMapper.selectById(team.getLeaderId());
            if (leader != null) dto.setLeaderName(leader.getRealName());
        }

        List<TeamMember> members = teamMemberMapper.findByTeamId(team.getId());
        dto.setMembers(members.stream().map(m -> {
            TeamDTO.MemberInfo info = new TeamDTO.MemberInfo();
            info.setUserId(m.getUserId());
            User user = userMapper.selectById(m.getUserId());
            if (user != null) {
                info.setEmployeeNumber(user.getEmployeeNumber());
                info.setRealName(user.getRealName());
            }
            return info;
        }).toList());

        return dto;
    }
}
