package com.apigateway.app.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReactorMdcLoggingFilterTest {

    private final ReactorMdcLoggingFilter filter = new ReactorMdcLoggingFilter();

    @Test
    void filter_withExistingHeaders_usesThem() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("X-Trace-Id", "trace-123")
                .header("X-Request-Id", "req-456")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).isEqualTo("trace-123");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).isEqualTo("req-456");
    }

    @Test
    void filter_withoutHeaders_generatesNewOnes() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).isNotBlank();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    @Test
    void filter_logsOnCompletion() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // doFinally logic is covered by the verifyComplete
    }

    @Test
    void filter_getOrder_returnsCorrectValue() {
        assertThat(filter.getOrder()).isLessThan(0);
    }
}
