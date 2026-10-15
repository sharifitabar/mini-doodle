package com.minidoodle.calendar.adapter.in.web;

import com.minidoodle.calendar.application.port.in.ChangeSlotStatusUseCase;
import com.minidoodle.calendar.application.port.in.CreateSlotUseCase;
import com.minidoodle.calendar.application.port.in.GetSlotUseCase;
import com.minidoodle.calendar.application.port.in.ListSlotsUseCase;
import com.minidoodle.calendar.application.port.in.ModifySlotCommand;
import com.minidoodle.calendar.application.port.in.ModifySlotUseCase;
import com.minidoodle.calendar.application.port.in.SlotQuery;
import com.minidoodle.calendar.domain.exception.InvalidSlotException;
import com.minidoodle.calendar.domain.model.SlotId;
import com.minidoodle.calendar.domain.model.SlotStatus;
import com.minidoodle.calendar.domain.model.TimeSlot;
import com.minidoodle.calendar.domain.model.UserId;
import com.minidoodle.shared.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/slots")
@Validated
class SlotController {

    private final CreateSlotUseCase createSlot;
    private final GetSlotUseCase getSlot;
    private final ListSlotsUseCase listSlots;
    private final ChangeSlotStatusUseCase changeSlotStatus;
    private final ModifySlotUseCase modifySlot;

    SlotController(
            CreateSlotUseCase createSlot,
            GetSlotUseCase getSlot,
            ListSlotsUseCase listSlots,
            ChangeSlotStatusUseCase changeSlotStatus,
            ModifySlotUseCase modifySlot) {
        this.createSlot = createSlot;
        this.getSlot = getSlot;
        this.listSlots = listSlots;
        this.changeSlotStatus = changeSlotStatus;
        this.modifySlot = modifySlot;
    }

    @PostMapping
    ResponseEntity<SlotResponse> create(
            @CurrentUser UserId caller,
            @Valid @RequestBody CreateSlotRequest request,
            UriComponentsBuilder uriBuilder) {
        TimeSlot created = createSlot.createSlot(caller, SlotWebMapper.toCommand(request));
        var location = uriBuilder.path("/api/v1/slots/{id}")
                .buildAndExpand(created.id().value())
                .toUri();
        return ResponseEntity.created(location).body(SlotWebMapper.toResponse(created));
    }

    @GetMapping
    SlotPageResponse list(
            @CurrentUser UserId caller,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) SlotStatus status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(SlotQuery.MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return SlotWebMapper.toResponse(
                listSlots.listSlots(caller, new SlotQuery(from, to, status, limit, offset)));
    }

    @GetMapping("/{slotId}")
    SlotResponse get(@CurrentUser UserId caller, @PathVariable UUID slotId) {
        return SlotWebMapper.toResponse(getSlot.getSlot(caller, SlotId.of(slotId)));
    }

    @PatchMapping("/{slotId}")
    SlotResponse update(
            @CurrentUser UserId caller,
            @PathVariable UUID slotId,
            @Valid @RequestBody UpdateSlotRequest request) {
        SlotId id = SlotId.of(slotId);
        if (request.status() == null && request.startAt() == null && request.durationMinutes() == null) {
            throw new InvalidSlotException("At least one of status, startAt or durationMinutes must be provided");
        }
        TimeSlot updated = null;
        if (request.status() != null) {
            updated = changeSlotStatus.changeStatus(caller, id, request.status());
        }
        if (request.startAt() != null || request.durationMinutes() != null) {
            updated = modifySlot.modifySlot(
                    caller, id, new ModifySlotCommand(request.startAt(), request.durationMinutes()));
        }
        return SlotWebMapper.toResponse(updated);
    }
}
