package com.ybank.core.card;

import com.ybank.core.common.ApiResponse;
import com.ybank.core.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/cards")
class CardController {
    private final JdbcTemplate jdbc;

    CardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    ApiResponse<List<CardDto>> cards(Authentication authentication) {
        return ApiResponse.ok("Cards", jdbc.query("""
                SELECT c.id, c.card_number, c.type, c.brand, c.active
                FROM user_cards c
                INNER JOIN users u ON u.id = c.customer_id
                WHERE u.email = ?
                ORDER BY c.id ASC
                """, (rs, rowNum) -> new CardDto(
                rs.getLong("id"),
                maskCard(rs.getString("card_number")),
                rs.getString("type"),
                rs.getBoolean("active") ? "ACTIVE" : "LOCKED",
                rs.getString("brand")
        ), currentEmail(authentication)));
    }

    @PatchMapping("/{id}/status")
    ApiResponse<CardDto> status(@PathVariable Long id, @RequestBody CardStatusRequest request, Authentication authentication) {
        String email = currentEmail(authentication);
        String normalized = request.status() == null ? "ACTIVE" : request.status().trim().toUpperCase(Locale.ROOT);
        boolean active = !("LOCKED".equals(normalized) || "BLOCKED".equals(normalized) || "INACTIVE".equals(normalized));
        int updated = jdbc.update("""
                UPDATE user_cards c
                SET active = ?
                FROM users u
                WHERE c.customer_id = u.id
                  AND c.id = ?
                  AND u.email = ?
                """, active, id, email);
        if (updated == 0) throw new BusinessException("Tarjeta no encontrada");
        return ApiResponse.ok("Card status updated", findCard(id, email));
    }

    private CardDto findCard(Long id, String email) {
        var rows = jdbc.query("""
                SELECT c.id, c.card_number, c.type, c.brand, c.active
                FROM user_cards c
                INNER JOIN users u ON u.id = c.customer_id
                WHERE c.id = ? AND u.email = ?
                """, (rs, rowNum) -> new CardDto(
                rs.getLong("id"),
                maskCard(rs.getString("card_number")),
                rs.getString("type"),
                rs.getBoolean("active") ? "ACTIVE" : "LOCKED",
                rs.getString("brand")
        ), id, email);
        if (rows.isEmpty()) throw new BusinessException("Tarjeta no encontrada");
        return rows.get(0);
    }

    private String currentEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("Sesion invalida");
        }
        return authentication.getName();
    }

    private static String maskCard(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() <= 4) return "**** **** **** " + digits;
        return "**** **** **** " + digits.substring(digits.length() - 4);
    }
}

record CardDto(Long id, String maskedNumber, String type, String status, String brand) {}
record CardStatusRequest(String status) {}