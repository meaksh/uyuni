ALTER TABLE suseProductSCCRepository ADD COLUMN IF NOT EXISTS gpg_key_url  VARCHAR(256);
ALTER TABLE suseProductSCCRepository ADD COLUMN IF NOT EXISTS gpg_key_id   VARCHAR(14);
ALTER TABLE suseProductSCCRepository ADD COLUMN IF NOT EXISTS gpg_key_fp   VARCHAR(50);
