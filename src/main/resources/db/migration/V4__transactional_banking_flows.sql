ALTER TABLE transfers ADD COLUMN IF NOT EXISTS customer_id BIGINT REFERENCES users(id);
ALTER TABLE transfers ADD COLUMN IF NOT EXISTS destination_bank_code VARCHAR(20) NOT NULL DEFAULT 'YBANK';
ALTER TABLE transfers ADD COLUMN IF NOT EXISTS operation_id VARCHAR(80) UNIQUE;
ALTER TABLE transfers ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS external_banks (
  code VARCHAR(20) PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  transfer_fee NUMERIC(18,2) NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS yape_contacts (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES users(id),
  phone VARCHAR(20) NOT NULL,
  alias VARCHAR(120) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE(customer_id, phone)
);

CREATE TABLE IF NOT EXISTS yape_payments (
  id BIGSERIAL PRIMARY KEY,
  operation_id VARCHAR(80) NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL REFERENCES users(id),
  origin_account VARCHAR(30) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  amount NUMERIC(18,2) NOT NULL,
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS mobile_operators (
  code VARCHAR(20) PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS mobile_recharges (
  id BIGSERIAL PRIMARY KEY,
  operation_id VARCHAR(80) NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL REFERENCES users(id),
  origin_account VARCHAR(30) NOT NULL,
  operator_code VARCHAR(20) NOT NULL REFERENCES mobile_operators(code),
  phone VARCHAR(20) NOT NULL,
  amount NUMERIC(18,2) NOT NULL,
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO accounts(customer_id, account_number, type, currency, balance, active)
SELECT id, '001-102-00889900', 'CHECKING', 'PEN', 1250.00, TRUE
FROM users WHERE document_number = '77777777'
ON CONFLICT(account_number) DO UPDATE SET
  customer_id = EXCLUDED.customer_id,
  type = EXCLUDED.type,
  currency = EXCLUDED.currency,
  balance = EXCLUDED.balance,
  active = TRUE;

INSERT INTO account_movements(account_id, title, description, amount, currency, direction, category, created_at)
SELECT a.id, 'Apertura cuenta sueldo', 'Cuenta adicional Victor', 1250.00, 'PEN', 'CREDIT', 'OPENING', NOW() - INTERVAL '7 days'
FROM accounts a
JOIN users u ON u.id = a.customer_id
WHERE u.document_number = '77777777'
  AND a.account_number = '001-102-00889900';

INSERT INTO external_banks(code, name, transfer_fee, active) VALUES
('YBANK', 'YBank', 0.00, TRUE),
('BCP', 'Banco de Credito del Peru', 0.00, TRUE),
('INTERBANK', 'Interbank', 0.00, TRUE),
('BBVA', 'BBVA Peru', 0.00, TRUE),
('SCOTIABANK', 'Scotiabank Peru', 0.00, TRUE)
ON CONFLICT(code) DO UPDATE SET
  name = EXCLUDED.name,
  transfer_fee = EXCLUDED.transfer_fee,
  active = TRUE;

INSERT INTO yape_contacts(customer_id, phone, alias, active)
SELECT u.id, x.phone, x.alias, TRUE
FROM users u
CROSS JOIN (
  VALUES
    ('987654321', 'Andrea Salazar'),
    ('912345678', 'Carlos Mendoza'),
    ('999111222', 'Bodega San Isidro')
) AS x(phone, alias)
WHERE u.document_number = '77777777'
ON CONFLICT(customer_id, phone) DO UPDATE SET
  alias = EXCLUDED.alias,
  active = TRUE;

INSERT INTO mobile_operators(code, name, active) VALUES
('CLARO', 'Claro', TRUE),
('MOVISTAR', 'Movistar', TRUE),
('ENTEL', 'Entel', TRUE),
('BITEL', 'Bitel', TRUE)
ON CONFLICT(code) DO UPDATE SET
  name = EXCLUDED.name,
  active = TRUE;