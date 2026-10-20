package com.minidoodle.calendar.domain.exception;

/**
 * The database aborted a slot write because it conflicted with a concurrent write.
 *
 * <p>The request was valid. The client can send it again and a new attempt can succeed, so the web
 * layer maps this to {@code 503 Service Unavailable} with a {@code Retry-After} header. This is the
 * counterpart of {@link SlotOverlapException}, which reports a state conflict that a new attempt
 * cannot resolve.
 */
public class SlotContentionException extends DomainException {

    public SlotContentionException(String message) {
        super(message);
    }
}
