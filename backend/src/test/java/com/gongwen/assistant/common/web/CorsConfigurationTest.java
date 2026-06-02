package com.gongwen.assistant.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigurationTest {
    @Test
    @SuppressWarnings("unchecked")
    void exposesContentDispositionForDownloadFileNames() throws Exception {
        CorsRegistry registry = new CorsRegistry();

        new CorsConfiguration().addCorsMappings(registry);

        Method method = CorsRegistry.class.getDeclaredMethod("getCorsConfigurations");
        method.setAccessible(true);
        Map<String, org.springframework.web.cors.CorsConfiguration> configurations =
                (Map<String, org.springframework.web.cors.CorsConfiguration>) method.invoke(registry);

        assertThat(configurations.get("/api/**").getExposedHeaders())
                .contains(HttpHeaders.CONTENT_DISPOSITION);
    }
}
