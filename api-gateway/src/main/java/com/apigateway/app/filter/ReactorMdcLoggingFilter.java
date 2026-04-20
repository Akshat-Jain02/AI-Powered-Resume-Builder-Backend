package com.apigateway.app.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ReactorMdcLoggingFilter — MDC-aware logging for WebFlux / reactive pipelines.
 *
 * In reactive code, MDC cannot propagate automatically through Reactor scheduler
 * hops because each operator may run on a different thread. This filter:
 *   1. Extracts / generates traceId and requestId from incoming headers.
 *   2. Populates MDC on the subscriber thread for synchronous log statements.
 *   3. Forwards trace headers to downstream services.
 *   4. Logs request completion with status and latency.
 *
 * For fully reactive MDC propagation (e.g. inside flatMap / Mono chains),
 * use Reactor Context and retrieve values inside each operator explicitly.
 */
@Component
public class ReactorMdcLoggingFilter implements GlobalFilter, Ordered {

    private static final String TRACE_HEADER   = "X-Trace-Id";
    private static final String REQUEST_HEADER = "X-Request-Id";
    private static final String SERVICE_NAME   = "api-gateway";

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ReactorMdcLoggingFilter.class);

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 1; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request  = exchange.getRequest();
        String traceId   = resolveHeader(request, TRACE_HEADER);
        String requestId = resolveHeader(request, REQUEST_HEADER);
        long   start     = System.currentTimeMillis();

        // Mutate request to forward trace headers to downstream services
        ServerHttpRequest mutated = request.mutate()
                .header(TRACE_HEADER,   traceId)
                .header(REQUEST_HEADER, requestId)
                .build();

        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set(TRACE_HEADER,   traceId);
        response.getHeaders().set(REQUEST_HEADER, requestId);

        // Populate MDC for synchronous log statements in this operator chain
        MDC.put("traceId",   traceId);
        MDC.put("requestId", requestId);
        MDC.put("service",   SERVICE_NAME);
        MDC.put("method",    request.getMethod().name());
        MDC.put("uri",       request.getPath().value());

        return chain.filter(exchange.mutate().request(mutated).build())
                .doFinally(signal -> {
                    long elapsed = System.currentTimeMillis() - start;
                    MDC.put("traceId",   traceId);
                    MDC.put("requestId", requestId);
                    log.info("{} {} -> {} ({} ms)",
                            request.getMethod().name(),
                            request.getPath().value(),
                            response.getStatusCode() != null ? response.getStatusCode().value() : 0,
                            elapsed);
                    MDC.clear();
                });
    }

    private String resolveHeader(ServerHttpRequest request, String header) {
        String value = request.getHeaders().getFirst(header);
        return (value != null && !value.isBlank()) ? value : UUID.randomUUID().toString();
    }
}
