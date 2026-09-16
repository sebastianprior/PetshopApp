package com.petshop.app.dto;

import com.petshop.app.model.User;

public class UserDTO {
    public String id;
    public String email;
    public String name;
    public String role;

    public UserDTO() {}

    public UserDTO(String id, String email, String name, String role) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.role = role;
    }

    public static UserDTO fromUser(User user) {
        return new UserDTO(user.id, user.email, user.name, user.role);
    }
}
