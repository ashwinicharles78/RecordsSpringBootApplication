package com.records.Records.config;

import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * @author Ashwini Charles on 3/6/2024
 * @project records-v2
 */

@EnableWebSecurity
@EnableWebMvc
public class ResourceServerConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests( auth -> auth
                        .requestMatchers("/records").hasAuthority("SCOPE_api.read"))
                .oauth2ResourceServer(Customizer.withDefaults());
        return http.build();
    }
}
