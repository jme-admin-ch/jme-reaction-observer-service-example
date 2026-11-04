package ch.admin.bit.jeap.jme.reactionobserver.order;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * URLs of the services the order service calls when checking an order, and the timeouts of these calls. The URLs
 * depend on the environment and have no defaults; the service doesn't start without them.
 *
 * @param productCatalogUrl base URL of the product catalog (jme-ros-header-only)
 * @param priceServiceUrl   base URL of the price service (jme-ros-no-messaging)
 * @param ratingServiceUrl  base URL of the external credit rating agency (jme-ros-external)
 * @param partnerServiceUrl base URL of the partner service (jme-ros-partner-service)
 * @param connectTimeout    connect timeout of all calls
 * @param readTimeout       read timeout of all calls; the slow credit rating answers only after it has expired
 */
@Validated
@ConfigurationProperties("jme.ros.order-service")
public record OrderServiceProperties(
        @NotBlank String productCatalogUrl,
        @NotBlank String priceServiceUrl,
        @NotBlank String ratingServiceUrl,
        @NotBlank String partnerServiceUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout) {
}
