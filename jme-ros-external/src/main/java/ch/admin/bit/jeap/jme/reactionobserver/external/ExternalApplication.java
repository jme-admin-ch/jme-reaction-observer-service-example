package ch.admin.bit.jeap.jme.reactionobserver.external;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Credit rating agency of the reaction observer demo system. It stands for a system outside of jEAP: it uses the jEAP
 * parent like all services of the example, but never returns the header of the jEAP Reaction Observer.
 */
@SpringBootApplication
public class ExternalApplication {

    protected ExternalApplication() {
    }

    static void main(String[] args) {
        SpringApplication.run(ExternalApplication.class, args);
    }
}
