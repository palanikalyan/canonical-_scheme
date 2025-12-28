DROP TABLE IF EXISTS canonical_trades CASCADE;
DROP TABLE IF EXISTS client CASCADE;
DROP TABLE IF EXISTS fund CASCADE;
DROP TABLE IF EXISTS firms CASCADE;
CREATE TABLE IF NOT EXISTS client
(
    client_id INTEGER PRIMARY KEY,
    kyc_status VARCHAR(20) NOT NULL,  
    pan_number VARCHAR(20),
    status VARCHAR(20) NOT NULL,      
    type VARCHAR(20)                  
);
CREATE INDEX IF NOT EXISTS idx_clients_kyc_status ON client(kyc_status);
CREATE INDEX IF NOT EXISTS idx_clients_status ON client(status);
CREATE TABLE IF NOT EXISTS fund
(
    fund_id INTEGER PRIMARY KEY,
    scheme_code VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,      
    max_limit NUMERIC(18,2),
    min_limit NUMERIC(18,2)
);
CREATE INDEX IF NOT EXISTS idx_funds_scheme_code ON fund(scheme_code);
CREATE INDEX IF NOT EXISTS idx_funds_status ON fund(status);
CREATE TABLE IF NOT EXISTS canonical_trades
(
    id UUID PRIMARY KEY,
    status VARCHAR(50),                      
    created_at TIMESTAMP,
    originator_type INTEGER,
    firm_number INTEGER,
    fund_number INTEGER,
    transaction_type VARCHAR(10),            
    transaction_id VARCHAR(50),
    raw_order_id UUID,                       -- For MQ: from ingestion; For S3: generated per trade
    file_id UUID,                            -- For MQ: null; For S3: from ingestion (batch ID)
    order_source VARCHAR(10),                -- MQ or S3
    trade_datetime TIMESTAMP,
    dollar_amount NUMERIC(15,2),
    client_account_no INTEGER,
    client_name VARCHAR(100),
    ssn VARCHAR(20),
    dob DATE,
    share_quantity NUMERIC(15,2),
    validation_errors TEXT,                  
    validated_at TIMESTAMP,
    request_id VARCHAR(100),
    CONSTRAINT uk_raw_order_transaction UNIQUE (raw_order_id, transaction_id)
);
CREATE INDEX IF NOT EXISTS idx_transaction_id ON canonical_trades(transaction_id);
CREATE INDEX IF NOT EXISTS idx_trade_datetime ON canonical_trades(trade_datetime);
CREATE INDEX IF NOT EXISTS idx_client_account ON canonical_trades(client_account_no);
CREATE INDEX IF NOT EXISTS idx_fund_number ON canonical_trades(fund_number);
CREATE INDEX IF NOT EXISTS idx_status ON canonical_trades(status);
CREATE INDEX IF NOT EXISTS idx_request_id ON canonical_trades(request_id);
CREATE INDEX IF NOT EXISTS idx_raw_order_id ON canonical_trades(raw_order_id);
CREATE INDEX IF NOT EXISTS idx_file_id ON canonical_trades(file_id);
CREATE INDEX IF NOT EXISTS idx_order_source ON canonical_trades(order_source);

CREATE TABLE IF NOT EXISTS firms
(
    firm_number INTEGER PRIMARY KEY,
    firm_name VARCHAR(200)
);

INSERT INTO firms (firm_number, firm_name) VALUES
(1, 'Firm 1'),
(2, 'Firm 2'),
(3, 'Firm 3'),
(4, 'Firm 4'),
(5, 'Firm 5'),
(6, 'Firm 6'),
(7, 'Firm 7'),
(8, 'Firm 8'),
(9, 'Firm 9'),
(10, 'Firm 10'),
(11, 'Firm 11'),
(12, 'Firm 12'),
(13, 'Firm 13'),
(14, 'Firm 14'),
(15, 'Firm 15'),
(16, 'Firm 16'),
(17, 'Firm 17'),
(18, 'Firm 18'),
(19, 'Firm 19'),
(20, 'Firm 20');

