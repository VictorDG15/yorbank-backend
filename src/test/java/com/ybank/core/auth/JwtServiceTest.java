package com.ybank.core.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private JwtService jwtService;
    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET);
    }

    @Test
    void testCreateAndVerifyToken() {
        User user = User.builder()
                .id(1L)
                .email("test@ybank.pe")
                .role(Role.CUSTOMER)
                .build();

        String token = jwtService.create(user, 3600);
        assertNotNull(token);

        String subject = jwtService.subject(token);
        assertEquals("test@ybank.pe", subject);
    }
}
