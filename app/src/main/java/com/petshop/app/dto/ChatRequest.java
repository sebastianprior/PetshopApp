package com.petshop.app.dto;

import java.util.List;

public class ChatRequest {
    /** Lo que escribió el cliente. */
    public String message;
    /** Mensajes anteriores del cliente en esta charla (para recordar, por ejemplo, la mascota). */
    public List<String> history;

    public ChatRequest() {}
}
