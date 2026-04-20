package com.resumeai.auth.controller;

import com.resumeai.auth.entity.UserAuthEntity;
import com.resumeai.auth.repository.UserAuthRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(roles = "ADMIN")
class AdminUserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean UserAuthRepository userAuthRepository;

    private UserAuthEntity user;

    @BeforeEach
    void setUp() {
        user = new UserAuthEntity();
        user.setId(1);
        user.setUsername("alice");
        user.setEmail("alice@test.com");
        user.setRoles(new ArrayList<>(List.of("USER")));
        user.setBanned(false);
    }

    @Test
    void listAllUsers_returnsOk() throws Exception {
        when(userAuthRepository.findAll()).thenReturn(List.of(user));
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    void getUserById_found() throws Exception {
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(user));
        mockMvc.perform(get("/api/admin/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void getUserById_notFound() throws Exception {
        when(userAuthRepository.findById(999)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/admin/users/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void promoteToAdmin_success() throws Exception {
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(user));
        when(userAuthRepository.save(any())).thenReturn(user);

        mockMvc.perform(post("/api/admin/users/1/promote").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void promoteToAdmin_alreadyAdmin() throws Exception {
        user.setRoles(new ArrayList<>(List.of("USER", "ADMIN")));
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/admin/users/1/promote").with(csrf()))
                .andExpect(status().isOk());
        verify(userAuthRepository, never()).save(any());
    }

    @Test
    void promoteToAdmin_notFound() throws Exception {
        when(userAuthRepository.findById(999)).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/admin/users/999/promote").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void demoteFromAdmin_success() throws Exception {
        user.setRoles(new ArrayList<>(List.of("USER", "ADMIN")));
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(user));
        when(userAuthRepository.save(any())).thenReturn(user);

        mockMvc.perform(post("/api/admin/users/1/demote").with(csrf())
                .header("X-Username", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void demoteFromAdmin_cannotDemoteSelf() throws Exception {
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/admin/users/1/demote").with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot demote yourself"));
    }

    @Test
    void demoteFromAdmin_notFound() throws Exception {
        when(userAuthRepository.findById(999)).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/admin/users/999/demote").with(csrf())
                .header("X-Username", "admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    void banUser_success() throws Exception {
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(user));
        when(userAuthRepository.save(any())).thenReturn(user);

        mockMvc.perform(post("/api/admin/users/1/ban").with(csrf())
                .header("X-Username", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void banUser_cannotBanSelf() throws Exception {
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/admin/users/1/ban").with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unbanUser_success() throws Exception {
        user.setBanned(true);
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(user));
        when(userAuthRepository.save(any())).thenReturn(user);

        mockMvc.perform(post("/api/admin/users/1/unban").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void deleteUser_success() throws Exception {
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(user));

        mockMvc.perform(delete("/api/admin/users/1").with(csrf())
                .header("X-Username", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void deleteUser_cannotDeleteSelf() throws Exception {
        when(userAuthRepository.findById(1)).thenReturn(Optional.of(user));

        mockMvc.perform(delete("/api/admin/users/1").with(csrf())
                .header("X-Username", "alice"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserStats_returnsStats_withRoleAdminPrefix() throws Exception {
        UserAuthEntity admin = new UserAuthEntity();
        admin.setId(2);
        admin.setUsername("admin");
        admin.setRoles(List.of("ROLE_ADMIN"));
        admin.setBanned(false);

        when(userAuthRepository.findAll()).thenReturn(List.of(user, admin));

        mockMvc.perform(get("/api/admin/users/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(2))
                .andExpect(jsonPath("$.adminCount").value(1));
    }

    @Test
    void getUserStats_returnsStats_withAdminNoPrefix() throws Exception {
        UserAuthEntity admin = new UserAuthEntity();
        admin.setRoles(List.of("ADMIN"));
        when(userAuthRepository.findAll()).thenReturn(List.of(admin));

        mockMvc.perform(get("/api/admin/users/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminCount").value(1));
    }
}
