package com.ybank.core.transfer;

import com.ybank.core.common.ApiResponse;
import com.ybank.core.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
class TransferController {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;

    TransferController(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @GetMapping("/banks")
    ApiResponse<List<BankDto>> banks() {
        return ApiResponse.ok("Banks", jdbc.query("""
                SELECT code, name, transfer_fee
                FROM external_banks
                WHERE active = TRUE
                ORDER BY CASE WHEN code = 'YBANK' THEN 0 ELSE 1 END, name
                """, (rs, rowNum) -> new BankDto(
                rs.getString("code"),
                rs.getString("name"),
                money(rs.getBigDecimal("transfer_fee"))
        )));
    }

    @PostMapping
    @Transactional
    ApiResponse<TransferDto> create(@Valid @RequestBody TransferRequest request, Authentication authentication) {
        Long customerId = currentCustomerId(authentication);
        BigDecimal amount = validAmount(request.amount());
        String currency = normalizeCurrency(request.currency());
        String bankCode = normalizeBankCode(request.destinationBankCode());
        BankDto bank = findBank(bankCode);

        AccountRow origin = lockOrigin(customerId, request.originAccount());
        if (!origin.currency().equalsIgnoreCase(currency)) {
            throw new BusinessException("Moneda de la cuenta origen no coincide");
        }

        BigDecimal totalDebit = amount.add(new BigDecimal(bank.transferFee()));
        if (origin.balance().compareTo(totalDebit) < 0) {
            throw new BusinessException("Saldo insuficiente");
        }

        AccountRow destination = null;
        boolean internalTransfer = "YBANK".equals(bankCode);
        if (internalTransfer) {
            destination = lockDestination(request.destinationAccount());
            if (destination.id().equals(origin.id())) {
                throw new BusinessException("La cuenta destino debe ser diferente");
            }
            if (!destination.currency().equalsIgnoreCase(currency)) {
                throw new BusinessException("Moneda de la cuenta destino no coincide");
            }
        }

        jdbc.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", totalDebit, origin.id());
        if (destination != null) {
            jdbc.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", amount, destination.id());
        }

        String operationId = UUID.randomUUID().toString();
        String status = internalTransfer ? "COMPLETED" : "SENT_TO_BANK";
        Long transferId = jdbc.queryForObject("""
                INSERT INTO transfers(customer_id, origin_account, destination_account, amount, currency, status, description, created_at, destination_bank_code, operation_id, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?, NOW())
                RETURNING id
                """, Long.class,
                customerId,
                origin.accountNumber(),
                cleanText(request.destinationAccount()),
                amount,
                currency,
                status,
                cleanText(request.description()),
                bankCode,
                operationId
        );

        insertMovement(
                origin.id(),
                "Transferencia enviada",
                bank.name() + " - " + cleanText(request.destinationAccount()),
                totalDebit.negate(),
                currency,
                "DEBIT",
                internalTransfer ? "TRANSFER" : "EXTERNAL_TRANSFER"
        );
        if (destination != null) {
            insertMovement(
                    destination.id(),
                    "Transferencia recibida",
                    "Desde " + origin.accountNumber(),
                    amount,
                    currency,
                    "CREDIT",
                    "TRANSFER"
            );
        }

        publishTransferEvent(transferId, operationId);
        return ApiResponse.ok("Transfer processed", findTransfer(transferId, customerId));
    }

    @GetMapping
    ApiResponse<List<TransferDto>> history(Authentication authentication) {
        Long customerId = currentCustomerId(authentication);
        return ApiResponse.ok("Transfer history", jdbc.query("""
                SELECT id, operation_id, origin_account, destination_account, destination_bank_code, amount, currency, status, description, created_at
                FROM transfers
                WHERE customer_id = ?
                ORDER BY created_at DESC
                LIMIT 20
                """, (rs, rowNum) -> mapTransfer(rs), customerId));
    }

    private TransferDto findTransfer(Long id, Long customerId) {
        var rows = jdbc.query("""
                SELECT id, operation_id, origin_account, destination_account, destination_bank_code, amount, currency, status, description, created_at
                FROM transfers
                WHERE id = ? AND customer_id = ?
                """, (rs, rowNum) -> mapTransfer(rs), id, customerId);
        if (rows.isEmpty()) throw new BusinessException("Transferencia no encontrada");
        return rows.get(0);
    }

    private TransferDto mapTransfer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TransferDto(
                rs.getLong("id"),
                rs.getString("operation_id"),
                rs.getString("origin_account"),
                rs.getString("destination_account"),
                rs.getString("destination_bank_code"),
                money(rs.getBigDecimal("amount")),
                rs.getString("currency"),
                rs.getString("status"),
                safe(rs.getString("description")),
                instant(rs.getTimestamp("created_at"))
        );
    }

