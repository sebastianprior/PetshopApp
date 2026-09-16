package com.petshop.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    public User() {}

    public User(String id, String email, String password, String name) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.name = name;
    }
}
