package com.xiaobai.workorder.config;

import com.xiaobai.workorder.common.enums.WorkOrderType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Work order behavior configuration.
 * Bound from the {@code app.workorder} prefix in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "app.workorder")
public class WorkOrderProperties {

    /**
     * Per-order-type flag: when true for a given type, workers must call startWork
     * before calling reportWork. Defaults to false for all types if not configured.
     *
     * Example in application.yml:
     * <pre>
     * app:
     *   workorder:
     *     force-start-before-report:
     *       PRODUCTION: false
     *       INSPECTION: false
     *       TRANSPORT: false
     *       ANDON: false
     * </pre>
     */
    private Map<String, Boolean> forceStartBeforeReport = new HashMap<>();

    public Map<String, Boolean> getForceStartBeforeReport() {
        return forceStartBeforeReport;
    }

    public void setForceStartBeforeReport(Map<String, Boolean> forceStartBeforeReport) {
        this.forceStartBeforeReport = forceStartBeforeReport;
    }

    /**
     * Returns whether force-start-before-report is enabled for the given order type.
     * Defaults to {@code false} if the order type is null or not configured.
     */
    public boolean isForceStartBeforeReport(WorkOrderType orderType) {
        if (orderType == null) {
            return false;
        }
        return forceStartBeforeReport.getOrDefault(orderType.name(), false);
    }
}
