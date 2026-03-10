package com.xiaobai.workorder.modules.mesintegration.event;

import lombok.Getter;
import com.xiaobai.workorder.modules.inspection.entity.InspectionRecord;
import org.springframework.context.ApplicationEvent;

/**
 * Published after an inspection record is successfully persisted.
 * Consumed by MesEventListener to push the result to MES.
 */
@Getter
public class InspectionRecordSavedEvent extends ApplicationEvent {

    private final Long inspectionRecordId;
    private final InspectionRecord inspectionRecord;

    public InspectionRecordSavedEvent(Object source, Long inspectionRecordId,
                                       InspectionRecord inspectionRecord) {
        super(source);
        this.inspectionRecordId = inspectionRecordId;
        this.inspectionRecord = inspectionRecord;
    }
}
