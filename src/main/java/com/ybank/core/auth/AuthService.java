package com.ybank.core.auth;

import com.ybank.core.common.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
class AuthService {
  private final UserRepository users;
  private final LoginCardRepository cards;
  private final PasswordEncoder encoder;
  private final JwtService jwt;

  AuthService(UserRepository users, LoginCardRepository cards, PasswordEncoder encoder, JwtService jwt) {
    this.users = users;
    this.cards = cards;
    this.encoder = encoder;
    this.jwt = jwt;
  }

  @Transactional
  TokenResponse register(RegisterRequest r) {
    String documentNumber = clean(r.documentNumber());
    String email = isBlank(r.email()) ? emailFor(documentNumber) : r.email();
    if (users.existsByEmail(email) || users.existsByDocumentNumber(documentNumber)) {
      throw new BusinessException("Customer already exists");
    }
    var u = users.save(User.builder()
        .email(email)
        .documentNumber(documentNumber)
        .fullName(r.fullName())
        .passwordHash(encoder.encode(r.password()))
        .role(Role.CUSTOMER)
        .active(true)
        .twoFactorEnabled(false)
        .createdAt(Instant.now())
        .build());
    return tokens(u);
  }

  LoginPrepareResponse prepareLogin(LoginPrepareRequest r) {
    var identity = validateIdentity(r.segment(), r.documentType(), r.documentNumber(), r.cardNumber());
    return new LoginPrepareResponse(
        identity.segment(),
        identity.documentType(),
        identity.documentNumber(),
        maskCard(identity.card().cardNumber),
        cardLabel(identity.card()));
  }

  @Transactional
  TokenResponse login(LoginRequest r) {
    var identity = validateIdentity(r.segment(), r.documentType(), r.documentNumber(), r.cardNumber());
    var user = identity.user();
    if (!passwordMatches(r.password(), user.passwordHash)) {
      throw new BusinessException("Clave invalida");
    }
    return tokens(user);
  }

  TokenResponse verifyOtp(OtpRequest r) {
    var u = users.findByEmail(r.email()).orElseThrow(() -> new BusinessException("User not found"));
    if (!"123456".equals(r.otp())) throw new BusinessException("Invalid OTP for demo");
    return tokens(u);
  }

  MeResponse me(String email) {
    var u = users.findByEmail(email).orElseThrow();
    return new MeResponse(u.id, u.email, u.fullName, u.role.name());
  }

  private ValidatedIdentity validateIdentity(String segment, String documentType, String documentNumber, String cardNumber) {
    String normalizedSegment = normalizeSegment(segment);
    String type = normalizeDocumentType(documentType);
    String doc = clean(documentNumber);
    String card = clean(cardNumber);

    if ("RUC".equals(type) && doc.length() != 11) throw new BusinessException("RUC invalido");
    if ("DNI".equals(type) && doc.length() != 8) throw new BusinessException("DNI invalido");
    if (doc.length() < 6) throw new BusinessException("Documento invalido");
    if (card.length() != 16) throw new BusinessException("Tarjeta invalida");

    var user = users.findByDocumentNumber(doc)
        .orElseThrow(() -> new BusinessException(type + " no existe"));
    var loginCard = cards.findByCardNumberAndActiveTrue(card)
        .orElseThrow(() -> new BusinessException("Tarjeta invalida"));

    if (!loginCard.customerId.equals(user.id)) {
      throw new BusinessException("La tarjeta no pertenece al documento");
    }

    return new ValidatedIdentity(normalizedSegment, type, doc, user, loginCard);
  }

  private String normalizeSegment(String value) {
    return isBlank(value) ? "PERSONAS" : value.trim().toUpperCase();
  }

  private String normalizeDocumentType(String value) {
    return isBlank(value) ? "DNI" : value.trim().toUpperCase();
  }

  private String clean(String value) {
    return value == null ? "" : value.replaceAll("\\D", "");
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String emailFor(String documentNumber) {
    return "cliente+" + documentNumber + "@ybank.local";
  }

  private boolean passwordMatches(String raw, String savedHash) {
    if (savedHash != null && savedHash.startsWith("{plain}")) {
      return savedHash.substring(7).equals(raw);
    }
    return encoder.matches(raw, savedHash);
  }

  private String maskCard(String value) {
    String card = clean(value);
    if (card.length() <= 4) return "**** " + card;
    return "**** **** **** " + card.substring(card.length() - 4);
  }

  private String cardLabel(LoginCard card) {
    String brand = isBlank(card.brand) ? cardType(card.cardNumber) : card.brand;
    String type = isBlank(card.type) ? "Tarjeta YBank" : card.type;
    return brand + " " + type;
  }

  private String cardType(String value) {
    String card = clean(value);
    if (card.startsWith("4")) return "VISA";
    if (card.startsWith("5")) return "MASTERCARD";
    return "Tarjeta YBank";
  }

  private TokenResponse tokens(User u) {
    return new TokenResponse(jwt.create(u, 900), jwt.create(u, 604800), "Bearer", 900L);
  }

  private record ValidatedIdentity(
      String segment,
      String documentType,
      String documentNumber,
      User user,
      LoginCard card) {}
}