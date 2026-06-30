package com.ybank.core.auth;

import jakarta.validation.constraints.*;

record RegisterRequest(@NotBlank String documentNumber, String documentType, String cardNumber, @Email String email, @NotBlank String fullName, @Size(min=6) String password) {}
record LoginPrepareRequest(@NotBlank String segment, @NotBlank String documentType, @NotBlank String documentNumber, @NotBlank String cardNumber) {}
record LoginPrepareResponse(String segment, String documentType, String documentNumber, String maskedCard, String cardType) {}
record LoginRequest(String segment, String documentType, @NotBlank String documentNumber, @NotBlank String cardNumber, @Email String email, @NotBlank @Size(min=6) String password) {}
record OtpRequest(@Email String email, @NotBlank String otp) {}
record TokenResponse(String accessToken, String refreshToken, String tokenType, Long expiresIn) {}
record MeResponse(Long id, String email, String fullName, String role) {}
