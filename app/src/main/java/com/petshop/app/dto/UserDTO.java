package com.petshop.app.dto;

import com.petshop.app.model.User;

public class UserDTO {
    public String id;
    public String email;
    public String name;
    public String role;
    public boolean active;
    /** "GOOGLE" si entra con Google (no tiene contraseña propia) o "LOCAL" si entra con email y contraseña. */
    public String authProvider = "LOCAL";

    public UserDTO() {}

    public UserDTO(String id, String email, String name, String role, boolean active) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.role = role;
        this.active = active;
    }

    public static UserDTO fromUser(User user) {
        UserDTO dto = new UserDTO(user.id, user.email, user.name, user.role, user.active);
        dto.authProvider = "GOOGLE".equals(user.authProvider) ? "GOOGLE" : "LOCAL";
        return dto;
    }
}
