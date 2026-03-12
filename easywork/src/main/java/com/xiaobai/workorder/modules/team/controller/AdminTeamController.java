package com.xiaobai.workorder.modules.team.controller;

import com.xiaobai.workorder.common.response.ApiResponse;
import com.xiaobai.workorder.common.util.SecurityUtils;
import com.xiaobai.workorder.modules.team.dto.CreateTeamRequest;
import com.xiaobai.workorder.modules.team.dto.TeamDTO;
import com.xiaobai.workorder.modules.team.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Admin - Teams", description = "Admin APIs for team management")
@RestController
@RequestMapping("/api/admin/teams")
@RequiredArgsConstructor
public class AdminTeamController {

    private final TeamService teamService;
    private final SecurityUtils securityUtils;

    @Operation(summary = "Create a new team")
    @PostMapping
    public ApiResponse<TeamDTO> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(teamService.createTeam(request, userId));
    }

    @Operation(summary = "List all teams")
    @GetMapping
    public ApiResponse<List<TeamDTO>> listTeams() {
        return ApiResponse.success(teamService.listTeams());
    }

    @Operation(summary = "Add members to a team")
    @PostMapping("/{teamId}/members")
    public ApiResponse<Void> addMembers(
            @PathVariable Long teamId,
            @RequestBody Map<String, List<Long>> body) {
        List<Long> userIds = body.get("userIds");
        if (userIds == null || userIds.isEmpty()) {
            return ApiResponse.success("No members to add", null);
        }
        teamService.addMembers(teamId, userIds);
        return ApiResponse.success("Members added", null);
    }

    @Operation(summary = "Remove a member from a team")
    @DeleteMapping("/{teamId}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @PathVariable Long teamId,
            @PathVariable Long userId) {
        teamService.removeMember(teamId, userId);
        return ApiResponse.success("Member removed", null);
    }
}
