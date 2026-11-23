package ch.admin.bit.jeap.jme.reactionobserver.headeronly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Product catalog of the reaction observer demo system. It stands for a partner that only returns the header of the
 * jEAP Reaction Observer, without using the library itself. The header is set by the module
 * {@code jeap-reaction-observer-header-starter}, which the jEAP parent brings to every service.
 */
@SpringBootApplication
public class HeaderOnlyApplication {

    protected HeaderOnlyApplication() {
    }

    static void main(String[] args) {
        SpringApplication.run(HeaderOnlyApplication.class, args);
    }
}
