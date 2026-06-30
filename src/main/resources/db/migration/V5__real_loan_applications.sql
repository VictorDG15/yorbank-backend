UPDATE loan_products
SET max_amount = 10000.00,
    min_amount = 500.00
WHERE active = TRUE;

CREATE TABLE IF NOT EXISTS loan_applications (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES users(id),
  account_number VARCHAR(30) NOT NULL,
  product_id BIGINT NOT NULL REFERENCES loan_products(id),
  amount NUMERIC(18,2) NOT NULL,
  months INTEGER NOT NULL,
  annual_rate NUMERIC(6,2) NOT NULL,
  monthly_payment NUMERIC(18,2) NOT NULL,
  total_interest NUMERIC(18,2) NOT NULL,
  total_insurance NUMERIC(18,2) NOT NULL,
  total_commission NUMERIC(18,2) NOT NULL,
  total_payment NUMERIC(18,2) NOT NULL,
  tcea NUMERIC(6,2) NOT NULL,
  start_date DATE NOT NULL,
  first_due_date DATE NOT NULL,
  payment_day INTEGER NOT NULL,
  purpose VARCHAR(80) NOT NULL,
  declared_monthly_income NUMERIC(18,2) NOT NULL DEFAULT 0,
  capacity_status VARCHAR(40) NOT NULL,
  status VARCHAR(40) NOT NULL,
  operation_id VARCHAR(80) NOT NULL UNIQUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS loan_installments (
  id BIGSERIAL PRIMARY KEY,
  loan_application_id BIGINT NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,
  installment_number INTEGER NOT NULL,
  due_date DATE NOT NULL,
  opening_balance NUMERIC(18,2) NOT NULL,
  amortization NUMERIC(18,2) NOT NULL,
  interest NUMERIC(18,2) NOT NULL,
  insurance NUMERIC(18,2) NOT NULL,
  commission NUMERIC(18,2) NOT NULL,
  payment_amount NUMERIC(18,2) NOT NULL,
  closing_balance NUMERIC(18,2) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  UNIQUE(loan_application_id, installment_number)
);

CREATE INDEX IF NOT EXISTS idx_loan_applications_customer ON loan_applications(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_loan_installments_application ON loan_installments(loan_application_id, installment_number);