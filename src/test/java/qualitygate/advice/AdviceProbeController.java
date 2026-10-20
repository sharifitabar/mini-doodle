package qualitygate.advice;

import com.minidoodle.calendar.domain.exception.InvalidMeetingException;
import com.minidoodle.calendar.domain.exception.InvalidSlotException;
import com.minidoodle.calendar.domain.exception.SlotContentionException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A throwaway controller that provokes the {@code GlobalExceptionHandler} mappings the public API
 * cannot reach: a required header that no request sends, the two domain exceptions that request
 * bean validation pre-empts in the real API, and the contention error that only a database deadlock
 * produces.
 *
 * <p>It is deliberately <em>not</em> in a {@code com.minidoodle} package. Spring Boot component
 * scans from the application class's package, so anything under {@code com.minidoodle} in the test
 * sources is a candidate for registration, and a {@code @RestController} there would publish {@code
 * /unreachable/**} into every integration test that boots the real context. Keeping it outside the
 * scanned root rules that out by construction rather than by observation.
 *
 * <p>It is public and carries a stereotype because the standalone MockMvc setup detects handler
 * methods by looking for a controller annotation on the bean class: without one the request finds
 * no handler, answers 404, and never reaches the advice at all. See {@code
 * com.minidoodle.shared.web.ExceptionHandlerAdviceTest}.
 */
@RestController
@RequestMapping("/unreachable")
public class AdviceProbeController {

    @GetMapping("/header")
    String requiresHeader(@RequestHeader("X-Required") String required) {
        return required;
    }

    @GetMapping("/invalid-slot")
    String throwsInvalidSlot() {
        throw new InvalidSlotException("start must be in the future");
    }

    @GetMapping("/invalid-meeting")
    String throwsInvalidMeeting() {
        throw new InvalidMeetingException("A participant must have a name or an email");
    }

    @GetMapping("/contention")
    String throwsContention() {
        throw new SlotContentionException(
                "Slot write conflicted with a concurrent write; send the request again");
    }
}
