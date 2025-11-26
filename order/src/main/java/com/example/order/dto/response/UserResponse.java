package com.example.order.dto.response;

import lombok.Data;

@Data
public class UserResponse {
    private String id; // UUID, e.g., fe4eb3cc-bc5f-42e6-a31e-a7f07d8be798
    private String username;
    private String name;
    private String email;
    private String dob;

}