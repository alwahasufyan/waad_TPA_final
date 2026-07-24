package com.waad.tba.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CLAIM-REVIEW-FOLLOWUP-1: the JVM (and this app's {@code LocalDateTime.now()}
 * calls) run in UTC (containers have no TZ set), but Jackson's default
 * {@link LocalDateTime} serialization writes a naive string with no zone
 * marker (e.g. "2026-07-23T18:41:37.252"). Browsers parsing that string via
 * {@code new Date(...)} interpret it as LOCAL time, not UTC — for a Libya
 * (UTC+2) browser this silently shifted every timestamp in the UI (chat/notes
 * timestamps, relative "submitted X ago" times, etc.) by exactly 2 hours.
 *
 * Fix: serialize every {@code LocalDateTime} field with an explicit "Z" UTC
 * marker, so {@code new Date(...)} on the frontend parses it correctly and
 * converts it to the viewer's real local time automatically. No entity or
 * DTO changes needed — this is a single global serialization rule.
 */
@Configuration
public class JacksonDateTimeConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcLocalDateTimeCustomizer() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        return builder -> builder.postConfigurer(objectMapper -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
            objectMapper.registerModule(module);
        });
    }
}
