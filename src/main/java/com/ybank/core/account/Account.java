package com.ybank.core.account;
import jakarta.persistence.*;import lombok.*;import java.math.BigDecimal;import java.time.Instant;
@Getter@Setter@NoArgsConstructor@AllArgsConstructor@Builder@Entity@Table(name="accounts")
class Account { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; Long customerId; @Column(unique=true) String accountNumber; String type; String currency; BigDecimal balance; boolean active; Instant createdAt; }
