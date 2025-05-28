package com.example.user.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.user.dto.request.UserCreationRequest;
import com.example.user.dto.request.UserUpdateRequest;
import com.example.user.dto.request.SellerRequest;
import com.example.user.dto.response.UserResponse;
import com.example.user.dto.response.SellerRequestResponse;
import com.example.user.entity.Role;
import com.example.user.entity.User;
import com.example.user.entity.SellerRequestEntity;
import com.example.user.enums.Roles;
import com.example.user.exception.AppException;
import com.example.user.exception.ErrorCode;
import com.example.user.mapper.UserMapper;
import com.example.user.repository.RoleRepository;
import com.example.user.repository.UserRepository;
import com.example.user.repository.SellerRequestRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UserService {
    RoleRepository roleRepository;
    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    RoleService roleService;
    SellerRequestRepository sellerRequestRepository;

    public UserResponse createUser(UserCreationRequest request) {
        if (userRepository.existsByUsername(request.getUsername()))
            throw new AppException(ErrorCode.USER_EXISTED);

        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        Role userRole = roleService.findByName(Roles.USER.name());
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        user.setRole(roles);

        return userMapper.toUserResponse(userRepository.save(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> getUsers() {
        log.info("Trong phương thức lấy danh sách người dùng");
        return userRepository.findAll().stream().map(userMapper::toUserResponse).toList();
    }

    public UserResponse getUser(String id) {
        return userMapper.toUserResponse(userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng")));
    }

    public UserResponse getUserByUsername(String username) {
        return userMapper.toUserResponse(userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với username: " + username)));
    }

    public UserResponse getMyInfo() {
        var context = SecurityContextHolder.getContext();
        String name = context.getAuthentication().getName();

        User user = userRepository.findByUsername(name).orElseThrow(
                () -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return userMapper.toUserResponse(user);
    }

    public UserResponse updateUser(String userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!user.getUsername().equals(currentUsername)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        userMapper.updateUser(user, request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        var role = roleRepository.findAllById(request.getRole());
        user.setRole(new HashSet<>(role));

        return userMapper.toUserResponse(userRepository.save(user));
    }

    @PreAuthorize("hasRole('USER')")
    public SellerRequestResponse requestSellerRole(SellerRequest request) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("Current username from SecurityContext: {}", currentUsername);

        if (currentUsername == null || currentUsername.isEmpty()) {
            log.error("No authenticated user found in SecurityContext");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> {
                    log.error("User not found with username: {}", currentUsername);
                    return new AppException(ErrorCode.USER_NOT_EXISTED);
                });

        List<SellerRequestEntity> pendingRequests = sellerRequestRepository.findPendingByUser(user);
        if (!pendingRequests.isEmpty()) {
            log.warn("User {} already has a pending seller request", currentUsername);
            throw new AppException(ErrorCode.PENDING_REQUEST_EXISTS);
        }

        SellerRequestEntity sellerRequest = SellerRequestEntity.builder()
                .user(user)
                .storeName(request.getStoreName())
                .businessLicense(request.getBusinessLicense())
                .status("PENDING")
                .build();

        sellerRequest = sellerRequestRepository.save(sellerRequest);
        log.info("Saved SellerRequestEntity with id: {}, user_id: {}", sellerRequest.getId(), user.getId());

        return SellerRequestResponse.builder()
                .id(sellerRequest.getId())
                .userId(user.getId())
                .username(user.getUsername())
                .storeName(sellerRequest.getStoreName())
                .businessLicense(sellerRequest.getBusinessLicense())
                .status(sellerRequest.getStatus())
                .build();
    }

    public UserResponse approveSeller(String requestId) {
        SellerRequestEntity request = sellerRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.REQUEST_NOT_FOUND));
        if (!request.getStatus().equals("PENDING")) {
            throw new AppException(ErrorCode.INVALID_REQUEST_STATUS);
        }

        User user = request.getUser();
        Role sellerRole = roleService.findByName(Roles.SELLER.name());
        Set<Role> roles = user.getRole();
        if (roles == null) {
            roles = new HashSet<>();
        }
        if (!roles.contains(sellerRole)) {
            roles.add(sellerRole);
            user.setRole(roles);
            userRepository.save(user);
        }

        request.setStatus("APPROVED");
        sellerRequestRepository.save(request);

        return userMapper.toUserResponse(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<SellerRequestResponse> getAllSellerRequests() {
        List<SellerRequestEntity> requests = sellerRequestRepository.findAllPendingWithUser();
        return requests.stream().map(request -> {
            User user = request.getUser();
            if (user == null) {
                log.error("User is null for SellerRequestEntity with id: {}", request.getId());
                throw new AppException(ErrorCode.USER_NOT_EXISTED);
            }
            return SellerRequestResponse.builder()
                    .id(request.getId())
                    .userId(user.getId())
                    .username(user.getUsername())
                    .storeName(request.getStoreName())
                    .businessLicense(request.getBusinessLicense())
                    .status(request.getStatus())
                    .build();
        }).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void banUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (user.isBanned()) {
            log.warn("User {} is already banned", user.getUsername());
            return;
        }

        user.setBanned(true);
        userRepository.save(user);
        log.info("User {} has been banned", user.getUsername());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> getBannedUsers() {
        List<User> bannedUsers = userRepository.findByIsBannedTrue();
        return bannedUsers.stream()
                .map(userMapper::toUserResponse)
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void unbanUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (!user.isBanned()) {
            log.warn("User {} is not banned", user.getUsername());
            return;
        }

        user.setBanned(false);
        userRepository.save(user);
        log.info("User {} has been unbanned", user.getUsername());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(String userId) {
        userRepository.deleteById(userId);
    }
}