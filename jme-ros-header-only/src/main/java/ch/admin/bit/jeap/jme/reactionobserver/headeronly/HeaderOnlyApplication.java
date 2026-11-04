package ch.admin.bit.jeap.jme.reactionobserver.headeronly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Product catalog of the reaction observer demo system. It stands for a partner that only returns the header of the
 * jEAP Reaction Observer (once the header module is available), without using the library itself.
 */
@SpringBootApplication
public class HeaderOnlyApplication {

    protected HeaderOnlyApplication() {
    }

    static void main(String[] args) {
        SpringApplication.run(HeaderOnlyApplication.class, args);
    }
}
