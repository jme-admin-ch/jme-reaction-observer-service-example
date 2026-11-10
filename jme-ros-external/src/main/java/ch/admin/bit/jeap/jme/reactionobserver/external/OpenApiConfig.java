package ch.admin.bit.jeap.jme.reactionobserver.external;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    GroupedOpenApi ratingApi() {
        return GroupedOpenApi.builder()
                .group("Rating API")
                .pathsToMatch("/api/**")
                .build();
    }
}
