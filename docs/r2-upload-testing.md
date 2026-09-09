# Cloudflare R2 upload verification

The application has two different checks. Run both against the development bucket; never use the production bucket for a smoke test.

## 1. Infrastructure smoke test

Prerequisites:

- `.env` points to `commercehub-dev`.
- `MEDIA_PUBLIC_BASE_URL` points to the public development image domain.
- The development bucket CORS policy allows `http://localhost:3000` with `PUT`, `GET`, and `HEAD`.

PowerShell:

```powershell
$env:R2_LIVE_TEST_ENABLED = "true"
.\mvnw.cmd "-Dmaven.repo.local=C:\Users\acer\.m2\repository" "-Dtest=R2LiveUploadIT" test
Remove-Item Env:R2_LIVE_TEST_ENABLED
```

The test uploads a real one-pixel PNG through a presigned URL, verifies the browser preflight response, reads the image through both S3 and the public image domain, then deletes it. It refuses to run against `commercehub-prod` and never prints credentials or the signed URL.

## 2. Full application test

1. Start PostgreSQL and apply `V2/schema-v8.sql`, then `V2/seed-test-data.sql` on a disposable local database if test users are missing.
2. Start the backend and frontend.
3. Sign in as `seller_demo` using the password documented at the top of `seed-test-data.sql`.
4. Open `/seller/products/new`, select a JPG, PNG, or WebP image smaller than 2 MB, and create a product.
5. In browser DevTools, confirm the request order: backend `presign`, direct R2 `PUT`, backend `complete`, then product creation.
6. Confirm `products.thumbnail_url` contains an object key beginning with `shops/`, not a full domain URL.

Do not insert a fake R2 object key directly into the database: the database row would reference an object that does not exist.
