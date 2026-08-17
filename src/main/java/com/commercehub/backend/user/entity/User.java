package com.commercehub.backend.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(unique = true, length = 100)
    private String username;

    @Column(name = "username_changed_at")
    private OffsetDateTime usernameChangedAt;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "ban_reason", columnDefinition = "TEXT")
    private String banReason;

    @Column(name = "last_active_at", nullable = false)
    private OffsetDateTime lastActiveAt;

    @Builder.Default
    @Column(name = "provider", length = 20)
    private String provider = "LOCAL";


    // lk bảng level_configs
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_level", referencedColumnName = "level", nullable = false)
    private LevelConfig userLevel;

    @Builder.Default
    @Column(name = "accumulated_spent", nullable = false)
    private BigDecimal accumulatedSpent = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "accumulated_earned", nullable = false)
    private BigDecimal accumulatedEarned = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "is_email_verified", nullable = false)
    private Boolean isEmailVerified = false;

    @Builder.Default
    @Column(name = "is_phone_verified", nullable = false)
    private Boolean isPhoneVerified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    //qh Many-to-Many với bảng Roles
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uq_user_roles_one_role_per_user",
                    columnNames = "user_id"
            )
    )
    private Set<Role> roles = new HashSet<>();

    public boolean hasRole(String roleName) {
        return roleName != null
                && roles != null
                && roles.stream().anyMatch(role -> roleName.equals(role.getName()));
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();

        createdAt = now;
        updatedAt = now;
        lastActiveAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
