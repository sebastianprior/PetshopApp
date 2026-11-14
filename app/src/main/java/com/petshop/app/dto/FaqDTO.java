package com.petshop.app.dto;

/** Pregunta frecuente tal como la ve la tienda y como la guarda el administrador. */
public class FaqDTO {
    public Long id;
    public String question;
    public String answer;

    public FaqDTO() {}

    public FaqDTO(Long id, String question, String answer) {
        this.id = id;
        this.question = question;
        this.answer = answer;
    }
}
