package com.tracci.module.journal.service;

import com.tracci.common.response.PageResponse;
import com.tracci.module.journal.dto.request.JournalEntryCreateReq;
import com.tracci.module.journal.dto.request.JournalEntryUpdateReq;
import com.tracci.module.journal.dto.response.JournalEntryResp;

public interface JournalEntryService {

    JournalEntryResp createEntry(String tripId, JournalEntryCreateReq req);

    PageResponse<JournalEntryResp> listEntries(String tripId, int page, int pageSize);

    JournalEntryResp updateEntry(String tripId, String entryId, JournalEntryUpdateReq req);

    JournalEntryResp.PhotoResp featurePhoto(String tripId, String photoId);

    JournalEntryResp.PhotoResp unfeaturePhoto(String tripId, String photoId);

    void deleteEntry(String tripId, String entryId);
}
