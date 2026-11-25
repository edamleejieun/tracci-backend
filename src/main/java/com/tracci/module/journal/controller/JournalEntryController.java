package com.tracci.module.journal.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.common.response.PageResponse;
import com.tracci.module.journal.dto.request.JournalEntryCreateReq;
import com.tracci.module.journal.dto.request.JournalEntryUpdateReq;
import com.tracci.module.journal.dto.response.JournalEntryResp;
import com.tracci.module.journal.service.JournalEntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/journal")
@RequiredArgsConstructor
public class JournalEntryController {

    private final JournalEntryService journalEntryService;

    @PostMapping
    public ApiResponse<JournalEntryResp> create(
            @PathVariable String tripId,
            @Valid @RequestBody JournalEntryCreateReq req) {
        return ApiResponse.ok(journalEntryService.createEntry(tripId, req));
    }

    @GetMapping
    public ApiResponse<PageResponse<JournalEntryResp>> list(
            @PathVariable String tripId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(journalEntryService.listEntries(tripId, page, pageSize));
    }

    @PutMapping("/{entryId}")
    public ApiResponse<JournalEntryResp> update(
            @PathVariable String tripId,
            @PathVariable String entryId,
            @Valid @RequestBody JournalEntryUpdateReq req) {
        return ApiResponse.ok(journalEntryService.updateEntry(tripId, entryId, req));
    }

    @DeleteMapping("/{entryId}")
    public ApiResponse<Void> delete(
            @PathVariable String tripId,
            @PathVariable String entryId) {
        journalEntryService.deleteEntry(tripId, entryId);
        return ApiResponse.ok();
    }

    @PutMapping("/photos/{photoId}/featured")
    public ApiResponse<JournalEntryResp.PhotoResp> featurePhoto(
            @PathVariable String tripId,
            @PathVariable String photoId) {
        return ApiResponse.ok(journalEntryService.featurePhoto(tripId, photoId));
    }

    @DeleteMapping("/photos/{photoId}/featured")
    public ApiResponse<JournalEntryResp.PhotoResp> unfeaturePhoto(
            @PathVariable String tripId,
            @PathVariable String photoId) {
        return ApiResponse.ok(journalEntryService.unfeaturePhoto(tripId, photoId));
    }
}
