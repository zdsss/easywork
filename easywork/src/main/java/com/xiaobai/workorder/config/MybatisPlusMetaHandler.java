package com.xiaobai.workorder.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.xiaobai.workorder.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MybatisPlusMetaHandler implements MetaObjectHandler {

    private final SecurityUtils securityUtils;

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        Long currentUserId = securityUtils.getCurrentUserId();
        if (currentUserId != null) {
            this.strictInsertFill(metaObject, "createdBy", Long.class, currentUserId);
            this.strictInsertFill(metaObject, "updatedBy", Long.class, currentUserId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        Long currentUserId = securityUtils.getCurrentUserId();
        if (currentUserId != null) {
            this.strictUpdateFill(metaObject, "updatedBy", Long.class, currentUserId);
        }
    }
}
