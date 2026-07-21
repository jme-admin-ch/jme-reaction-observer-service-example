package ch.admin.bit.jeap.jme.reactionobserver.test;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.AllowOverridePactUrl;
import au.com.dius.pact.provider.junitsupport.IgnoreNoPactsToVerify;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.reaction.observer.domain.GraphExtractor;
import ch.admin.bit.jeap.reaction.observer.domain.ObservedReactionsAggregatedRepository;
import ch.admin.bit.jeap.reaction.observer.domain.SystemRepository;
import ch.admin.bit.jeap.reaction.observer.domain.models.graph.*;
import ch.admin.bit.jeap.reaction.observer.web.GraphHolder;
import ch.admin.bit.jeap.reaction.observer.web.ReactionObserverApplication;
import ch.admin.bit.jeap.reaction.observer.web.service.GraphFingerprintCalculator;
import ch.admin.bit.jeap.reaction.observer.web.service.ScheduledTasksService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(classes = ReactionObserverApplication.class, webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Provider("bit-jme-reaction-observer-service")
@IgnoreNoPactsToVerify
@PactBroker(enablePendingPacts = "false")
@AllowOverridePactUrl
class PactProviderTest extends KafkaIntegrationTestBase {

    @LocalServerPort
    private int localServerPort;

    @MockitoBean
    private ObservedReactionsAggregatedRepository observedReactionsAggregatedRepository;

    @MockitoBean
    private ScheduledTasksService scheduledTasksService;

    @MockitoBean
    private GraphHolder graphHolder;

    @MockitoBean
    private GraphExtractor graphExtractor;

    @MockitoBean
    private GraphFingerprintCalculator fingerprintCalculator;

    @MockitoBean
    private SystemRepository systemRepository;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        // If there are no pacts there will be no context.
        if (context != null) {
            context.setTarget(new HttpTestTarget("localhost", localServerPort, "/applicationplatform-reaction-observer-service"));
        }
    }

    @State("Message graphs are available")
    public void setupMessageGraphsAvailable() {
        var message = Message.builder()
                .id(123)
                .messageType("ExistingEvent")
                .variant("default")
                .semantic(SemanticType.EVENT)
                .build();

        var reaction = Reaction.builder()
                .id(77)
                .component("notification-service")
                .system("sys1")
                .build();

        var trigger = Trigger.builder()
                .source(message)
                .target(reaction)
                .median(10)
                .build();

        var action = ch.admin.bit.jeap.reaction.observer.domain.models.graph.Action.builder()
                .source(reaction)
                .target(message)
                .build();

        var domainGraph = new Graph(
                List.of(message, reaction),
                List.of(trigger, action)
        );

        when(graphHolder.getGraph()).thenReturn(domainGraph);
        when(graphExtractor.getMessageRelatedGraph(eq(domainGraph), eq("ExistingEvent"), eq("default")))
                .thenReturn(domainGraph);
        when(fingerprintCalculator.calculate(any())).thenReturn("updated-fingerprint");
    }

    @State("Component graphs are available")
    public void setupComponentGraphsAvailable() {
        var message = Message.builder()
                .id(2)
                .messageType("Command2")
                .variant(null)
                .semantic(SemanticType.COMMAND)
                .build();

        var reaction = Reaction.builder()
                .id(1)
                .component("service1")
                .system("sys1")
                .build();

        var trigger = Trigger.builder()
                .source(message)
                .target(reaction)
                .median(5)
                .build();

        var domainGraph = new Graph(
                List.of(message, reaction),
                List.of(trigger)
        );

        when(graphHolder.getGraph()).thenReturn(domainGraph);
        when(graphExtractor.getComponentRelatedGraph(eq(domainGraph), eq("service1")))
                .thenReturn(domainGraph);
        when(fingerprintCalculator.calculate(any())).thenReturn("d2a3e71875d3419799fd68958990ee058d589becb764607a286d65896ce74de3");
    }

    @State("System graphs are available")
    public void setupSystemGraphsAvailable() {
        var message = Message.builder()
                .id(2)
                .messageType("Command2")
                .variant(null)
                .semantic(SemanticType.COMMAND)
                .build();

        var reaction = Reaction.builder()
                .id(1)
                .component("service1")
                .system("sys1")
                .build();

        var trigger = Trigger.builder()
                .source(message)
                .target(reaction)
                .median(5)
                .build();

        var domainGraph = new Graph(
                List.of(message, reaction),
                List.of(trigger)
        );

        when(graphHolder.getGraph()).thenReturn(domainGraph);
        when(graphExtractor.getSystemRelatedGraph(eq(domainGraph), eq("sys1")))
                .thenReturn(domainGraph);
        when(fingerprintCalculator.calculate(any())).thenReturn("d2a3e71875d3419799fd68958990ee058d589becb764607a286d65896ce74de3");
    }

    @State("System names are available")
    public void setupSystemNamesAvailable() {
        when(systemRepository.getSystemNames())
                .thenReturn(List.of("system-1", "system-2"));
    }


    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void testPacts(PactVerificationContext context) {
        // If there are no pacts there will be no context.
        if (context != null) {
            context.verifyInteraction();
        }
    }
}
