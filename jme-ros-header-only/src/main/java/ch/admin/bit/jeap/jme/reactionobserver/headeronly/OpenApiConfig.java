package ch.admin.bit.jeap.jme.reactionobserver.headeronly;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    GroupedOpenApi productApi() {
        return GroupedOpenApi.builder()
                .group("Product API")
                .pathsToMatch("/api/**")
                .build();
    }
}
