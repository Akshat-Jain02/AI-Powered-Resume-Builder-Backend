package com.apigateway.app.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
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
        final ServerHttpRequest req = exchange.getRequest();
        final String tId = resolveHeader(req, TRACE_HEADER);
        final String rId = resolveHeader(req, REQUEST_HEADER);
        final long startTimeMillis = System.currentTimeMillis();

        ServerHttpRequest mutatedRequest = req.mutate()
                .header(TRACE_HEADER, tId)
                .header(REQUEST_HEADER, rId)
                .build();

        exchange.getResponse().getHeaders().set(TRACE_HEADER, tId);
        exchange.getResponse().getHeaders().set(REQUEST_HEADER, rId);

        populateDiagnosticContext(tId, rId, req);

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - startTimeMillis;
                    MDC.put("traceId", tId);
                    MDC.put("requestId", rId);
                    
                    int status = (exchange.getResponse().getStatusCode() != null) ? exchange.getResponse().getStatusCode().value() : 0;
                    
                    log.info("GW-LOG: {} {} status: {} duration: {}ms",
                            req.getMethod(),
                            req.getPath().value(),
                            status,
                            duration);
                    MDC.clear();
                });
    }

    private void populateDiagnosticContext(String tid, String rid, ServerHttpRequest request) {
        MDC.put("traceId", tid);
        MDC.put("requestId", rid);
        MDC.put("service", SERVICE_NAME);
        MDC.put("method", request.getMethod().name());
        MDC.put("uri", request.getPath().value());
    }

    private String resolveHeader(ServerHttpRequest request, String header) {
        String value = request.getHeaders().getFirst(header);
        return (value != null && !value.isBlank()) ? value : UUID.randomUUID().toString();
    }
}
