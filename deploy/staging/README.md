# CommerceHub staging deployment

This directory contains safe templates only. Real secrets must be configured in
the deployment platform or stored outside the repository under
`/etc/commercehub/staging` with mode `0600`.

## Required public hosts

- Frontend: `https://staging.tienhoclaptrinh.site`
- API: `https://api-staging.tienhoclaptrinh.site`
- R2 media: `https://images-staging.tienhoclaptrinh.site`
- SePay webhook: `https://api-staging.tienhoclaptrinh.site/api/v1/payments/sepay/webhook`

## First deployment

1. Copy `.env.staging.example` to `/etc/commercehub/staging/backend.env` and
   replace every placeholder without committing the resulting file.
2. Copy `postgres.env.example` to `/etc/commercehub/staging/postgres.env` and
   use the same database password as the backend secret store.
3. Set both files to owner `root:root` and permission `0600`.
4. Create the `commercehub-staging` R2 bucket, a bucket-scoped API token, the
   media custom domain, and apply `r2-cors.json`.
5. From the repository root run:

   ```bash
   docker compose -f deploy/staging/docker-compose.staging.yml build --pull backend
   docker compose -f deploy/staging/docker-compose.staging.yml up -d
   docker compose -f deploy/staging/docker-compose.staging.yml ps
   curl --fail http://127.0.0.1:8081/readyz
   ```

6. Install `nginx-api-staging.conf`, issue a valid TLS certificate, and use
   Cloudflare SSL mode Full (strict).
7. Configure the frontend staging variables from its `.env.staging.example`
   and rebuild the frontend.
8. Point the SePay test webhook to the URL above and run the full system UAT.

An empty staging database is migrated automatically by Flyway. Keep
`FLYWAY_BASELINE_ON_MIGRATE=false` and never load `seed-test-data.sql`
automatically.

## Smoke checks

```bash
curl --fail https://api-staging.tienhoclaptrinh.site/livez
curl --fail https://api-staging.tienhoclaptrinh.site/readyz
docker compose -f deploy/staging/docker-compose.staging.yml logs --since=10m backend
```

PostgreSQL and Redis intentionally have no host port mapping. The backend is
bound to `127.0.0.1:8081`, so only Nginx can publish it.
