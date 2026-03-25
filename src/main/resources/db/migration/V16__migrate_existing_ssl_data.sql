-- Link existing ssl_certificates to their domain assignments (if not already linked)
UPDATE ssl_certificates sc
SET assignment_id = (
    SELECT da.id FROM domain_assignments da
    WHERE da.resource_id = sc.server_id
      AND da.status != 'RELEASED'
    ORDER BY da.created_at DESC
    LIMIT 1
)
WHERE sc.assignment_id IS NULL
  AND sc.server_id IS NOT NULL;
