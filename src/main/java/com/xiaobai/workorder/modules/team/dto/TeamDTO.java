package com.xiaobai.workorder.modules.team.dto;

import lombok.Data;

import java.util.List;

@Data
public class TeamDTO {
    private Long id;
    private String teamCode;
    private String teamName;
    private String description;
    private Long leaderId;
    private String leaderName;
    private String status;
    private List<MemberInfo> members;

    @Data
    public static class MemberInfo {
        private Long userId;
        private String employeeNumber;
        private String realName;
    }
}
