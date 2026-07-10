package com.commercehub.backend.wallet.entity;

import com.commercehub.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
     User user;

    @Column(name = "available_balance", nullable = false, precision = 18, scale = 2)
     BigDecimal availableBalance;

    @Column(name = "hold_balance", nullable = false, precision = 18, scale = 2)
     BigDecimal holdBalance;

    @Column(nullable = false, length = 30)
     String status;

    @Column(name = "is_platform", nullable = false)
     Boolean isPlatform;

    @Version
    @Column(nullable = false)
     Long version;



    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
     OffsetDateTime updatedAt;


}
