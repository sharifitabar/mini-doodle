package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.application.port.in.CreateSlotUseCase;
import com.minidoodle.calendar.application.port.in.DeleteSlotUseCase;
import com.minidoodle.calendar.application.port.in.GetSlotUseCase;
import com.minidoodle.calendar.application.port.in.ListSlotsUseCase;
import com.minidoodle.calendar.application.port.in.SlotQuery;
import com.minidoodle.calendar.application.port.in.UpdateSlotCommand;
import com.minidoodle.calendar.application.port.in.UpdateSlotUseCase;
import com.minidoodle.calendar.domain.exception.InvalidSlotException;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;
import com.minidoodle.shared.web.CurrentUser;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/slots")
@Validated
@Tag(
        name = "Time Slots",
        description =
                "Create, read, modify, and delete time slots in the caller's personal calendar. "
                        + "Every request must identify the caller via the X-User-Id header.")
class SlotController {

    private static final String CALLER_HEADER = "X-User-Id";
    private static final String CALLER_DESCRIPTION = "Caller user id (demo authentication)";

    private final CreateSlotUseCase createSlot;
    private final GetSlotUseCase getSlot;
    private final ListSlotsUseCase listSlots;
    private final UpdateSlotUseCase updateSlot;
    private final DeleteSlotUseCase deleteSlot;

    SlotController(
            CreateSlotUseCase createSlot,
            GetSlotUseCase getSlot,
            ListSlotsUseCase listSlots,
            UpdateSlotUseCase updateSlot,
            DeleteSlotUseCase deleteSlot) {
        this.createSlot = createSlot;
        this.getSlot = getSlot;
        this.listSlots = listSlots;
        this.updateSlot = updateSlot;
        this.deleteSlot = deleteSlot;
    }

    @PostMapping
    @Operation(
            summary = "Create a time slot",
            description =
                    "Creates a FREE time slot in the caller's calendar. Rejected if it overlaps an existing slot.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Slot created"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid body or missing/malformed X-User-Id"),
        @ApiResponse(responseCode = "403", description = "Caller is not a known user"),
        @ApiResponse(
                responseCode = "409",
                description = "Slot overlaps an existing slot in the calendar"),
        @ApiResponse(responseCode = "422", description = "Validation failed")
    })
    ResponseEntity<SlotResponse> create(
            @Parameter(
                            name = CALLER_HEADER,
                            in = ParameterIn.HEADER,
                            required = true,
                            description = CALLER_DESCRIPTION)
                    @CurrentUser
                    UserId caller,
            @Valid @RequestBody CreateSlotRequest request,
            UriComponentsBuilder uriBuilder) {
        TimeSlot created = createSlot.createSlot(caller, SlotWebMapper.toCommand(request));
        var location =
                uriBuilder.path("/api/v1/slots/{id}").buildAndExpand(created.id().value()).toUri();
        return ResponseEntity.created(location).body(SlotWebMapper.toResponse(created));
    }

    @GetMapping
    @Operation(
            summary = "List the caller's time slots",
            description =
                    "Returns a bounded, paginated page of the caller's slots with optional time-range and status filters.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of slots"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid query parameters or missing/malformed X-User-Id"),
        @ApiResponse(responseCode = "403", description = "Caller is not a known user")
    })
    SlotPageResponse list(
            @Parameter(
                            name = CALLER_HEADER,
                            in = ParameterIn.HEADER,
                            required = true,
                            description = CALLER_DESCRIPTION)
                    @CurrentUser
                    UserId caller,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime to,
            @RequestParam(required = false) SlotStatus status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(SlotQuery.MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return SlotWebMapper.toResponse(
                listSlots.listSlots(caller, new SlotQuery(from, to, status, limit, offset)));
    }

    @GetMapping("/{slotId}")
    @Operation(
            summary = "Get a time slot by id",
            description = "Returns a single slot owned by the caller's calendar.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The slot"),
        @ApiResponse(responseCode = "400", description = "Malformed X-User-Id or slot id"),
        @ApiResponse(responseCode = "403", description = "Caller is not a known user"),
        @ApiResponse(responseCode = "404", description = "Slot not found in the caller's calendar")
    })
    SlotResponse get(
            @Parameter(
                            name = CALLER_HEADER,
                            in = ParameterIn.HEADER,
                            required = true,
                            description = CALLER_DESCRIPTION)
                    @CurrentUser
                    UserId caller,
            @PathVariable UUID slotId) {
        return SlotWebMapper.toResponse(getSlot.getSlot(caller, SlotId.of(slotId)));
    }

    @PatchMapping("/{slotId}")
    @Operation(
            summary = "Update a time slot",
            description =
                    "Partially updates a slot. Any subset of status, startAt, and durationMinutes may be supplied; "
                            + "at least one field is required.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Updated slot"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid body, empty update, or missing/malformed X-User-Id"),
        @ApiResponse(responseCode = "403", description = "Caller is not a known user"),
        @ApiResponse(responseCode = "404", description = "Slot not found in the caller's calendar"),
        @ApiResponse(responseCode = "409", description = "Updated time overlaps another slot")
    })
    SlotResponse update(
            @Parameter(
                            name = CALLER_HEADER,
                            in = ParameterIn.HEADER,
                            required = true,
                            description = CALLER_DESCRIPTION)
                    @CurrentUser
                    UserId caller,
            @PathVariable UUID slotId,
            @Valid @RequestBody UpdateSlotRequest request) {
        SlotId id = SlotId.of(slotId);
        if (request.status() == null
                && request.startAt() == null
                && request.durationMinutes() == null) {
            throw new InvalidSlotException(
                    "At least one of status, startAt or durationMinutes must be provided");
        }
        TimeSlot updated =
                updateSlot.updateSlot(
                        caller,
                        id,
                        new UpdateSlotCommand(
                                request.status(), request.startAt(), request.durationMinutes()));
        return SlotWebMapper.toResponse(updated);
    }

    @DeleteMapping("/{slotId}")
    @Operation(
            summary = "Delete a time slot",
            description =
                    "Deletes a slot from the caller's calendar. Returns no content on success.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Slot deleted"),
        @ApiResponse(responseCode = "400", description = "Malformed X-User-Id or slot id"),
        @ApiResponse(responseCode = "403", description = "Caller is not a known user"),
        @ApiResponse(responseCode = "404", description = "Slot not found in the caller's calendar")
    })
    ResponseEntity<Void> delete(
            @Parameter(
                            name = CALLER_HEADER,
                            in = ParameterIn.HEADER,
                            required = true,
                            description = CALLER_DESCRIPTION)
                    @CurrentUser
                    UserId caller,
            @PathVariable UUID slotId) {
        deleteSlot.deleteSlot(caller, SlotId.of(slotId));
        return ResponseEntity.noContent().build();
    }
}
