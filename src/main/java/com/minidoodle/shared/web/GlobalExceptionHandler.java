package com.minidoodle.shared.web;

import com.minidoodle.calendar.domain.exception.ForbiddenAccessException;
import com.minidoodle.calendar.domain.exception.InvalidCallerIdException;
import com.minidoodle.calendar.domain.exception.InvalidMeetingException;
import com.minidoodle.calendar.domain.exception.InvalidSlotException;
import com.minidoodle.calendar.domain.exception.MeetingConflictException;
import com.minidoodle.calendar.domain.exception.MeetingNotFoundException;
import com.minidoodle.calendar.domain.exception.SlotNotFoundException;
import com.minidoodle.calendar.domain.exception.SlotOverlapException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates domain and request-binding failures into RFC 7807 problem details.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final String PROBLEM_BASE = "https://minidoodle.example/problems/";

    @ExceptionHandler(InvalidCallerIdException.class)
    ProblemDetail handleInvalidCaller(InvalidCallerIdException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-caller", "Invalid caller identity", ex.getMessage());
    }

    @ExceptionHandler(InvalidSlotException.class)
    ProblemDetail handleInvalidSlot(InvalidSlotException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-slot", "Invalid slot", ex.getMessage());
    }

    @ExceptionHandler(InvalidMeetingException.class)
    ProblemDetail handleInvalidMeeting(InvalidMeetingException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-meeting", "Invalid meeting", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenAccessException.class)
    ProblemDetail handleForbidden(ForbiddenAccessException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(SlotNotFoundException.class)
    ProblemDetail handleNotFound(SlotNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "slot-not-found", "Slot not found", ex.getMessage());
    }

    @ExceptionHandler(MeetingNotFoundException.class)
    ProblemDetail handleMeetingNotFound(MeetingNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "meeting-not-found", "Meeting not found", ex.getMessage());
    }

    @ExceptionHandler(SlotOverlapException.class)
    ProblemDetail handleOverlap(SlotOverlapException ex) {
        return problem(HttpStatus.CONFLICT, "slot-overlap", "Slot overlaps an existing slot", ex.getMessage());
    }

    @ExceptionHandler(MeetingConflictException.class)
    ProblemDetail handleMeetingConflict(MeetingConflictException ex) {
        return problem(HttpStatus.CONFLICT, "meeting-conflict", "Meeting conflict", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .findFirst()
                .orElse("Request validation failed");
        return problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed", detail);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        return problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "Missing required header: " + ex.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "Invalid value for '" + ex.getName() + "'");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "Malformed or unreadable request body");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .findFirst()
                .orElse("Request validation failed");
        return problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed", detail);
    }

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(java.net.URI.create(PROBLEM_BASE + type));
        problem.setTitle(title);
        return problem;
    }
}
