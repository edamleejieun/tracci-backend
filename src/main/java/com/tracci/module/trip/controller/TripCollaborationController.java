package com.tracci.module.trip.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.common.response.PageResponse;
import com.tracci.module.trip.dto.request.TripInvitationCreateReq;
import com.tracci.module.trip.dto.request.TripInvitationJoinReq;
import com.tracci.module.trip.dto.request.TripMemberUpdateReq;
import com.tracci.module.trip.dto.request.TripOwnershipTransferReq;
import com.tracci.module.trip.dto.response.TripActivityLogResp;
import com.tracci.module.trip.dto.response.TripInvitationCreateResp;
import com.tracci.module.trip.dto.response.TripInvitationJoinResp;
import com.tracci.module.trip.dto.response.TripMemberResp;
import com.tracci.module.trip.dto.response.TripOwnershipTransferResp;
import com.tracci.module.trip.service.TripActivityLogService;
import com.tracci.module.trip.service.TripCollaborationService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TripCollaborationController {

    private final TripCollaborationService collaborationService;
    private final TripActivityLogService activityLogService;

    @PostMapping("/trips/{tripId}/invitations")
    public ApiResponse<TripInvitationCreateResp> createInvitation(
            @PathVariable String tripId,
            @Valid @RequestBody TripInvitationCreateReq req) {
        return ApiResponse.ok(collaborationService.createInvitation(tripId, req));
    }

    @PostMapping("/trip-invitations/join")
    public ApiResponse<TripInvitationJoinResp> joinInvitation(@Valid @RequestBody TripInvitationJoinReq req) {
        return ApiResponse.ok(collaborationService.joinInvitation(req));
    }

    @GetMapping("/trips/{tripId}/members")
    public ApiResponse<List<TripMemberResp>> listMembers(@PathVariable String tripId) {
        return ApiResponse.ok(collaborationService.listMembers(tripId));
    }

    @PutMapping("/trips/{tripId}/members/{memberId}")
    public ApiResponse<TripMemberResp> updateMember(
            @PathVariable String tripId,
            @PathVariable String memberId,
            @Valid @RequestBody TripMemberUpdateReq req) {
        return ApiResponse.ok(collaborationService.updateMember(tripId, memberId, req));
    }

    @PutMapping("/trips/{tripId}/owner")
    public ApiResponse<TripOwnershipTransferResp> transferOwnership(
            @PathVariable String tripId,
            @Valid @RequestBody TripOwnershipTransferReq req) {
        return ApiResponse.ok(collaborationService.transferOwnership(tripId, req));
    }

    @DeleteMapping("/trips/{tripId}/members/{memberId}")
    public ApiResponse<Void> removeMember(@PathVariable String tripId, @PathVariable String memberId) {
        collaborationService.removeMember(tripId, memberId);
        return ApiResponse.ok();
    }

    @GetMapping("/trips/{tripId}/activity-logs")
    public ApiResponse<PageResponse<TripActivityLogResp>> listActivityLogs(
            @PathVariable String tripId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return ApiResponse.ok(activityLogService.listLogs(tripId, page, pageSize));
    }
}
