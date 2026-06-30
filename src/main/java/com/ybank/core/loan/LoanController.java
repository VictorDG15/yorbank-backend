package com.ybank.core.loan;

import com.ybank.core.common.ApiResponse;
import com.ybank.core.common.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
class LoanController {
    private static final BigDecimal MAX_DIGITAL_AMOUNT = new BigDecimal("10000.00");
    private static final BigDecimal MIN_DIGITAL_AMOUNT = new BigDecimal("500.00");
    private static final BigDecimal INSURANCE_RATE = new BigDecimal("0.00038");
    private static final BigDecimal COMMISSION = new BigDecimal("8.00");

    private final JdbcTemplate jdbc;

    LoanController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/products")
    ApiResponse<List<LoanProduct>> products() {
        return ApiResponse.ok("Loan products", jdbc.query("""
                SELECT id, name, annual_rate, min_amount, max_amount, min_months, max_months
                FROM loan_products
                WHERE active = TRUE
                ORDER BY annual_rate ASC
                """, (rs, rowNum) -> new LoanProduct(
                rs.getLong("id"),
                rs.getString("name"),
                money(rs.getBigDecimal("annual_rate")),
                money(rs.getBigDecimal("min_amount")),
                money(rs.getBigDecimal("max_amount")),
                rs.getInt("min_months"),
                rs.getInt("max_months")
        )));
    }

    @PostMapping("/simulate")
    ApiResponse<LoanSimulation> simulate(@RequestBody LoanRequest request, Authentication authentication) {
        Long customerId = currentCustomerId(authentication);
        validateAccount(customerId, request.accountNumber());
        ProductRow product = findProduct(request.productId());
        LoanPlan plan = buildPlan(product, request);
        return ApiResponse.ok("Loan simulation", plan.toResponse(null, null, "SIMULATED"));
    }

    @PostMapping("/applications")
    @Transactional
    ApiResponse<LoanApplicationResponse> apply(@RequestBody LoanRequest request, Authentication authentication) {
        Long customerId = currentCustomerId(authentication);
        AccountRow account = lockAccount(customerId, request.accountNumber());
        ProductRow product = findProduct(request.productId());
        LoanPlan plan = buildPlan(product, request);
        String operationId = UUID.randomUUID().toString();
        String status = "APPROVED_DISBURSED";

        Long applicationId = jdbc.queryForObject("""
                INSERT INTO loan_applications(
                  customer_id, account_number, product_id, amount, months, annual_rate,
                  monthly_payment, total_interest, total_insurance, total_commission, total_payment, tcea,
                  start_date, first_due_date, payment_day, purpose, declared_monthly_income,
                  capacity_status, status, operation_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                RETURNING id
                """, Long.class,
                customerId,
                account.accountNumber(),
                product.id(),
                plan.amount(),
                plan.months(),
                product.annualRate(),
                plan.monthlyPayment(),
                plan.totalInterest(),
                plan.totalInsurance(),
                plan.totalCommission(),
                plan.totalPayment(),
                plan.tcea(),
                Date.valueOf(plan.startDate()),
                Date.valueOf(plan.firstDueDate()),
                plan.paymentDay(),
                plan.purpose(),
                plan.declaredMonthlyIncome(),
                plan.capacityStatus(),
                status,
                operationId
        );

        for (LoanInstallment row : plan.schedule()) {
            jdbc.update("""
                    INSERT INTO loan_installments(
                      loan_application_id, installment_number, due_date, opening_balance,
                      amortization, interest, insurance, commission, payment_amount, closing_balance, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                    """,
                    applicationId,
                    row.number(),
                    Date.valueOf(row.dueDate()),
                    row.openingBalance(),
                    row.amortization(),
                    row.interest(),
                    row.insurance(),
                    row.commission(),
                    row.paymentAmount(),
                    row.closingBalance());
        }

        jdbc.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", plan.amount(), account.id());
        jdbc.update("""
                INSERT INTO account_movements(account_id, title, description, amount, currency, direction, category, created_at)
                VALUES (?, 'Desembolso de prestamo', ?, ?, 'PEN', 'CREDIT', 'LOAN', NOW())
                """, account.id(), product.name(), plan.amount());

        return ApiResponse.ok("Loan approved", new LoanApplicationResponse(
                applicationId,
                operationId,
                status,
                plan.toResponse(applicationId, operationId, status)
        ));
    }

