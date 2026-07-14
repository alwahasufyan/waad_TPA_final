package com.waad.tba.modules.dangerzone.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "danger_zone_otp")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DangerZoneOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation", nullable = false, length = 20)
    private String operation;

    @Column(name = "username", nullable = false, length = 150)
    private String username;

    /** SHA-256 hash of the 6-digit code — the plaintext is never stored or logged. */
    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "environment", length = 40)
    private String environment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "consumed", nullable = false)
    @Builder.Default
    private Boolean consumed = false;
}
