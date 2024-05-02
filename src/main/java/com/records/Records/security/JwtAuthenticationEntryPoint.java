package com.records.Records.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author Ashwini Charles on 3/10/2024
 * @project RecordsSpringBootApplication
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        PrintWriter writer = response.getWriter();
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        writer.println("Exception occurred please check logs for further details");
    }
}