INSERT INTO client (client_id, kyc_status, pan_number, status, type) VALUES
(1,  'YES', 'ABCDE1234F', 'ACTIVE',    'INDIVIDUAL'),
(2,  'NO',  'PQRSX5678L', 'ACTIVE',    'CORPORATE'),
(3,  'YES', 'LMNOP3456Q', 'BLOCKED',   'INDIVIDUAL'),
(4,  'YES', 'QWERT6789A', 'ACTIVE',    'NRI'),
(5,  'NO',  'ZXCVB1122T', 'INACTIVE',  'INDIVIDUAL'),
(6,  'YES', 'ASDFG3344Y', 'ACTIVE',    'CORPORATE'),
(7,  'YES', 'HJKLP5566Z', 'ACTIVE',    'INDIVIDUAL'),
(8,  'NO',  'TYUIO7788M', 'BLOCKED',   'NRI'),
(9,  'YES', 'GHJKL8899N', 'ACTIVE',    'INDIVIDUAL'),
(10, 'YES', 'BNMJK9900P', 'INACTIVE',  'CORPORATE'),
(11, 'NO',  'ACDFG1212A', 'ACTIVE',    'INDIVIDUAL'),
(12, 'YES', 'PLMOK3434R', 'ACTIVE',    'NRI'),
(13, 'YES', 'OIUYT5656S', 'BLOCKED',   'CORPORATE'),
(14, 'NO',  'LKHGF7878D', 'INACTIVE',  'INDIVIDUAL'),
(15, 'YES', 'QAZWS9090E', 'ACTIVE',    'INDIVIDUAL'),
(16, 'YES', 'WSXED2323F', 'ACTIVE',    'CORPORATE'),
(17, 'NO',  'RFVTC4545G', 'INACTIVE',  'NRI'),
(18, 'YES', 'TGBNH6767H', 'ACTIVE',    'INDIVIDUAL'),
(19, 'YES', 'YHNJM8989J', 'ACTIVE',    'CORPORATE'),
(20, 'NO',  'UJMIK0101K', 'BLOCKED',   'INDIVIDUAL');
INSERT INTO fund (fund_id, scheme_code, status, max_limit, min_limit) VALUES
(1,  'SCH001', 'ACTIVE',   1000000, 1000),
(2,  'SCH002', 'ACTIVE',   500000,  500),
(3,  'SCH003', 'SUSPENDED',750000,  2000),
(4,  'SCH004', 'ACTIVE',   2000000, 5000),
(5,  'SCH005', 'CLOSED',   300000,  1000),
(6,  'SCH006', 'ACTIVE',   1500000, 2500),
(7,  'SCH007', 'ACTIVE',   900000,  1500),
(8,  'SCH008', 'SUSPENDED',400000,  1000),
(9,  'SCH009', 'ACTIVE',   1200000, 3000),
(10, 'SCH010', 'ACTIVE',   800000,  1200),
(11, 'SCH011', 'CLOSED',   600000,  900),
(12, 'SCH012', 'ACTIVE',   2500000, 5000),
(13, 'SCH013', 'ACTIVE',   1000000, 2000),
(14, 'SCH014', 'SUSPENDED',450000,  1500),
(15, 'SCH015', 'ACTIVE',   1700000, 3500),
(16, 'SCH016', 'ACTIVE',   1100000, 1800),
(17, 'SCH017', 'CLOSED',   700000,  2500),
(18, 'SCH018', 'ACTIVE',   1900000, 4000),
(19, 'SCH019', 'ACTIVE',   950000,  1200),
(20, 'SCH020', 'ACTIVE',   2200000, 6000);

-- Create scheme table (one-to-many: fund -> scheme) and seed three schemes per fund (LOW/MID/HIGH)
CREATE TABLE IF NOT EXISTS scheme (
    fund_id    INTEGER NOT NULL,
    scheme_id  INTEGER NOT NULL,
    scheme_code VARCHAR(50) NOT NULL,
    scheme_name VARCHAR(50) NOT NULL,
    status     VARCHAR(20) NOT NULL,
    PRIMARY KEY (fund_id, scheme_id),
    CONSTRAINT fk_scheme_fund FOREIGN KEY (fund_id) REFERENCES fund(fund_id)
);

CREATE INDEX IF NOT EXISTS idx_scheme_fund ON scheme(fund_id);
CREATE INDEX IF NOT EXISTS idx_scheme_status ON scheme(status);

-- Add scheme_id to canonical_trades and add composite FK to scheme
ALTER TABLE canonical_trades
    ADD COLUMN IF NOT EXISTS scheme_id INTEGER;

CREATE INDEX IF NOT EXISTS idx_scheme_id ON canonical_trades(scheme_id);

ALTER TABLE canonical_trades
    ADD CONSTRAINT fk_trade_fund_scheme
    FOREIGN KEY (fund_number, scheme_id)
    REFERENCES scheme(fund_id, scheme_id);

