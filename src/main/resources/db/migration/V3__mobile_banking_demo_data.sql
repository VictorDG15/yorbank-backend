CREATE TABLE IF NOT EXISTS customer_profiles (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
  phone VARCHAR(20) NOT NULL,
  address VARCHAR(180) NOT NULL,
  city VARCHAR(80) NOT NULL,
  segment VARCHAR(30) NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS account_movements (
  id BIGSERIAL PRIMARY KEY,
  account_id BIGINT NOT NULL REFERENCES accounts(id),
  title VARCHAR(120) NOT NULL,
  description VARCHAR(180),
  amount NUMERIC(18,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  direction VARCHAR(20) NOT NULL CHECK (direction IN ('CREDIT','DEBIT')),
  category VARCHAR(40) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_account_movements_account_date ON account_movements(account_id, created_at DESC);

CREATE TABLE IF NOT EXISTS beneficiaries (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES users(id),
  alias VARCHAR(120) NOT NULL,
  bank_name VARCHAR(80) NOT NULL,
  account_number VARCHAR(30) NOT NULL,
  document_number VARCHAR(20) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  UNIQUE(customer_id, account_number)
);

CREATE TABLE IF NOT EXISTS notifications (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES users(id),
  title VARCHAR(120) NOT NULL,
  body VARCHAR(240) NOT NULL,
  read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS service_bills (
  id BIGSERIAL PRIMARY KEY,
  service_code VARCHAR(30) NOT NULL UNIQUE,
  provider VARCHAR(80) NOT NULL,
  title VARCHAR(120) NOT NULL,
  category VARCHAR(40) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS bill_payments (
  id BIGSERIAL PRIMARY KEY,
  operation_id VARCHAR(80) NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL REFERENCES users(id),
  service_code VARCHAR(30) NOT NULL REFERENCES service_bills(service_code),
  account_number VARCHAR(30) NOT NULL,
  amount NUMERIC(18,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(30) NOT NULL,
  paid_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS loan_products (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE,
  annual_rate NUMERIC(5,2) NOT NULL,
  min_amount NUMERIC(18,2) NOT NULL,
  max_amount NUMERIC(18,2) NOT NULL,
  min_months INTEGER NOT NULL,
  max_months INTEGER NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO customer_profiles(customer_id, phone, address, city, segment)
SELECT id, '999888777', 'Av. Javier Prado 123', 'Lima', 'ORO'
FROM users WHERE document_number = '77777777'
ON CONFLICT(customer_id) DO UPDATE SET
  phone = EXCLUDED.phone,
  address = EXCLUDED.address,
  city = EXCLUDED.city,
  segment = EXCLUDED.segment,
  updated_at = NOW();

INSERT INTO account_movements(account_id, title, description, amount, currency, direction, category, created_at)
SELECT a.id, x.title, x.description, x.amount, x.currency, x.direction, x.category, x.created_at
FROM accounts a
JOIN users u ON u.id = a.customer_id
CROSS JOIN (
  VALUES
    ('Deposito de sueldo', 'Empresa ACME SAC', 3200.00, 'PEN', 'CREDIT', 'PAYROLL', NOW() - INTERVAL '2 days'),
    ('Yape recibido', 'Carlos Mendoza', 250.00, 'PEN', 'CREDIT', 'YAPE', NOW() - INTERVAL '1 day 4 hours'),
    ('Pago servicio luz', 'Luz del Sur', -84.20, 'PEN', 'DEBIT', 'SERVICES', NOW() - INTERVAL '22 hours'),
    ('Pago con QR', 'Tambo - San Isidro', -18.90, 'PEN', 'DEBIT', 'QR', NOW() - INTERVAL '12 hours'),
    ('Transferencia enviada', 'Andrea Salazar', -120.00, 'PEN', 'DEBIT', 'TRANSFER', NOW() - INTERVAL '6 hours'),
    ('Compra online', 'Marketplace Peru', -129.90, 'PEN', 'DEBIT', 'CARD', NOW() - INTERVAL '2 hours')
) AS x(title, description, amount, currency, direction, category, created_at)
WHERE u.document_number = '77777777'
  AND a.account_number = '001-101-00712345';

INSERT INTO beneficiaries(customer_id, alias, bank_name, account_number, document_number, active)
SELECT u.id, x.alias, x.bank_name, x.account_number, x.document_number, TRUE
FROM users u
CROSS JOIN (
  VALUES
    ('Andrea Salazar', 'YBank', '001-101-00990011', '45678912'),
    ('Carlos Mendoza', 'BCP', '191-240-55553331', '40771243'),
    ('Empresa ACME SAC', 'Interbank', '003-222-88004512', '20555888441')
) AS x(alias, bank_name, account_number, document_number)
WHERE u.document_number = '77777777'
ON CONFLICT(customer_id, account_number) DO UPDATE SET
  alias = EXCLUDED.alias,
  bank_name = EXCLUDED.bank_name,
  document_number = EXCLUDED.document_number,
  active = TRUE;

INSERT INTO notifications(customer_id, title, body, read, created_at)
SELECT u.id, x.title, x.body, x.read, x.created_at
FROM users u
CROSS JOIN (
  VALUES
    ('Compra segura', 'Activa o bloquea tu tarjeta desde la app cuando lo necesites.', FALSE, NOW() - INTERVAL '3 hours'),
    ('Pago recibido', 'Tu deposito de sueldo ya esta disponible.', FALSE, NOW() - INTERVAL '2 days'),
    ('Tip de seguridad', 'Nunca compartas tu clave de internet de 6 digitos.', TRUE, NOW() - INTERVAL '5 days')
) AS x(title, body, read, created_at)
WHERE u.document_number = '77777777';

INSERT INTO service_bills(service_code, provider, title, category, active) VALUES
('LUZ-001', 'Luz del Sur', 'Recibo de luz', 'Electricidad', TRUE),
('AGUA-002', 'Sedapal', 'Servicio de agua', 'Agua', TRUE),
('TEL-003', 'Movistar', 'Movistar Hogar', 'Telefonia', TRUE),
('EDU-004', 'Universidad Peru', 'Pension mensual', 'Educacion', TRUE)
ON CONFLICT(service_code) DO UPDATE SET
  provider = EXCLUDED.provider,
  title = EXCLUDED.title,
  category = EXCLUDED.category,
  active = TRUE;

INSERT INTO loan_products(name, annual_rate, min_amount, max_amount, min_months, max_months, active) VALUES
('Prestamo Personal Oro', 18.50, 1000.00, 50000.00, 6, 60, TRUE),
('Credito Emprendedor', 16.90, 5000.00, 120000.00, 12, 72, TRUE),
('Compra de deuda', 14.90, 3000.00, 80000.00, 12, 48, TRUE)
ON CONFLICT(name) DO UPDATE SET
  annual_rate = EXCLUDED.annual_rate,
  min_amount = EXCLUDED.min_amount,
  max_amount = EXCLUDED.max_amount,
  min_months = EXCLUDED.min_months,
  max_months = EXCLUDED.max_months,
  active = TRUE;