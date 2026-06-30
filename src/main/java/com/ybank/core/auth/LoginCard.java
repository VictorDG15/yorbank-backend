package com.ybank.core.auth;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_cards")
class LoginCard {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "customer_id", nullable = false)
  Long customerId;

  @Column(name = "card_number", unique = true, nullable = false)
  String cardNumber;

  @Column(nullable = false)
  String type;

  @Column(nullable = false)
  String brand;

  boolean active;
}