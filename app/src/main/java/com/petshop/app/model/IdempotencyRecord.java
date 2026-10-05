package com.petshop.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @Column(name = "idem_key", length = 120)
    public String idemKey;

    public String userId;
    public String requestHash;
    public int statusCode;
    public Long ordenId;
    public String problemSlug;
    public String problemTitle;

    @Column(length = 500)
    public String problemDetail;

    public Instant createdAt;

    public IdempotencyRecord() {}
}
