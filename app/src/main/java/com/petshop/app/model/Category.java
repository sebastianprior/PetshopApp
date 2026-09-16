package com.petshop.app.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    public String id;
    public String name;
    public String color;

    @JsonCreator
    public Category() {}

    public Category(String id, String name, String color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }
}
