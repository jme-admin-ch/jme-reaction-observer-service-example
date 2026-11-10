package ch.admin.bit.jeap.jme.reactionobserver.nomessaging;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    GroupedOpenApi priceApi() {
        return GroupedOpenApi.builder()
                .group("Price API")
                .pathsToMatch("/api/**")
                .build();
    }
}