    private AccountRow lockOrigin(Long customerId, String accountNumber) {
        var rows = jdbc.query("""
                SELECT id, customer_id, account_number, currency, balance
                FROM accounts
                WHERE customer_id = ? AND account_number = ? AND active = TRUE
                FOR UPDATE
                """, (rs, rowNum) -> mapAccount(rs), customerId, cleanText(accountNumber));
        if (rows.isEmpty()) throw new BusinessException("Cuenta origen no encontrada");
        return rows.get(0);
    }

    private AccountRow lockDestination(String accountNumber) {
        var rows = jdbc.query("""
                SELECT id, customer_id, account_number, currency, balance
                FROM accounts
                WHERE account_number = ? AND active = TRUE
                FOR UPDATE
                """, (rs, rowNum) -> mapAccount(rs), cleanText(accountNumber));
        if (rows.isEmpty()) throw new BusinessException("Cuenta destino no existe en YBank");
        return rows.get(0);
    }

    private AccountRow mapAccount(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AccountRow(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getString("account_number"),
                rs.getString("currency"),
                rs.getBigDecimal("balance")
        );
    }

    private void insertMovement(Long accountId, String title, String description, BigDecimal amount, String currency, String direction, String category) {
        jdbc.update("""
                INSERT INTO account_movements(account_id, title, description, amount, currency, direction, category, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                """, accountId, title, description, amount, currency, direction, category);
    }

    private BankDto findBank(String code) {
        var rows = jdbc.query("""
                SELECT code, name, transfer_fee
                FROM external_banks
                WHERE code = ? AND active = TRUE
                """, (rs, rowNum) -> new BankDto(
                rs.getString("code"),
                rs.getString("name"),
                money(rs.getBigDecimal("transfer_fee"))
        ), code);
        if (rows.isEmpty()) throw new BusinessException("Banco destino no existe");
        return rows.get(0);
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

    private String normalizeCurrency(String value) {
        return (value == null || value.isBlank()) ? "PEN" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeBankCode(String value) {
        return (value == null || value.isBlank()) ? "YBANK" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private void publishTransferEvent(Long transferId, String operationId) {
        try {
            kafka.send("banking.transfer.completed", transferId + ":" + operationId);
        } catch (RuntimeException ignored) {
            // La transaccion bancaria no debe fallar si Kafka no esta disponible en desarrollo local.
        }
    }
}

record TransferRequest(
        @NotBlank String originAccount,
        @NotBlank String destinationAccount,
        String destinationBankCode,
        @DecimalMin("1.00") BigDecimal amount,
        String currency,
        String description
        ) {}

record TransferDto(
        Long id,
        String operationId,
        String originAccount,
        String destinationAccount,
        String destinationBankCode,
        String amount,
        String currency,
        String status,
        String description,
        Instant createdAt
        ) {}

record BankDto(String code, String name, String transferFee) {}
record AccountRow(Long id, Long customerId, String accountNumber, String currency, BigDecimal balance) {}