package ch.admin.bit.jeap.jme.reactionobserver.external;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/ratings")
class RatingController {

    private final Duration delay;

    RatingController(@Value("${jme.ros.external.delay}") Duration delay) {
        this.delay = delay;
    }

    @Operation(summary = "Get the credit rating of a partner")
    @GetMapping("/{partnerId}")
    public RatingResponse getRating(@PathVariable String partnerId,
                                    @Parameter(description = "Demo switch: answer only after a delay longer than the timeout of the order service")
                                    @RequestParam(defaultValue = "false") boolean delay) throws InterruptedException {
        if (delay) {
            log.info("Delaying the rating of partner {} by {}", partnerId, this.delay);
            Thread.sleep(this.delay);
        }
        return new RatingResponse(partnerId, "GOOD");
    }

    record RatingResponse(String partnerId, String rating) {
    }
}
