package com.ybank.core.payment;

import com.ybank.core.common.ApiResponse;
import com.ybank.core.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController {
    private final JdbcTemplate jdbc;

    PaymentController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/services")
    ApiResponse<List<ServiceBill>> services() {
        return ApiResponse.ok("Bill services", jdbc.query("""
                SELECT service_code, category, provider, title
                FROM service_bills
                WHERE active = TRUE
                ORDER BY category, provider
                """, (rs, rowNum) -> new ServiceBill(
                rs.getString("service_code"),
                rs.getString("category"),
                rs.getString("provider"),
                rs.getString("title")
        )));
    }

    @PostMapping
    @Transactional
    ApiResponse<PaymentReceipt> pay(@Valid @RequestBody PaymentRequest request, Authentication authentication) {
        Long customerId = currentCustomerId(authentication);
        BigDecimal amount = validAmount(request.amount());
        ServiceBill service = findService(request.serviceCode());
        AccountRow account = debit(customerId, request.accountNumber(), amount, "PEN");

        String operationId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO bill_payments(operation_id, customer_id, service_code, account_number, amount, currency, status, paid_at)
                VALUES (?, ?, ?, ?, ?, 'PEN', 'PAID', NOW())
                """, operationId, customerId, service.code(), account.accountNumber(), amount);
        insertMovement(account.id(), "Pago " + service.provider(), service.title(), amount.negate(), "PEN", "DEBIT", "SERVICES");

        return ApiResponse.ok("Payment processed", new PaymentReceipt(operationId, service.code(), amount, "PAID", Instant.now()));
    }

    @GetMapping("/yape-contacts")
    ApiResponse<List<YapeContact>> yapeContacts(Authentication authentication) {
        Long customerId = currentCustomerId(authentication);
        return ApiResponse.ok("Yape contacts", jdbc.query("""
                SELECT phone, alias
                FROM yape_contacts
                WHERE customer_id = ? AND active = TRUE
                ORDER BY alias
                """, (rs, rowNum) -> new YapeContact(rs.getString("phone"), rs.getString("alias")), customerId));
    }

    @PostMapping("/yape")
    @Transactional
    ApiResponse<PaymentReceipt> yape(@Valid @RequestBody YapeRequest request, Authentication authentication) {
        Long customerId = currentCustomerId(authentication);
        BigDecimal amount = validAmount(request.amount());
        AccountRow account = debit(customerId, request.originAccount(), amount, "PEN");
        String phone = digits(request.phone());
        if (phone.length() < 9) throw new BusinessException("Celular Yape invalido");
        String alias = yapeAlias(customerId, phone);
        String operationId = UUID.randomUUID().toString();

        jdbc.update("""
                INSERT INTO yape_payments(operation_id, customer_id, origin_account, phone, amount, status, created_at)
                VALUES (?, ?, ?, ?, ?, 'PAID', NOW())
                """, operationId, customerId, account.accountNumber(), phone, amount);
        insertMovement(account.id(), "Yape enviado", alias, amount.negate(), "PEN", "DEBIT", "YAPE");

        return ApiResponse.ok("Yape processed", new PaymentReceipt(operationId, phone, amount, "PAID", Instant.now()));
    }

    @GetMapping("/mobile-operators")
    ApiResponse<List<MobileOperator>> mobileOperators() {
        return ApiResponse.ok("Mobile operators", jdbc.query("""
                SELECT code, name
                FROM mobile_operators
                WHERE active = TRUE
                ORDER BY name
                """, (rs, rowNum) -> new MobileOperator(rs.getString("code"), rs.getString("name"))));
    }

    @PostMapping("/recharges")
    @Transactional
    ApiResponse<PaymentReceipt> recharge(@Valid @RequestBody RechargeRequest request, Authentication authentication) {
        Long customerId = currentCustomerId(authentication);
        BigDecimal amount = validAmount(request.amount());
        AccountRow account = debit(customerId, request.originAccount(), amount, "PEN");
        MobileOperator operator = findOperator(request.operatorCode());
        String phone = digits(request.phone());
        if (phone.length() < 9) throw new BusinessException("Celular invalido");
        String operationId = UUID.randomUUID().toString();

        jdbc.update("""
                INSERT INTO mobile_recharges(operation_id, customer_id, origin_account, operator_code, phone, amount, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PAID', NOW())
                """, operationId, customerId, account.accountNumber(), operator.code(), phone, amount);
        insertMovement(account.id(), "Recarga " + operator.name(), phone, amount.negate(), "PEN", "DEBIT", "RECHARGE");

        return ApiResponse.ok("Recharge processed", new PaymentReceipt(operationId, operator.code(), amount, "PAID", Instant.now()));
    }

    private AccountRow debit(Long customerId, String accountNumber, BigDecimal amount, String currency) {
        AccountRow account = lockAccount(customerId, accountNumber);
        if (!account.currency().equalsIgnoreCase(currency)) {
            throw new BusinessException("Moneda de la cuenta no coincide");
        }
        if (account.balance().compareTo(amount) < 0) {
            throw new BusinessException("Saldo insuficiente");
        }
        jdbc.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", amount, account.id());
        return account;
    }

    private AccountRow lockAccount(Long customerId, String accountNumber) {
        var rows = jdbc.query("""
                SELECT id, account_number, currency, balance
                FROM accounts
                WHERE customer_id = ? AND account_number = ? AND active = TRUE
                FOR UPDATE
                """, (rs, rowNum) -> new AccountRow(
                rs.getLong("id"),
                rs.getString("account_number"),
                rs.getString("currency"),
                rs.getBigDecimal("balance")
        ), customerId, cleanText(accountNumber));
        if (rows.isEmpty()) throw new BusinessException("Cuenta no encontrada");
        return rows.get(0);
    }

    private void insertMovement(Long accountId, String title, String description, BigDecimal amount, String currency, String direction, String category) {
        jdbc.update("""
                INSERT INTO account_movements(account_id, title, description, amount, currency, direction, category, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                """, accountId, title, description, amount, currency, direction, category);
    }

    private ServiceBill findService(String serviceCode) {
        var rows = jdbc.query("""
                SELECT service_code, category, provider, title
                FROM service_bills
                WHERE service_code = ? AND active = TRUE
                """, (rs, rowNum) -> new ServiceBill(
                rs.getString("service_code"),
                rs.getString("category"),
                rs.getString("provider"),
                rs.getString("title")
        ), cleanText(serviceCode));
        if (rows.isEmpty()) throw new BusinessException("Servicio no encontrado");
        return rows.get(0);
    }

    private MobileOperator findOperator(String operatorCode) {
        String code = cleanText(operatorCode).toUpperCase(Locale.ROOT);
        var rows = jdbc.query("""
                SELECT code, name
                FROM mobile_operators
                WHERE code = ? AND active = TRUE
                """, (rs, rowNum) -> new MobileOperator(rs.getString("code"), rs.getString("name")), code);
        if (rows.isEmpty()) throw new BusinessException("Operador no encontrado");
        return rows.get(0);
    }

    private String yapeAlias(Long customerId, String phone) {
        var rows = jdbc.query("""
                SELECT alias
                FROM yape_contacts
                WHERE customer_id = ? AND phone = ? AND active = TRUE
                """, (rs, rowNum) -> rs.getString("alias"), customerId, phone);
        return rows.isEmpty() ? phone : rows.get(0);
    }

    private Long currentCustomerId(Authentication authentication) {
        String email = currentEmail(authentication);
        var ids = jdbc.query("SELECT id FROM users WHERE email = ?", (rs, rowNum) -> rs.getLong("id"), email);
        if (ids.isEmpty()) throw new BusinessException("Cliente no encontrado");
        return ids.get(0);
    }

    private String currentEmail(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("Sesion expirada. Inicia sesion nuevamente");
        }
        return authentication.getName();
    }

    private BigDecimal validAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ONE) < 0) {
            throw new BusinessException("Monto invalido");
        }
        return amount;
    }

    private String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }
}

record ServiceBill(String code, String category, String provider, String title) {}
record PaymentRequest(@NotBlank String serviceCode, @DecimalMin("1.00") BigDecimal amount, @NotBlank String accountNumber) {}
record PaymentReceipt(String operationId, String serviceCode, BigDecimal amount, String status, Instant paidAt) {}
record YapeContact(String phone, String alias) {}
record YapeRequest(@NotBlank String originAccount, @NotBlank String phone, @DecimalMin("1.00") BigDecimal amount, String note) {}
record MobileOperator(String code, String name) {}
record RechargeRequest(@NotBlank String originAccount, @NotBlank String operatorCode, @NotBlank String phone, @DecimalMin("1.00") BigDecimal amount) {}
record AccountRow(Long id, String accountNumber, String currency, BigDecimal balance) {}