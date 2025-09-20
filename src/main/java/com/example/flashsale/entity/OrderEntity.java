package com.example.flashsale.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "t_order", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_voucher", columnNames = {"user_id", "voucher_id"})
})
@Data
public class OrderEntity {
    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "voucher_id", nullable = false)
    private Long voucherId;

    @Column(name = "status", nullable = false)
    private Integer status = 1; // 1=CREATED

    @Column(name = "created_at", nullable = false)
    private Long createdAt = Instant.now().toEpochMilli();
}
