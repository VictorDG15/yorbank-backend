package com.ybank.core.account;

import com.ybank.core.common.ApiResponse;
import com.ybank.core.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {

    private final AccountRepository repo;
    private final JdbcTemplate jdbc;

    AccountController(AccountRepository repo, JdbcTemplate jdbc) {
        this.repo = repo;
        this.jdbc = jdbc;
    }

    @GetMapping
    ApiResponse<List<AccountDto>> accounts(Authentication authentication) {
        Long customerId = currentCustomerId(authentication);
        return ApiResponse.ok("Accounts", repo.findByCustomerId(customerId).stream().map(AccountDto::of).toList());
    }

    @GetMapping("/home-summary")
    ApiResponse<HomeSummaryDto> homeSummary(Authentication authentication) {
        String email = currentEmail(authentication);
        var rows = jdbc.query("""
                SELECT u.full_name,
                       a.account_number,
                       a.type AS account_type,
                       a.currency,
                       a.balance,
                       c.card_number,
                       c.brand,
                       c.type AS card_type
                FROM users u
                LEFT JOIN accounts a ON a.customer_id = u.id AND a.active = true
                LEFT JOIN user_cards c ON c.customer_id = u.id AND c.active = true
                WHERE u.email = ?
                ORDER BY a.id ASC, c.id ASC
                LIMIT 1
                """,
            (rs, rowNum) -> new HomeSummaryDto(
                rs.getString("full_name"),
                safeString(rs.getString("account_number")),
                safeString(rs.getString("account_type")),
                safeString(rs.getString("currency")),
                balanceText(rs.getBigDecimal("balance")),
                maskCard(rs.getString("card_number")),
                cardLabel(rs.getString("brand"), rs.getString("card_type"))
            ),
            email
        );

        if (rows.isEmpty()) throw new BusinessException("Cliente no encontrado");
        return ApiResponse.ok("Home summary", rows.get(0));
    }

    @GetMapping("/movements")
    ApiResponse<List<MovementDto>> movements(Authentication authentication) {
        Long customerId = currentCustomerId(authentication);
        var items = jdbc.query("""
                SELECT m.id,
                       m.title,
                       m.description,
                       m.amount,
                       m.currency,
                       m.direction,
                       m.category,
                       m.created_at
                FROM account_movements m
                INNER JOIN accounts a ON a.id = m.account_id
                WHERE a.customer_id = ?
                ORDER BY m.created_at DESC
                LIMIT 20
                """,
            (rs, rowNum) -> new MovementDto(
                rs.getLong("id"),
                rs.getString("title"),
                safeString(rs.getString("description")),
                balanceText(rs.getBigDecimal("amount")),
                safeString(rs.getString("currency")),
                safeString(rs.getString("direction")),
                safeString(rs.getString("category")),
                instant(rs.getTimestamp("created_at"))
            ),
            customerId
        );
        return ApiResponse.ok("Account movements", items);
    }

    @GetMapping("/{accountNumber}")
    ApiResponse<AccountDto> detail(@PathVariable String accountNumber) {
        return ApiResponse.ok("Account detail", repo.findByAccountNumber(accountNumber).map(AccountDto::of).orElseThrow());
    }

    private Long currentCustomerId(Authentication authentication) {
        String email = currentEmail(authentication);
        var ids = jdbc.query("SELECT id FROM users WHERE email = ?", (rs, rowNum) -> rs.getLong("id"), email);
        if (ids.isEmpty()) throw new BusinessException("Cliente no encontrado");
        return ids.get(0);
    }

    private String currentEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("Sesion invalida");
        }
        return authentication.getName();
    }

    private static String balanceText(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static String maskCard(String value) {
        if (value == null || value.isBlank()) return "******";
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 4) return "******" + digits;
        return "******" + digits.substring(0, 4);
    }

    private static String cardLabel(String brand, String type) {
        String cleanBrand = safeString(brand).isBlank() ? "Tarjeta" : brand;
        String cleanType = safeString(type).isBlank() ? "" : " " + type;
        return cleanBrand + cleanType;
    }
}

record AccountDto(
        Long id,
        String accountNumber,
        String type,
        String currency,
        String balance,
        boolean active
        ) {

    static AccountDto of(Account a) {
        return new AccountDto(
                a.id,
                a.accountNumber,
                a.type,
                a.currency,
                a.balance.toPlainString(),
                a.active
        );
    }
}

record HomeSummaryDto(
        String customerName,
        String accountNumber,
        String accountType,
        String currency,
        String balance,
        String maskedCard,
        String cardType
        ) {}

record MovementDto(
        Long id,
        String title,
        String description,
        String amount,
        String currency,
        String direction,
        String category,
        Instant createdAt
        ) {}