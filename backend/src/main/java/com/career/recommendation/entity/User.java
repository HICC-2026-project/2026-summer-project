package com.career.recommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    /** role 컬럼 값. 관리자 판정은 AdminAccountPolicy, 인가는 SecurityConfig(hasRole)에서 쓴다. */
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    private String email;       // 카카오 닉네임만 수집하기로 결정 — 항상 null

    private String nickname;

    /** 앱에서 직접 바꾼 닉네임이면 true — 카카오 로그인이 덮어쓰지 않는다(V23). */
    @Column(name = "nickname_overridden", nullable = false)
    @Builder.Default
    private boolean nicknameOverridden = false;

    @Column(nullable = false)
    private String provider;       // KAKAO | GOOGLE

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(nullable = false)
    @Builder.Default
    private String role = ROLE_USER;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
