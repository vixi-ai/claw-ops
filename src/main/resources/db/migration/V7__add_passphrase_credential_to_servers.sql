ALTER TABLE servers ADD COLUMN passphrase_credential_id UUID REFERENCES secrets(id) ON DELETE SET NULL;
