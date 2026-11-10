package ch.admin.bit.jeap.jme.reactionobserver.order.api;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    GroupedOpenApi orderApi() {
        return GroupedOpenApi.builder()
                .group("Order API")
                .pathsToMatch("/api/**")
                .build();
    }
}
