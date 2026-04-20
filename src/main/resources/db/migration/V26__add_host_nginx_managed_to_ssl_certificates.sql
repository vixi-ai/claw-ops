-- Track whether ClawOps manages the host nginx for each cert. Default TRUE preserves
-- existing behaviour for all pre-existing certs (they were issued by the old flow that
-- always installed + configured host nginx). New certs issued against servers whose :80
-- is already bound by Docker/Traefik/etc. get FALSE and trigger the coexistence path
-- on remove/renew.
ALTER TABLE ssl_certificates
    ADD COLUMN host_nginx_managed BOOLEAN NOT NULL DEFAULT TRUE;
