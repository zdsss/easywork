package com.xiaobai.workorder.modules.team.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateTeamRequest {
    @NotBlank(message = "Team code is required")
    private String teamCode;

    @NotBlank(message = "Team name is required")
    private String teamName;

    private String description;
    private Long leaderId;
    private List<Long> memberIds;
}
