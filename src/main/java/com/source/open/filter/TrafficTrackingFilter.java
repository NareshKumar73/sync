package com.source.open.filter;

import com.source.open.util.NetworkTrafficService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TrafficTrackingFilter implements Filter {

    private final NetworkTrafficService trafficService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        CountingHttpServletRequestWrapper requestWrapper = new CountingHttpServletRequestWrapper(httpRequest);
        CountingHttpServletResponseWrapper responseWrapper = new CountingHttpServletResponseWrapper(httpResponse);

        try {
            chain.doFilter(requestWrapper, responseWrapper);
        } finally {
            String ip = httpRequest.getRemoteAddr();
            long bytesRead = requestWrapper.getBytesRead();
            long bytesWritten = responseWrapper.getBytesWritten();
            
            trafficService.recordTraffic(ip, bytesRead, bytesWritten);
        }
    }
}
