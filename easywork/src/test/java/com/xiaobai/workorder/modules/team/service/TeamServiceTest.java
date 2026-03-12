package com.xiaobai.workorder.modules.team.service;

import com.xiaobai.workorder.common.exception.BusinessException;
import com.xiaobai.workorder.modules.team.dto.CreateTeamRequest;
import com.xiaobai.workorder.modules.team.dto.TeamDTO;
import com.xiaobai.workorder.modules.team.entity.Team;
import com.xiaobai.workorder.modules.team.entity.TeamMember;
import com.xiaobai.workorder.modules.team.repository.TeamMapper;
import com.xiaobai.workorder.modules.team.repository.TeamMemberMapper;
import com.xiaobai.workorder.modules.user.entity.User;
import com.xiaobai.workorder.modules.user.repository.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock TeamMapper teamMapper;
    @Mock TeamMemberMapper teamMemberMapper;
    @Mock UserMapper userMapper;

    @InjectMocks TeamService teamService;

    @Test
    void createTeam_newCode_insertsTeamAndReturnsDTO() {
        when(teamMapper.findByTeamCode("TEAM-A")).thenReturn(Optional.empty());
        doAnswer(inv -> { ((Team) inv.getArgument(0)).setId(1L); return null; })
                .when(teamMapper).insert(any(Team.class));
        when(teamMemberMapper.findByTeamId(1L)).thenReturn(List.of());

        CreateTeamRequest req = new CreateTeamRequest();
        req.setTeamCode("TEAM-A");
        req.setTeamName("Team Alpha");

        TeamDTO dto = teamService.createTeam(req, 1L);

        assertThat(dto.getTeamCode()).isEqualTo("TEAM-A");
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void createTeam_duplicateCode_throwsBusinessException() {
        Team existing = new Team();
        existing.setTeamCode("TEAM-A");
        when(teamMapper.findByTeamCode("TEAM-A")).thenReturn(Optional.of(existing));

        CreateTeamRequest req = new CreateTeamRequest();
        req.setTeamCode("TEAM-A");
        req.setTeamName("Team Alpha");

        assertThatThrownBy(() -> teamService.createTeam(req, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createTeam_withMembers_insertsTeamMembers() {
        when(teamMapper.findByTeamCode("TEAM-B")).thenReturn(Optional.empty());
        doAnswer(inv -> { ((Team) inv.getArgument(0)).setId(2L); return null; })
                .when(teamMapper).insert(any(Team.class));
        when(teamMemberMapper.findByTeamId(2L)).thenReturn(List.of());

        CreateTeamRequest req = new CreateTeamRequest();
        req.setTeamCode("TEAM-B");
        req.setTeamName("Team Beta");
        req.setMemberIds(List.of(10L, 11L));

        teamService.createTeam(req, 1L);

        verify(teamMemberMapper, times(2)).insert(any(TeamMember.class));
    }

    @Test
    void addMembers_teamNotFound_throwsBusinessException() {
        when(teamMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> teamService.addMembers(99L, List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Team not found");
    }

    @Test
    void removeMember_validIds_deletesRecord() {
        Team team = new Team();
        team.setId(1L);
        team.setDeleted(0);
        when(teamMapper.selectById(1L)).thenReturn(team);

        teamService.removeMember(1L, 10L);

        verify(teamMemberMapper).delete(any());
    }

    @Test
    void removeMember_teamNotFound_throwsException() {
        when(teamMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> teamService.removeMember(99L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Team not found");
    }
}
