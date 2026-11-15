package com.petshop.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una pregunta frecuente de la tienda, editable desde el panel de administración. */
@Entity
@Table(name = "faq_items")
public class FaqItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 300)
    public String question;

    @Column(nullable = false, length = 2000)
    public String answer;

    /** Orden en que se muestran (de menor a mayor). */
    public int position;

    public FaqItem() {}

    public FaqItem(String question, String answer, int position) {
        this.question = question;
        this.answer = answer;
        this.position = position;
    }
}
