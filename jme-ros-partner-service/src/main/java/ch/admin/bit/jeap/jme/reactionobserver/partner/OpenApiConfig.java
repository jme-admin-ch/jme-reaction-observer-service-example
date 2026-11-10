package ch.admin.bit.jeap.jme.reactionobserver.partner;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    GroupedOpenApi partnerApi() {
        return GroupedOpenApi.builder()
                .group("Partner API")
                .pathsToMatch("/api/**")
                .build();
    }
}
