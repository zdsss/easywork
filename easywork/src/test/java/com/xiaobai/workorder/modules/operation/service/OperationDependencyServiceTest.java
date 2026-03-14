package com.xiaobai.workorder.modules.operation.service;

import com.xiaobai.workorder.modules.operation.entity.OperationDependency;
import com.xiaobai.workorder.modules.operation.repository.OperationDependencyMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationDependencyServiceTest {

    @Mock OperationDependencyMapper mapper;

    @InjectMocks OperationDependencyService service;

    @Test
    void testGetPredecessors_ReturnsCorrectUpstreamOperations() {
        // getPredecessors(B) should return records where predecessor_operation_id = B
        // i.e., records where B is listed as a predecessor (successors of B)
        OperationDependency dep = new OperationDependency();
        dep.setOperationId(3L);          // C depends on B
        dep.setPredecessorOperationId(2L); // B is the predecessor
        dep.setDependencyType("SERIAL");

        when(mapper.selectList(any())).thenReturn(List.of(dep));

        List<OperationDependency> result = service.getPredecessors(2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPredecessorOperationId()).isEqualTo(2L);
        assertThat(result.get(0).getOperationId()).isEqualTo(3L);
    }

    @Test
    void testGetDependencies_ReturnsDependenciesForOperation() {
        OperationDependency dep = new OperationDependency();
        dep.setOperationId(2L);
        dep.setPredecessorOperationId(1L);
        dep.setDependencyType("SERIAL");

        when(mapper.selectList(any())).thenReturn(List.of(dep));

        List<OperationDependency> result = service.getDependencies(2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOperationId()).isEqualTo(2L);
    }
}
