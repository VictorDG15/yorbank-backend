package com.ybank.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

interface LoginCardRepository extends JpaRepository<LoginCard, Long> {
  Optional<LoginCard> findByCardNumberAndActiveTrue(String cardNumber);
}