package com.resumeai.auth.controller;

import com.resumeai.auth.dto.response.AdminUserDTO;
import com.resumeai.auth.entity.UserAuthEntity;
import com.resumeai.auth.repository.UserAuthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints for user management.
 * All routes under /api/admin/** are restricted to ROLE_ADMIN
 * via both this @PreAuthorize and the API Gateway role-check filter.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Utilities", description = "Back-office endpoints for user management")
public class AdminUserController {

    private final UserAuthRepository userAuthRepository;

    /**
     * GET /api/admin/users
     * List all registered users with their roles and status.
     */
    @Operation(summary = "List all users", description = "Returns a list of all registered users with their roles and status.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved user list")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminUserDTO>> listAllUsers() {
        List<UserAuthEntity> users = userAuthRepository.findAll();
        List<AdminUserDTO> dtos = users.stream()
                .map(u -> new AdminUserDTO(u.getId(), u.getUsername(), u.getEmail(),
                        u.getRoles(), u.isEnabled()))
                .toList();
        log.info("Admin listed {} users", dtos.size());
        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /api/admin/users/{id}
     * Fetch a single user by DB id.
     */
    @Operation(summary = "Get user by ID", description = "Fetches a single user by DB id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUserById(@PathVariable int id) {
        return userAuthRepository.findById(id)
                .map(u -> ResponseEntity.ok(new AdminUserDTO(
                        u.getId(), u.getUsername(), u.getEmail(),
                        u.getRoles(), u.isEnabled())))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/admin/users/{id}/promote
     * Add ADMIN role to a user.
     */
    @Operation(summary = "Promote user to Admin", description = "Adds the ADMIN role to a specific user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User promoted successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping("/{id}/promote")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> promoteToAdmin(@PathVariable int id) {
        return userAuthRepository.findById(id).map(u -> {
            List<String> roles = new ArrayList<>(u.getRoles());
            if (!roles.contains("ADMIN")) {
                roles.add("ADMIN");
                u.setRoles(roles);
                userAuthRepository.save(u);
                log.info("Admin promoted user '{}' to ADMIN", u.getUsername());
            }
            return ResponseEntity.ok(Map.of("message", "User '" + u.getUsername() + "' is now an ADMIN"));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/admin/users/{id}/demote
     * Remove ADMIN role from a user (cannot demote yourself).
     */
    @Operation(summary = "Demote user from Admin", description = "Removes the ADMIN role from a specific user. You cannot demote yourself.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User demoted successfully"),
            @ApiResponse(responseCode = "400", description = "Admin cannot demote themselves"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping("/{id}/demote")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> demoteFromAdmin(@PathVariable int id,
                                              @RequestHeader("X-Username") String callerUsername) {
        return userAuthRepository.findById(id).map(u -> {
            if (u.getUsername().equals(callerUsername)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "You cannot demote yourself"));
            }
            List<String> roles = new ArrayList<>(u.getRoles());
            roles.remove("ADMIN");
            u.setRoles(roles);
            userAuthRepository.save(u);
            log.info("Admin demoted user '{}' from ADMIN", u.getUsername());
            return ResponseEntity.ok(Map.of("message", "ADMIN role removed from '" + u.getUsername() + "'"));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/admin/users/{id}/ban
     * Soft-ban: sets a ban flag on the user. The user cannot log in.
     * This uses the 'banned' field added to UserAuthEntity.
     */
    @Operation(summary = "Ban a user", description = "Soft-bans a user so they can no longer log in. Admin cannot ban themselves.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User banned successfully"),
            @ApiResponse(responseCode = "400", description = "Admin cannot ban themselves"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping("/{id}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> banUser(@PathVariable int id,
                                      @RequestHeader("X-Username") String callerUsername) {
        return userAuthRepository.findById(id).map(u -> {
            if (u.getUsername().equals(callerUsername)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "You cannot ban yourself"));
            }
            u.setBanned(true);
            userAuthRepository.save(u);
            log.warn("Admin banned user '{}'", u.getUsername());
            return ResponseEntity.ok(Map.of("message", "User '" + u.getUsername() + "' has been banned"));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/admin/users/{id}/unban
     * Lift the ban on a user.
     */
    @Operation(summary = "Unban a user", description = "Lifts the ban on a user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User unbanned successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping("/{id}/unban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> unbanUser(@PathVariable int id) {
        return userAuthRepository.findById(id).map(u -> {
            u.setBanned(false);
            userAuthRepository.save(u);
            log.info("Admin unbanned user '{}'", u.getUsername());
            return ResponseEntity.ok(Map.of("message", "User '" + u.getUsername() + "' has been unbanned"));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/admin/users/{id}
     * Permanently delete a user account.
     */
    @Operation(summary = "Delete user", description = "Permanently deletes a user account. Admin cannot delete themselves.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Admin cannot delete themselves"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable int id,
                                         @RequestHeader("X-Username") String callerUsername) {
        return userAuthRepository.findById(id).map(u -> {
            if (u.getUsername().equals(callerUsername)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "You cannot delete your own account"));
            }
            userAuthRepository.delete(u);
            log.warn("Admin permanently deleted user '{}'", u.getUsername());
            return ResponseEntity.ok(Map.of("message", "User '" + u.getUsername() + "' has been deleted"));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/admin/users/stats
     * Return high-level user statistics: total, admins, banned.
     */
    @Operation(summary = "Get user statistics", description = "Returns high-level statistics including total users, admins, banned users, and active users.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved statistics")
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        List<UserAuthEntity> all = userAuthRepository.findAll();
        long total = all.size();
        long admins = all.stream()
                .filter(u -> u.getRoles().contains("ADMIN") || u.getRoles().contains("ROLE_ADMIN"))
                .count();
        long banned = all.stream().filter(UserAuthEntity::isBanned).count();
        long active = total - banned;

        Map<String, Object> stats = Map.of(
                "totalUsers", total,
                "adminCount", admins,
                "bannedCount", banned,
                "activeUsers", active
        );
        return ResponseEntity.ok(stats);
    }
}
