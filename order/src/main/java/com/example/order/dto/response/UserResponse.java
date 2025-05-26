package com.example.order.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class UserResponse {
    private String id; // UUID, e.g., fe4eb3cc-bc5f-42e6-a31e-a7f07d8be798
    private String username;
    private String name;
    private String email;
    private String dob;
    private List<Role> role;

    @Data
    public static class Role {
        private String name;
        private String description;
        private List<String> permission;
    }
}