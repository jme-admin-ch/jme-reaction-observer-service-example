package ch.admin.bit.jeap.jme.reactionobserver.nomessaging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Price service of the reaction observer demo system. It stands for services without jEAP Messaging, like the doc
 * service or the ArchRepo, and calls the product catalog.
 */
@SpringBootApplication
public class NoMessagingApplication {

    protected NoMessagingApplication() {
    }

    static void main(String[] args) {
        SpringApplication.run(NoMessagingApplication.class, args);
    }
}
