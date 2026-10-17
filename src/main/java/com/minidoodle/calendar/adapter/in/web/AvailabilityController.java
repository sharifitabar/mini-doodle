package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.application.port.in.AvailabilityQuery;
import com.minidoodle.calendar.application.port.in.GetAvailabilityUseCase;
import com.minidoodle.calendar.domain.model.UserId;
import com.minidoodle.shared.web.CurrentUser;

import java.time.OffsetDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/availability")
@Tag(
        name = "Availability",
        description =
                "Aggregated free/busy availability over a time frame. "
                        + "Every request must identify the caller via the X-User-Id header.")
class AvailabilityController {

    private static final String CALLER_HEADER = "X-User-Id";
    private static final String CALLER_DESCRIPTION = "Caller user id (demo authentication)";

    private final GetAvailabilityUseCase getAvailability;

    AvailabilityController(GetAvailabilityUseCase getAvailability) {
        this.getAvailability = getAvailability;
    }

    @GetMapping
    @Operation(
            summary = "Get the caller's aggregated availability over a time frame",
            description =
                    "Returns the caller's free/busy availability for the half-open frame [from, to) as a "
                            + "chronologically ordered list of disjoint FREE, BUSY, and UNALLOCATED intervals that cover "
                            + "the frame exactly. The frame is required and bounded (from < to, span at most 31 days).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The aggregated availability for the frame"),
        @ApiResponse(
                responseCode = "400",
                description = "Missing/invalid frame or missing/malformed X-User-Id"),
        @ApiResponse(responseCode = "403", description = "Caller is not a known user")
    })
    AvailabilityResponse get(
            @Parameter(
                            name = CALLER_HEADER,
                            in = ParameterIn.HEADER,
                            required = true,
                            description = CALLER_DESCRIPTION)
                    @CurrentUser
                    UserId caller,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return AvailabilityWebMapper.toResponse(
                getAvailability.getAvailability(caller, new AvailabilityQuery(from, to)));
    }
}
