package com.waad.tba.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password Encoder Configuration
 * 
 * Separated from SecurityConfig to break circular dependency:
 * SecurityConfig → UserDetailsService → UserSecurityService → PasswordEncoder → SecurityConfig
 * 
 * By defining PasswordEncoder in a separate configuration, it becomes available
 * early in the application context initialization, before SecurityConfig is processed.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
