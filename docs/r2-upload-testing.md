# Cloudflare R2 upload verification

The application has two different checks. Run both against the development bucket; never use the production bucket for a smoke test.

## 1. Infrastructure smoke test

Prerequisites:

- `.env` points to `commercehub-dev`.
- `MEDIA_PUBLIC_BASE_URL` points to the public development image domain.
- The development bucket CORS policy allows `http://localhost:3000` with `PUT`, `GET`, and `HEAD`.
- `AllowedHeaders` contains both `Content-Type` and `Cache-Control`; the latter is part of the PUT signature.

Development CORS example:

```json
[
  {
    "AllowedOrigins": ["http://localhost:3000", "http://127.0.0.1:3000"],
    "AllowedMethods": ["GET", "HEAD", "PUT"],
    "AllowedHeaders": ["Content-Type", "Cache-Control"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

PowerShell:

```powershell
$env:R2_LIVE_TEST_ENABLED = "true"
.\mvnw.cmd "-Dmaven.repo.local=C:\Users\acer\.m2\repository" "-Dtest=R2LiveUploadIT" test
Remove-Item Env:R2_LIVE_TEST_ENABLED
```

The test uploads a small infrastructure fixture through a presigned URL with the immutable cache header, verifies the browser preflight response, reads the object through both S3 and the public image domain, then deletes it. It refuses to run against `commercehub-prod` and never prints credentials or the signed URL.

## 2. Full application test

1. Start PostgreSQL and apply `V2/schema-v8.sql`, then `V2/seed-test-data.sql` on a disposable local database if test users are missing.
2. Start the backend and frontend.
3. Sign in as `seller_demo` using the password documented at the top of `seed-test-data.sql`.
4. Open `/seller/products/new`, select a JPG, PNG, or WebP image smaller than 2 MB whose centered 4:3 crop is at least 800 x 600 px, and create a product.
5. In browser DevTools, confirm the request order: backend `presign`, direct R2 `PUT`, backend `complete`, then product creation.
6. Confirm `products.thumbnail_url` contains an object key beginning with `shops/`, not a full domain URL.
7. Download the stored object and confirm it is WebP, exactly 1200 x 900 px, and has `Cache-Control: public, max-age=31536000, immutable`.

The browser center-crops the source to 4:3, resizes it to 1200 x 900, re-encodes it around 84% WebP quality, and removes EXIF/XMP before requesting the upload URL. The backend independently rejects objects that are not static WebP, are not 1200 x 900, exceed 2 MB, or still advertise EXIF/XMP metadata.

## 3. Rate-limit deployment mode

- One local backend: keep `R2_RATE_LIMIT_STORE=memory`; Redis is not contacted.
- Multiple production backend instances: set `R2_RATE_LIMIT_STORE=redis` and provide a TLS `REDIS_URL` through deployment secrets.
- The Redis implementation uses one atomic Lua script and a shared rolling 15-minute window. It fails closed with HTTP 503 if Redis is unavailable instead of allowing unmetered presigned URLs.

Never put the Redis password in a tracked `.env.example` value or in any `NEXT_PUBLIC_*` frontend variable.

Do not insert a fake R2 object key directly into the database: the database row would reference an object that does not exist.