-- Seed schemes (LOW=1, MID=2, HIGH=3) for each fund 1..20 (status mirrors fund status)
INSERT INTO scheme (fund_id, scheme_id, scheme_code, scheme_name, status) VALUES
(1, 1, 'SCH001-L', 'LOW',    'ACTIVE'),
(1, 2, 'SCH001-M', 'MID',    'ACTIVE'),
(1, 3, 'SCH001-H', 'HIGH',   'ACTIVE'),
(2, 1, 'SCH002-L', 'LOW',    'ACTIVE'),
(2, 2, 'SCH002-M', 'MID',    'ACTIVE'),
(2, 3, 'SCH002-H', 'HIGH',   'ACTIVE'),
(3, 1, 'SCH003-L', 'LOW',    'SUSPENDED'),
(3, 2, 'SCH003-M', 'MID',    'SUSPENDED'),
(3, 3, 'SCH003-H', 'HIGH',   'SUSPENDED'),
(4, 1, 'SCH004-L', 'LOW',    'ACTIVE'),
(4, 2, 'SCH004-M', 'MID',    'ACTIVE'),
(4, 3, 'SCH004-H', 'HIGH',   'ACTIVE'),
(5, 1, 'SCH005-L', 'LOW',    'CLOSED'),
(5, 2, 'SCH005-M', 'MID',    'CLOSED'),
(5, 3, 'SCH005-H', 'HIGH',   'CLOSED'),
(6, 1, 'SCH006-L', 'LOW',    'ACTIVE'),
(6, 2, 'SCH006-M', 'MID',    'ACTIVE'),
(6, 3, 'SCH006-H', 'HIGH',   'ACTIVE'),
(7, 1, 'SCH007-L', 'LOW',    'ACTIVE'),
(7, 2, 'SCH007-M', 'MID',    'ACTIVE'),
(7, 3, 'SCH007-H', 'HIGH',   'ACTIVE'),
(8, 1, 'SCH008-L', 'LOW',    'SUSPENDED'),
(8, 2, 'SCH008-M', 'MID',    'SUSPENDED'),
(8, 3, 'SCH008-H', 'HIGH',   'SUSPENDED'),
(9, 1, 'SCH009-L', 'LOW',    'ACTIVE'),
(9, 2, 'SCH009-M', 'MID',    'ACTIVE'),
(9, 3, 'SCH009-H', 'HIGH',   'ACTIVE'),
(10,1, 'SCH010-L', 'LOW',    'ACTIVE'),
(10,2, 'SCH010-M', 'MID',    'ACTIVE'),
(10,3, 'SCH010-H', 'HIGH',   'ACTIVE'),
(11,1, 'SCH011-L', 'LOW',    'CLOSED'),
(11,2, 'SCH011-M', 'MID',    'CLOSED'),
(11,3, 'SCH011-H', 'HIGH',   'CLOSED'),
(12,1, 'SCH012-L', 'LOW',    'ACTIVE'),
(12,2, 'SCH012-M', 'MID',    'ACTIVE'),
(12,3, 'SCH012-H', 'HIGH',   'ACTIVE'),
(13,1, 'SCH013-L', 'LOW',    'ACTIVE'),
(13,2, 'SCH013-M', 'MID',    'ACTIVE'),
(13,3, 'SCH013-H', 'HIGH',   'ACTIVE'),
(14,1, 'SCH014-L', 'LOW',    'SUSPENDED'),
(14,2, 'SCH014-M', 'MID',    'SUSPENDED'),
(14,3, 'SCH014-H', 'HIGH',   'SUSPENDED'),
(15,1, 'SCH015-L', 'LOW',    'ACTIVE'),
(15,2, 'SCH015-M', 'MID',    'ACTIVE'),
(15,3, 'SCH015-H', 'HIGH',   'ACTIVE'),
(16,1, 'SCH016-L', 'LOW',    'ACTIVE'),
(16,2, 'SCH016-M', 'MID',    'ACTIVE'),
(16,3, 'SCH016-H', 'HIGH',   'ACTIVE'),
(17,1, 'SCH017-L', 'LOW',    'CLOSED'),
(17,2, 'SCH017-M', 'MID',    'CLOSED'),
(17,3, 'SCH017-H', 'HIGH',   'CLOSED'),
(18,1, 'SCH018-L', 'LOW',    'ACTIVE'),
(18,2, 'SCH018-M', 'MID',    'ACTIVE'),
(18,3, 'SCH018-H', 'HIGH',   'ACTIVE'),
(19,1, 'SCH019-L', 'LOW',    'ACTIVE'),
(19,2, 'SCH019-M', 'MID',    'ACTIVE'),
(19,3, 'SCH019-H', 'HIGH',   'ACTIVE'),
(20,1, 'SCH020-L', 'LOW',    'ACTIVE'),
(20,2, 'SCH020-M', 'MID',    'ACTIVE'),
(20,3, 'SCH020-H', 'HIGH',   'ACTIVE');

-- Backfill existing canonical_trades to assign a scheme_id when null
-- Use deterministic choice: scheme_id = ((fund_number - 1) % 3) + 1
UPDATE canonical_trades
SET scheme_id = ((fund_number - 1) % 3) + 1
WHERE scheme_id IS NULL AND fund_number IS NOT NULL;

-- Enforce that every trade must have a scheme_id (safe after backfill)
ALTER TABLE canonical_trades
    ALTER COLUMN scheme_id SET NOT NULL;

-- Add a CHECK to ensure scheme_id values are within expected set (1..3)
ALTER TABLE scheme
    ADD CONSTRAINT chk_scheme_id_valid CHECK (scheme_id IN (1,2,3));

-- Recreate FK constraint to be explicit (ON DELETE RESTRICT, ON UPDATE CASCADE)
ALTER TABLE canonical_trades DROP CONSTRAINT IF EXISTS fk_trade_fund_scheme;
ALTER TABLE canonical_trades
    ADD CONSTRAINT fk_trade_fund_scheme
    FOREIGN KEY (fund_number, scheme_id)
    REFERENCES scheme(fund_id, scheme_id)
    ON DELETE RESTRICT
    ON UPDATE CASCADE;
