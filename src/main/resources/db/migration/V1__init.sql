CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  document_number VARCHAR(20) NOT NULL UNIQUE,
  email VARCHAR(120) NOT NULL UNIQUE,
  full_name VARCHAR(160) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(30) NOT NULL,
  two_factor_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE TABLE accounts (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  account_number VARCHAR(30) NOT NULL UNIQUE,
  type VARCHAR(30) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  balance NUMERIC(18,2) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE TABLE transfers (
  id BIGSERIAL PRIMARY KEY,
  origin_account VARCHAR(30) NOT NULL,
  destination_account VARCHAR(30) NOT NULL,
  amount NUMERIC(18,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(40) NOT NULL,
  description VARCHAR(180),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
INSERT INTO users(document_number,email,full_name,password_hash,role,two_factor_enabled,active) VALUES
('70000001','demo@ybank.pe','Demo Customer','$2a$12$B653nTDG86UYf50aAC8y2./1T5O8p4TMqRwDzO8D9l3Dg4pNeMe8u','CUSTOMER',true,true);
INSERT INTO accounts(customer_id,account_number,type,currency,balance,active) VALUES
(1,'001-101-00045821','SAVINGS','PEN',8420.50,true),
(1,'001-102-00090214','CHECKING','USD',1280.20,true);
