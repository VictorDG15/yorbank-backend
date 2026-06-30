package com.ybank.core.auth;

import com.ybank.core.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
  private final AuthService service;

  AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/register")
  ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest r) {
    return ApiResponse.ok("Customer registered", service.register(r));
  }

  @PostMapping("/login/prepare")
  ApiResponse<LoginPrepareResponse> prepareLogin(@Valid @RequestBody LoginPrepareRequest r) {
    return ApiResponse.ok("Card validated", service.prepareLogin(r));
  }

  @PostMapping("/login")
  ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest r) {
    return ApiResponse.ok("Login successful", service.login(r));
  }

  @PostMapping("/otp/verify")
  ApiResponse<TokenResponse> otp(@Valid @RequestBody OtpRequest r) {
    return ApiResponse.ok("OTP verified", service.verifyOtp(r));
  }

  @GetMapping("/me")
  ApiResponse<MeResponse> me(Authentication a) {
    return ApiResponse.ok("Current customer", service.me(a.getName()));
  }
}
