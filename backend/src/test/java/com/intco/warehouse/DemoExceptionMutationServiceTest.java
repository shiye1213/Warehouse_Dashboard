package com.intco.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.intco.warehouse.mapper.ExceptionEventMapper;
import com.intco.warehouse.service.DemoExceptionMutationService;
import com.intco.warehouse.service.DemoExceptionMutationService.MutationAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DemoExceptionMutationServiceTest {
    @Autowired
    private DemoExceptionMutationService mutationService;

    @Autowired
    private ExceptionEventMapper exceptionEventMapper;

    @Test
    void alternatesBetweenCreatingAndDeletingOneDemoException() {
        exceptionEventMapper.deleteById(DemoExceptionMutationService.DEMO_EVENT_ID);

        assertEquals(MutationAction.CREATED, mutationService.toggle());
        assertNotNull(exceptionEventMapper.selectById(DemoExceptionMutationService.DEMO_EVENT_ID));

        assertEquals(MutationAction.DELETED, mutationService.toggle());
        assertNull(exceptionEventMapper.selectById(DemoExceptionMutationService.DEMO_EVENT_ID));
    }
}
