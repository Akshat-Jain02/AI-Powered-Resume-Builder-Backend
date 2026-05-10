package com.resumeai.resumeservice.interceptor;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.Mockito.*;

class FeignAuthInterceptorTest {

    private FeignAuthInterceptor interceptor;

    @Mock private RequestTemplate template;
    @Mock private HttpServletRequest request;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        interceptor = new FeignAuthInterceptor();
    }

    @AfterEach
    void tearDown() throws Exception {
        RequestContextHolder.resetRequestAttributes();
        mocks.close();
    }

    @Test
    void apply_withHeaders_forwardsThem() {
        ServletRequestAttributes attrs = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attrs);

        when(request.getHeader("X-Username")).thenReturn("testuser");
        when(request.getHeader("X-Roles")).thenReturn("USER");

        interceptor.apply(template);

        verify(template).header("X-Username", "testuser");
        verify(template).header("X-Roles", "USER");
    }

    @Test
    void apply_noAttributes_doesNothing() {
        RequestContextHolder.resetRequestAttributes();
        interceptor.apply(template);
        verifyNoInteractions(template);
    }

    @Test
    void apply_noHeaders_doesNotAddHeaders() {
        ServletRequestAttributes attrs = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attrs);

        when(request.getHeader("X-Username")).thenReturn(null);
        when(request.getHeader("X-Roles")).thenReturn(null);

        interceptor.apply(template);

        verify(template, never()).header(anyString(), anyString());
    }
}
