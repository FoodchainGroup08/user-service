package com.microservices.user.service;

import com.microservices.user.dto.UpdateUserRequest;
import com.microservices.user.dto.UserResponse;
import com.microservices.user.entity.User;
import com.microservices.user.exception.ResourceNotFoundException;
import com.microservices.user.repository.UserRepository;
import com.microservices.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl Unit Tests")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private UUID testUserId;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = User.builder()
                .id(testUserId)
                .email("user@example.com")
                .passwordHash("encoded-password")
                .role(User.Role.CUSTOMER)
                .build();
    }

    // ---- loadUserByUsername ----

    @Test
    @DisplayName("loadUserByUsername: returns User for a registered email")
    void loadUserByUsername_returnsUser_forRegisteredEmail() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));

        UserDetails result = userService.loadUserByUsername("user@example.com");

        assertNotNull(result);
        assertEquals("user@example.com", result.getUsername());
    }

    @Test
    @DisplayName("loadUserByUsername: throws UsernameNotFoundException for unknown email")
    void loadUserByUsername_throwsException_forUnknownEmail() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userService.loadUserByUsername("ghost@example.com"));
    }

    // ---- findById ----

    @Test
    @DisplayName("findById: returns UserResponse with correct fields")
    void findById_returnsUserResponse_forValidId() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        UserResponse response = userService.findById(testUserId);

        assertNotNull(response);
        assertEquals(testUserId, response.getId());
        assertEquals("user@example.com", response.getEmail());
        assertEquals(User.Role.CUSTOMER, response.getRole());
    }

    @Test
    @DisplayName("findById: throws ResourceNotFoundException for unknown UUID")
    void findById_throwsException_forUnknownId() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.findById(unknownId));
    }

    // ---- findByEmail ----

    @Test
    @DisplayName("findByEmail: returns UserResponse for a registered email")
    void findByEmail_returnsUserResponse_forRegisteredEmail() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));

        UserResponse response = userService.findByEmail("user@example.com");

        assertNotNull(response);
        assertEquals("user@example.com", response.getEmail());
    }

    @Test
    @DisplayName("findByEmail: throws ResourceNotFoundException for unknown email")
    void findByEmail_throwsException_forUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.findByEmail("nobody@example.com"));
    }

    // ---- findAll ----

    @Test
    @DisplayName("findAll: returns all users mapped to UserResponse")
    void findAll_returnsAllUsers() {
        User second = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .role(User.Role.HEAD_OFFICE_ADMIN)
                .build();
        when(userRepository.findAll()).thenReturn(List.of(testUser, second));

        List<UserResponse> result = userService.findAll();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(u -> "user@example.com".equals(u.getEmail())));
        assertTrue(result.stream().anyMatch(u -> "admin@example.com".equals(u.getEmail())));
    }

    @Test
    @DisplayName("findAll: returns empty list when no users exist")
    void findAll_returnsEmptyList_whenNoUsers() {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        assertTrue(userService.findAll().isEmpty());
    }

    // ---- updateUser ----

    @Test
    @DisplayName("updateUser: updates the role when role is provided")
    void updateUser_updatesRole_whenRoleProvided() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setRole(User.Role.BRANCH_MANAGER);

        userService.updateUser(testUserId, request);

        verify(userRepository).save(argThat(u -> u.getRole() == User.Role.BRANCH_MANAGER));
    }

    @Test
    @DisplayName("updateUser: updates branchId when branchId is provided")
    void updateUser_updatesBranchId_whenProvided() {
        UUID branchId = UUID.randomUUID();
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setBranchId(branchId);

        userService.updateUser(testUserId, request);

        verify(userRepository).save(argThat(u -> branchId.equals(u.getBranchId())));
    }

    @Test
    @DisplayName("updateUser: deactivates user when isActive is false")
    void updateUser_deactivatesUser_whenIsActiveFalse() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setIsActive(false);

        userService.updateUser(testUserId, request);

        verify(userRepository).save(argThat(u -> !u.isActive()));
    }

    @Test
    @DisplayName("updateUser: ignores null fields — does not overwrite unchanged fields")
    void updateUser_ignoresNullFields() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest(); // all null

        userService.updateUser(testUserId, request);

        // original role must be preserved
        verify(userRepository).save(argThat(u -> u.getRole() == User.Role.CUSTOMER));
    }

    @Test
    @DisplayName("updateUser: throws ResourceNotFoundException for unknown UUID")
    void updateUser_throwsException_forUnknownId() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateUser(unknownId, new UpdateUserRequest()));
    }

    @Test
    @DisplayName("updateUser: returns UserResponse reflecting the saved entity")
    void updateUser_returnsUpdatedUserResponse() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setRole(User.Role.KITCHEN_STAFF);

        UserResponse response = userService.updateUser(testUserId, request);

        assertNotNull(response);
        assertEquals(testUserId, response.getId());
    }
}
