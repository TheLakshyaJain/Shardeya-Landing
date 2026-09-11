package com.shardeya.builder.team;

import com.shardeya.builder.team.dto.DeactivateRequest;
import com.shardeya.builder.team.dto.StaffActivityResponse;
import com.shardeya.builder.team.dto.TeamMemberCreateRequest;
import com.shardeya.builder.team.dto.TeamMemberResponse;
import com.shardeya.builder.team.dto.TeamMemberUpdateRequest;
import com.shardeya.platform.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * B-12 Admin Panel / Team & Roles. Every mutating endpoint requires
 * TEAM_MANAGE (Admin/Owner only per §18.3); listing/viewing the roster
 * without editing is TEAM_VIEW (also granted to Manager -- see the seed
 * migration), matching "Viewing the team roster... can be granted via
 * TEAM_VIEW to Managers."
 */
@RestController
public class TeamController {

    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/builder/team")
    @RequiresPermission("TEAM_VIEW")
    public List<TeamMemberResponse> list(@RequestParam(required = false) String status, @RequestParam(required = false) String role) {
        return service.list(status, role);
    }

    @PostMapping("/api/v1/builder/team")
    @RequiresPermission("TEAM_MANAGE")
    public ResponseEntity<TeamMemberResponse> create(@Valid @RequestBody TeamMemberCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/api/v1/builder/team/{userId}")
    @RequiresPermission("TEAM_VIEW")
    public TeamMemberResponse get(@PathVariable UUID userId) {
        return service.get(userId);
    }

    @PatchMapping("/api/v1/builder/team/{userId}")
    @RequiresPermission("TEAM_MANAGE")
    public TeamMemberResponse update(@PathVariable UUID userId, @RequestBody TeamMemberUpdateRequest request) {
        return service.update(userId, request);
    }

    @PostMapping("/api/v1/builder/team/{userId}/deactivate")
    @RequiresPermission("TEAM_MANAGE")
    public ResponseEntity<Void> deactivate(@PathVariable UUID userId, @RequestBody DeactivateRequest request) {
        service.deactivate(userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/builder/team/{userId}/reactivate")
    @RequiresPermission("TEAM_MANAGE")
    public ResponseEntity<Void> reactivate(@PathVariable UUID userId) {
        service.reactivate(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/builder/team/{userId}")
    @RequiresPermission("TEAM_MANAGE")
    public ResponseEntity<Void> remove(@PathVariable UUID userId, @RequestBody(required = false) DeactivateRequest request) {
        service.remove(userId, request == null ? new DeactivateRequest(null, null, false) : request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/builder/team/{userId}/resend-invite")
    @RequiresPermission("TEAM_MANAGE")
    public ResponseEntity<Void> resendInvite(@PathVariable UUID userId) {
        service.resendInvite(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/builder/team/{userId}/reset-password")
    @RequiresPermission("TEAM_MANAGE")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID userId) {
        service.resetPassword(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/builder/team/{userId}/activity")
    @RequiresPermission("TEAM_VIEW")
    public StaffActivityResponse activity(@PathVariable UUID userId) {
        return service.activity(userId);
    }
}