    @GetMapping("/applications")
    ApiResponse<List<LoanApplicationSummary>> applications(Authentication authentication) {
        Long customerId = currentCustomerId(authentication);
        return ApiResponse.ok("Loan applications", jdbc.query("""
                SELECT la.id, la.operation_id, lp.name, la.amount, la.months, la.monthly_payment, la.status, la.created_at
                FROM loan_applications la
                INNER JOIN loan_products lp ON lp.id = la.product_id
                WHERE la.customer_id = ?
                ORDER BY la.created_at DESC
                """, (rs, rowNum) -> new LoanApplicationSummary(
                rs.getLong("id"),
                rs.getString("operation_id"),
                rs.getString("name"),
                money(rs.getBigDecimal("amount")),
                rs.getInt("months"),
                money(rs.getBigDecimal("monthly_payment")),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant()
        ), customerId));
    }

    @GetMapping("/applications/{id}/schedule.pdf")
    ResponseEntity<byte[]> schedulePdf(@PathVariable Long id, Authentication authentication) {
        Long customerId = currentCustomerId(authentication);
        LoanPdfData data = loadPdfData(id, customerId);
        byte[] pdf = SimplePdf.loanSchedule(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cronograma-prestamo-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private LoanPlan buildPlan(ProductRow product, LoanRequest request) {
        BigDecimal amount = validAmount(request.amount(), product);
        int months = validMonths(request.months(), product);
        int paymentDay = validPaymentDay(request.paymentDay());
        LocalDate startDate = parseStartDate(request.startDate());
        LocalDate firstDueDate = dateWithDay(startDate.plusMonths(1), paymentDay);
        BigDecimal declaredIncome = valueOrZero(request.declaredMonthlyIncome());
        String purpose = cleanText(request.purpose()).isBlank() ? "Libre disponibilidad" : cleanText(request.purpose());

        double monthlyRate = Math.pow(1 + product.annualRate().doubleValue() / 100.0, 1.0 / 12.0) - 1;
        double principal = amount.doubleValue();
        double factor = Math.pow(1 + monthlyRate, months);
        double baseInstallment = principal * monthlyRate * factor / (factor - 1);

        List<LoanInstallment> schedule = new ArrayList<>();
        BigDecimal balance = amount;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalInsurance = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        BigDecimal totalPayment = BigDecimal.ZERO;
        List<BigDecimal> payments = new ArrayList<>();

        for (int i = 1; i <= months; i++) {
            BigDecimal opening = balance;
            BigDecimal interest = moneyValue(opening.multiply(BigDecimal.valueOf(monthlyRate)));
            BigDecimal amortization = moneyValue(BigDecimal.valueOf(baseInstallment).subtract(interest));
            if (i == months || amortization.compareTo(opening) > 0) {
                amortization = opening;
            }
            BigDecimal insurance = moneyValue(opening.multiply(INSURANCE_RATE));
            BigDecimal payment = moneyValue(amortization.add(interest).add(insurance).add(COMMISSION));
            BigDecimal closing = moneyValue(opening.subtract(amortization).max(BigDecimal.ZERO));
            LocalDate dueDate = dateWithDay(firstDueDate.plusMonths(i - 1L), paymentDay);

            schedule.add(new LoanInstallment(
                    i,
                    dueDate,
                    opening,
                    amortization,
                    interest,
                    insurance,
                    COMMISSION,
                    payment,
                    closing
            ));

            totalInterest = totalInterest.add(interest);
            totalInsurance = totalInsurance.add(insurance);
            totalCommission = totalCommission.add(COMMISSION);
            totalPayment = totalPayment.add(payment);
            payments.add(payment);
            balance = closing;
        }

        BigDecimal monthlyPayment = schedule.isEmpty() ? BigDecimal.ZERO : schedule.get(0).paymentAmount();
        BigDecimal tcea = moneyValue(BigDecimal.valueOf((Math.pow(1 + monthlyIrr(amount, payments), 12) - 1) * 100));
        String capacityStatus = capacityStatus(monthlyPayment, declaredIncome);
        String advice = adviceFor(capacityStatus);

        return new LoanPlan(
                product,
                amount,
                months,
                paymentDay,
                startDate,
                firstDueDate,
                purpose,
                declaredIncome,
                monthlyPayment,
                moneyValue(totalInterest),
                moneyValue(totalInsurance),
                moneyValue(totalCommission),
                moneyValue(totalPayment),
                tcea,
                capacityStatus,
                advice,
                schedule
        );
    }

    private LoanPdfData loadPdfData(Long id, Long customerId) {
        var headers = jdbc.query("""
                SELECT la.id, la.operation_id, la.account_number, la.amount, la.months, la.annual_rate,
                       la.monthly_payment, la.total_payment, la.tcea, la.status, la.purpose,
                       u.full_name, lp.name AS product_name
                FROM loan_applications la
                INNER JOIN users u ON u.id = la.customer_id
                INNER JOIN loan_products lp ON lp.id = la.product_id
                WHERE la.id = ? AND la.customer_id = ?
                """, (rs, rowNum) -> new LoanPdfHeader(
                rs.getLong("id"),
                rs.getString("operation_id"),
                rs.getString("full_name"),
                rs.getString("account_number"),
                rs.getString("product_name"),
                money(rs.getBigDecimal("amount")),
                rs.getInt("months"),
                money(rs.getBigDecimal("annual_rate")),
                money(rs.getBigDecimal("monthly_payment")),
                money(rs.getBigDecimal("total_payment")),
                money(rs.getBigDecimal("tcea")),
                rs.getString("status"),
                rs.getString("purpose")
        ), id, customerId);
        if (headers.isEmpty()) throw new BusinessException("Prestamo no encontrado");
        List<LoanInstallment> schedule = jdbc.query("""
                SELECT installment_number, due_date, opening_balance, amortization, interest, insurance, commission, payment_amount, closing_balance
                FROM loan_installments
                WHERE loan_application_id = ?
                ORDER BY installment_number
                """, (rs, rowNum) -> new LoanInstallment(
                rs.getInt("installment_number"),
                rs.getDate("due_date").toLocalDate(),
                rs.getBigDecimal("opening_balance"),
                rs.getBigDecimal("amortization"),
                rs.getBigDecimal("interest"),
                rs.getBigDecimal("insurance"),
                rs.getBigDecimal("commission"),
                rs.getBigDecimal("payment_amount"),
                rs.getBigDecimal("closing_balance")
        ), id);
        return new LoanPdfData(headers.get(0), schedule);
    }

    private ProductRow findProduct(Long productId) {
        Long id = productId == null ? firstProductId() : productId;
        var rows = jdbc.query("""
                SELECT id, name, annual_rate, min_amount, max_amount, min_months, max_months
                FROM loan_products
                WHERE id = ? AND active = TRUE
                """, (rs, rowNum) -> new ProductRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getBigDecimal("annual_rate"),
                rs.getBigDecimal("min_amount"),
                rs.getBigDecimal("max_amount"),
                rs.getInt("min_months"),
                rs.getInt("max_months")
        ), id);
        if (rows.isEmpty()) throw new BusinessException("Producto de prestamo no encontrado");
        return rows.get(0);
    }

    private Long firstProductId() {
        return jdbc.queryForObject("SELECT id FROM loan_products WHERE active = TRUE ORDER BY annual_rate ASC LIMIT 1", Long.class);
    }

    private void validateAccount(Long customerId, String accountNumber) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM accounts WHERE customer_id = ? AND account_number = ? AND active = TRUE
                """, Integer.class, customerId, cleanText(accountNumber));
        if (count == null || count == 0) throw new BusinessException("Cuenta de desembolso no encontrada");
    }

    private AccountRow lockAccount(Long customerId, String accountNumber) {
        var rows = jdbc.query("""
                SELECT id, account_number
                FROM accounts
                WHERE customer_id = ? AND account_number = ? AND active = TRUE
                FOR UPDATE
                """, (rs, rowNum) -> new AccountRow(rs.getLong("id"), rs.getString("account_number")), customerId, cleanText(accountNumber));
        if (rows.isEmpty()) throw new BusinessException("Cuenta de desembolso no encontrada");
        return rows.get(0);
    }

    private Long currentCustomerId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("Sesion expirada. Inicia sesion nuevamente");
        }
        var rows = jdbc.query("SELECT id FROM users WHERE email = ?", (rs, rowNum) -> rs.getLong("id"), authentication.getName());
        if (rows.isEmpty()) throw new BusinessException("Cliente no encontrado");
        return rows.get(0);
    }

    private BigDecimal validAmount(BigDecimal amount, ProductRow product) {
        if (amount == null) throw new BusinessException("Monto requerido");
        BigDecimal maxProduct = product.maxAmount().min(MAX_DIGITAL_AMOUNT);
        if (amount.compareTo(MIN_DIGITAL_AMOUNT) < 0) {
            throw new BusinessException("El monto minimo es S/ 500.00");
        }
        if (amount.compareTo(maxProduct) > 0) {
            throw new BusinessException("El monto maximo digital es S/ 10,000.00");
        }
        if (amount.compareTo(product.minAmount()) < 0 || amount.compareTo(product.maxAmount()) > 0) {
            throw new BusinessException("Monto fuera del rango del producto");
        }
        return moneyValue(amount);
    }

    private int validMonths(Integer months, ProductRow product) {
        if (months == null) throw new BusinessException("Plazo requerido");
        if (months < product.minMonths() || months > product.maxMonths()) {
            throw new BusinessException("Plazo fuera del rango del producto");
        }
        return months;
    }

    private int validPaymentDay(Integer paymentDay) {
        int day = paymentDay == null ? 15 : paymentDay;
        if (day < 1 || day > 28) throw new BusinessException("Dia de pago invalido");
        return day;
    }

    private LocalDate parseStartDate(String value) {
        if (value == null || value.isBlank()) return LocalDate.now();
        LocalDate date = LocalDate.parse(value);
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) throw new BusinessException("La fecha de desembolso no puede ser anterior a hoy");
        if (date.isAfter(today.plusDays(60))) throw new BusinessException("La fecha de desembolso no puede superar 60 dias");
        return date;
    }

    private LocalDate dateWithDay(LocalDate date, int day) {
        int safeDay = Math.min(day, date.lengthOfMonth());
        return date.withDayOfMonth(safeDay);
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return moneyValue(value == null ? BigDecimal.ZERO : value);
    }

    private String capacityStatus(BigDecimal monthlyPayment, BigDecimal income) {
        if (income == null || income.compareTo(BigDecimal.ZERO) <= 0) return "SIN_INGRESO_DECLARADO";
        BigDecimal ratio = monthlyPayment.divide(income, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("0.30")) <= 0) return "SALUDABLE";
        if (ratio.compareTo(new BigDecimal("0.40")) <= 0) return "AJUSTADO";
        return "RIESGO_ALTO";
    }

    private String adviceFor(String status) {
        return switch (status) {
            case "SALUDABLE" -> "Tu cuota esta dentro de un rango saludable frente al ingreso declarado.";
            case "AJUSTADO" -> "La cuota es posible, pero conviene revisar gastos fijos antes de aceptar.";
            case "RIESGO_ALTO" -> "La cuota supera el nivel recomendado. Reduce monto o amplia plazo.";
            default -> "Declara tu ingreso mensual para estimar capacidad de pago.";
        };
    }

    private double monthlyIrr(BigDecimal principal, List<BigDecimal> payments) {
        double low = 0.000001;
        double high = 1.0;
        double p = principal.doubleValue();
        for (int i = 0; i < 80; i++) {
            double mid = (low + high) / 2;
            double npv = -p;
            for (int n = 0; n < payments.size(); n++) {
                npv += payments.get(n).doubleValue() / Math.pow(1 + mid, n + 1);
            }
            if (npv > 0) low = mid; else high = mid;
        }
        return (low + high) / 2;
    }

    private BigDecimal moneyValue(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00" : moneyValue(value).toPlainString();
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }
}

record LoanProduct(Long id, String name, String annualRate, String minAmount, String maxAmount, Integer minMonths, Integer maxMonths) {}
record LoanRequest(Long productId, BigDecimal amount, Integer months, String accountNumber, String startDate, Integer paymentDay, String purpose, BigDecimal declaredMonthlyIncome) {}
record LoanSimulation(Long applicationId, String operationId, String status, String productName, String amount, Integer months, String annualRate, String monthlyPayment, String totalInterest, String totalInsurance, String totalCommission, String totalPayment, String tcea, String startDate, String firstDueDate, Integer paymentDay, String purpose, String declaredMonthlyIncome, String capacityStatus, String advice, List<LoanInstallmentDto> schedule) {}
record LoanApplicationResponse(Long applicationId, String operationId, String status, LoanSimulation simulation) {}
record LoanApplicationSummary(Long id, String operationId, String productName, String amount, Integer months, String monthlyPayment, String status, Instant createdAt) {}
record LoanInstallmentDto(Integer number, String dueDate, String openingBalance, String amortization, String interest, String insurance, String commission, String paymentAmount, String closingBalance) {}
record ProductRow(Long id, String name, BigDecimal annualRate, BigDecimal minAmount, BigDecimal maxAmount, Integer minMonths, Integer maxMonths) {}
record AccountRow(Long id, String accountNumber) {}
record LoanPdfHeader(Long id, String operationId, String customerName, String accountNumber, String productName, String amount, Integer months, String annualRate, String monthlyPayment, String totalPayment, String tcea, String status, String purpose) {}
record LoanPdfData(LoanPdfHeader header, List<LoanInstallment> schedule) {}

record LoanInstallment(Integer number, LocalDate dueDate, BigDecimal openingBalance, BigDecimal amortization, BigDecimal interest, BigDecimal insurance, BigDecimal commission, BigDecimal paymentAmount, BigDecimal closingBalance) {
    LoanInstallmentDto toDto() {
        return new LoanInstallmentDto(
                number,
                dueDate.toString(),
                openingBalance.toPlainString(),
                amortization.toPlainString(),
                interest.toPlainString(),
                insurance.toPlainString(),
                commission.toPlainString(),
                paymentAmount.toPlainString(),
                closingBalance.toPlainString()
        );
    }
}

record LoanPlan(ProductRow product, BigDecimal amount, Integer months, Integer paymentDay, LocalDate startDate, LocalDate firstDueDate, String purpose, BigDecimal declaredMonthlyIncome, BigDecimal monthlyPayment, BigDecimal totalInterest, BigDecimal totalInsurance, BigDecimal totalCommission, BigDecimal totalPayment, BigDecimal tcea, String capacityStatus, String advice, List<LoanInstallment> schedule) {
    LoanSimulation toResponse(Long applicationId, String operationId, String status) {
        return new LoanSimulation(
                applicationId,
                operationId,
                status,
                product.name(),
                amount.toPlainString(),
                months,
                product.annualRate().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                monthlyPayment.toPlainString(),
                totalInterest.toPlainString(),
                totalInsurance.toPlainString(),
                totalCommission.toPlainString(),
                totalPayment.toPlainString(),
                tcea.toPlainString(),
                startDate.toString(),
                firstDueDate.toString(),
                paymentDay,
                purpose,
                declaredMonthlyIncome.toPlainString(),
                capacityStatus,
                advice,
                schedule.stream().map(LoanInstallment::toDto).toList()
        );
    }
}

class SimplePdf {
    private SimplePdf() {}

    static byte[] loanSchedule(LoanPdfData data) {
        List<String> lines = new ArrayList<>();
        LoanPdfHeader h = data.header();
        lines.add("YBank - Cronograma de prestamo");
        lines.add("Operacion: " + h.operationId());
        lines.add("Cliente: " + h.customerName());
        lines.add("Producto: " + h.productName());
        lines.add("Cuenta desembolso: " + h.accountNumber());
        lines.add("Monto: S/ " + h.amount() + "   Plazo: " + h.months() + " meses   TEA: " + h.annualRate() + "%   TCEA: " + h.tcea() + "%");
        lines.add("Cuota estimada: S/ " + h.monthlyPayment() + "   Total a pagar: S/ " + h.totalPayment());
        lines.add("Estado: " + h.status() + "   Finalidad: " + h.purpose());
        lines.add(" ");
        lines.add("N  Vence       SaldoIni   Amortiz.   Interes   Seguro   Comision   Cuota     SaldoFin");
        for (LoanInstallment row : data.schedule()) {
            lines.add(String.format(
                    "%02d %s %10s %10s %9s %8s %9s %9s %10s",
                    row.number(),
                    row.dueDate(),
                    row.openingBalance().toPlainString(),
                    row.amortization().toPlainString(),
                    row.interest().toPlainString(),
                    row.insurance().toPlainString(),
                    row.commission().toPlainString(),
                    row.paymentAmount().toPlainString(),
                    row.closingBalance().toPlainString()
            ));
        }
        lines.add(" ");
        lines.add("Documento generado por YBank Core Banking API.");
        return pdf(lines);
    }

    private static byte[] pdf(List<String> allLines) {
        List<List<String>> pages = new ArrayList<>();
        int pageSize = 42;
        for (int i = 0; i < allLines.size(); i += pageSize) {
            pages.add(allLines.subList(i, Math.min(i + pageSize, allLines.size())));
        }

        StringBuilder body = new StringBuilder();
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        appendObj(body, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        int fontObj = 3 + pages.size() * 2;
        for (int i = 0; i < pages.size(); i++) {
            kids.append(3 + i * 2).append(" 0 R ");
        }
        appendObj(body, offsets, 2, "<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>");
        for (int i = 0; i < pages.size(); i++) {
            int pageObj = 3 + i * 2;
            int contentObj = pageObj + 1;
            appendObj(body, offsets, pageObj, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 " + fontObj + " 0 R >> >> /Contents " + contentObj + " 0 R >>");
            String stream = streamFor(pages.get(i));
            appendObj(body, offsets, contentObj, "<< /Length " + stream.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + stream + "endstream");
        }
        appendObj(body, offsets, fontObj, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

        String header = "%PDF-1.4\n";
        int xrefStart = header.getBytes(StandardCharsets.ISO_8859_1).length + body.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        StringBuilder pdf = new StringBuilder(header).append(body);
        pdf.append("xref\n0 ").append(offsets.size()).append("\n0000000000 65535 f \n");
        int base = header.getBytes(StandardCharsets.ISO_8859_1).length;
        for (int i = 1; i < offsets.size(); i++) {
            pdf.append(String.format("%010d 00000 n \n", offsets.get(i) + base));
        }
        pdf.append("trailer\n<< /Size ").append(offsets.size()).append(" /Root 1 0 R >>\nstartxref\n").append(xrefStart).append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void appendObj(StringBuilder body, List<Integer> offsets, int number, String content) {
        while (offsets.size() <= number) offsets.add(0);
        offsets.set(number, body.toString().getBytes(StandardCharsets.ISO_8859_1).length);
        body.append(number).append(" 0 obj\n").append(content).append("\nendobj\n");
    }

    private static String streamFor(List<String> lines) {
        StringBuilder s = new StringBuilder("BT\n/F1 10 Tf\n50 800 Td\n");
        for (String line : lines) {
            s.append("(").append(escape(line)).append(") Tj\n0 -17 Td\n");
        }
        s.append("ET\n");
        return s.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}