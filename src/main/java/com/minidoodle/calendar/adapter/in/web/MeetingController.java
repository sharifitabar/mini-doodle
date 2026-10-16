package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.application.port.in.BookMeetingUseCase;
import com.minidoodle.calendar.application.port.in.GetMeetingUseCase;
import com.minidoodle.calendar.application.port.in.MeetingView;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.UserId;
import com.minidoodle.shared.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/slots/{slotId}/meeting")
@Validated
@Tag(name = "Meetings", description = "Book, read, and cancel the meeting held on a slot in the caller's calendar. "
        + "Every request must identify the caller via the X-User-Id header.")
class MeetingController {

    private static final String CALLER_HEADER = "X-User-Id";
    private static final String CALLER_DESCRIPTION = "Caller user id (demo authentication)";

    private final BookMeetingUseCase bookMeeting;
    private final GetMeetingUseCase getMeeting;

    MeetingController(BookMeetingUseCase bookMeeting, GetMeetingUseCase getMeeting) {
        this.bookMeeting = bookMeeting;
        this.getMeeting = getMeeting;
    }

    @PostMapping
    @Operation(summary = "Book a meeting on a slot",
            description = "Books a meeting on a FREE slot owned by the caller, marks the slot BUSY, and records the "
                    + "free-form participants. Rejected if the slot is not FREE or already holds a meeting.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Meeting booked"),
            @ApiResponse(responseCode = "400", description = "Invalid body or missing/malformed X-User-Id"),
            @ApiResponse(responseCode = "403", description = "Caller is not a known user"),
            @ApiResponse(responseCode = "404", description = "Slot not found in the caller's calendar"),
            @ApiResponse(responseCode = "409", description = "Slot is not free or already holds a meeting")
    })
    ResponseEntity<MeetingResponse> book(
            @Parameter(name = CALLER_HEADER, in = ParameterIn.HEADER, required = true,
                    description = CALLER_DESCRIPTION) @CurrentUser UserId caller,
            @PathVariable UUID slotId,
            @Valid @RequestBody BookMeetingRequest request,
            UriComponentsBuilder uriBuilder) {
        MeetingView view = bookMeeting.bookMeeting(caller, MeetingWebMapper.toCommand(SlotId.of(slotId), request));
        var location = uriBuilder.path("/api/v1/slots/{id}/meeting")
                .buildAndExpand(slotId)
                .toUri();
        return ResponseEntity.created(location).body(MeetingWebMapper.toResponse(view));
    }

    @GetMapping
    @Operation(summary = "Get the meeting on a slot",
            description = "Returns the meeting booked on a slot owned by the caller, including its participants and "
                    + "the slot's time window. Returns 404 when the slot has no meeting.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The meeting"),
            @ApiResponse(responseCode = "400", description = "Malformed X-User-Id or slot id"),
            @ApiResponse(responseCode = "403", description = "Caller is not a known user"),
            @ApiResponse(responseCode = "404", description = "Slot not found in the caller's calendar or has no meeting")
    })
    MeetingResponse get(
            @Parameter(name = CALLER_HEADER, in = ParameterIn.HEADER, required = true,
                    description = CALLER_DESCRIPTION) @CurrentUser UserId caller,
            @PathVariable UUID slotId) {
        return MeetingWebMapper.toResponse(getMeeting.getMeeting(caller, SlotId.of(slotId)));
    }
}
