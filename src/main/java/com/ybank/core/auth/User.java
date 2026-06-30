package com.ybank.core.auth;
import jakarta.persistence.*;import lombok.*;import java.time.*;
@Getter@Setter@NoArgsConstructor@AllArgsConstructor@Builder@Entity@Table(name="users")
class User { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(unique=true,nullable=false) String documentNumber; @Column(unique=true,nullable=false) String email; @Column(nullable=false) String fullName; @Column(nullable=false) String passwordHash; @Enumerated(EnumType.STRING) Role role; boolean twoFactorEnabled; boolean active; Instant createdAt; }
enum Role { CUSTOMER, ADMIN }
