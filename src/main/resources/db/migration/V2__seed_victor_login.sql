CREATE TABLE IF NOT EXISTS user_cards (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES users(id),
  card_number VARCHAR(16) NOT NULL UNIQUE,
  type VARCHAR(30) NOT NULL,
  brand VARCHAR(30) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO users(document_number,email,full_name,password_hash,role,two_factor_enabled,active) VALUES
('77777777','victor@ybank.pe','Victor Ramos Quispe','{plain}123456','CUSTOMER',false,true)
ON CONFLICT(document_number) DO UPDATE SET
  email = EXCLUDED.email,
  full_name = EXCLUDED.full_name,
  password_hash = EXCLUDED.password_hash,
  role = EXCLUDED.role,
  two_factor_enabled = false,
  active = true;

INSERT INTO accounts(customer_id,account_number,type,currency,balance,active)
SELECT id,'001-101-00712345','SAVINGS','PEN',3500.00,true
FROM users WHERE document_number = '77777777'
ON CONFLICT(account_number) DO NOTHING;

INSERT INTO user_cards(customer_id,card_number,type,brand,active)
SELECT id,'4555555555555555','DEBIT','VISA',true
FROM users WHERE document_number = '77777777'
ON CONFLICT(card_number) DO UPDATE SET
  customer_id = EXCLUDED.customer_id,
  type = EXCLUDED.type,
  brand = EXCLUDED.brand,
  active = true;