package com.example.geohousing.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal placeholder: only exempts the health endpoint from Spring Security's default
 * "authenticate everything" policy, which otherwise activates as soon as
 * spring-boot-starter-oauth2-resource-server is on the classpath. The real JWT-authenticated filter
 * chain (identity.infrastructure.security) replaces this once the identity module's
 * account-resolution pipeline exists.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            registry ->
                registry.requestMatchers("/actuator/health").permitAll().anyRequest().denyAll())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(CsrfConfigurer::disable);
    return http.build();
  }
}
