package com.petshop.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "users")
public class User {
    @Id
    public String id;

    @Column(unique = true, nullable = false)
    public String email;

    @Column(nullable = false)
    public String password;

    public String name;

    public String role = "CUSTOMER";

    @Column(nullable = false)
    @ColumnDefault("true")
    public boolean active = true;

    /** Cómo se registró: "GOOGLE" si entró con Google; nulo = con email y contraseña. */
    @Column(length = 20)
    public String authProvider;

    public User() {}

    public User(String id, String email, String password, String name) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.name = name;
    }
}
