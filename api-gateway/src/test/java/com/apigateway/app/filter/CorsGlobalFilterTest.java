package com.apigateway.app.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CorsGlobalFilterTest {

    private final CorsGlobalFilter corsGlobalFilter = new CorsGlobalFilter();
    private final WebFilter filter = corsGlobalFilter.corsFilter();

    @Test
    void corsFilter_optionsRequest_returnsOkAndStops() {
        MockServerHttpRequest request = MockServerHttpRequest.options("/any").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = mock(WebFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
    }

    @Test
    void corsFilter_nonOptionsRequest_callsChain() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/any").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
    }
}

