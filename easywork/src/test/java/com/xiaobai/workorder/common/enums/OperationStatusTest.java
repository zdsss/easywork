package com.xiaobai.workorder.common.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies OperationStatus enum values match V1.8 CHECK constraint.
 */
class OperationStatusTest {

    @Test
    void enumValuesShouldMatchDatabaseCheckConstraint() {
        Set<String> enumValues = Arrays.stream(OperationStatus.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        Set<String> dbConstraintValues = Set.of(
                "NOT_STARTED", "STARTED", "REPORTED", "INSPECTED",
                "TRANSPORTED", "HANDLED", "COMPLETED"
        );

        assertEquals(dbConstraintValues, enumValues,
                "Enum values must match V1.8 CHECK constraint");
    }

    @Test
    void shouldContainAllRequiredStatuses() {
        assertTrue(Arrays.asList(OperationStatus.values()).contains(OperationStatus.NOT_STARTED));
        assertTrue(Arrays.asList(OperationStatus.values()).contains(OperationStatus.STARTED));
        assertTrue(Arrays.asList(OperationStatus.values()).contains(OperationStatus.REPORTED));
        assertTrue(Arrays.asList(OperationStatus.values()).contains(OperationStatus.INSPECTED));
        assertTrue(Arrays.asList(OperationStatus.values()).contains(OperationStatus.TRANSPORTED));
        assertTrue(Arrays.asList(OperationStatus.values()).contains(OperationStatus.HANDLED));
        assertTrue(Arrays.asList(OperationStatus.values()).contains(OperationStatus.COMPLETED));
    }
}
