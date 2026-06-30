package com.ybank.core.transfer;
import jakarta.persistence.*;import lombok.*;import java.math.BigDecimal;import java.time.Instant;
@Getter@Setter@NoArgsConstructor@AllArgsConstructor@Builder@Entity@Table(name="transfers")
class Transfer { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; String originAccount; String destinationAccount; BigDecimal amount; String currency; String status; String description; Instant createdAt; }
