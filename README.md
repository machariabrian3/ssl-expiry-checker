# SSL Expiry Checker

Production-ready Spring Boot service that performs a real TLS handshake with SNI, reads the leaf X.509 certificate, and returns its expiry details.

Author: Brian Mwai

**API**
`GET /api/v1/ssl/expiry?host=erp.bometwater.co.ke&port=443`
Optional:
`POST /api/v1/ssl/expiry` with JSON body `{ "host": "erp.bometwater.co.ke", "port": 443 }`

Bulk:
`POST /api/v1/ssl/expiry/bulk` with JSON array body (see example below)
If a domain returns a known interception certificate (e.g., Fortinet blocked page), the service retries using `client_ip` when provided,
otherwise it resolves DNS and tries the resolved IPs.

Health:
`GET /actuator/health`

**Response Schema**
- `host`: string
- `port`: integer
- `expiresAt`: ISO-8601 timestamp (UTC)
- `daysRemaining`: integer
- `status`: `OK | EXPIRING | EXPIRED | ERROR`
- `errorMessage`: string (only when `status=ERROR`)
- `checkedAt`: ISO-8601 timestamp (UTC)
- `chainTrusted`: boolean (true when default trust succeeded, false when a trust-all fallback was required)

**Days Remaining Rules**
- If the certificate is expired, `daysRemaining = 0` and `status = EXPIRED`.
- Otherwise, `daysRemaining = ceil(remaining_hours / 24.0)`.
- If `daysRemaining <= SSL_EXPIRING_DAYS`, then `status = EXPIRING`.

**Configuration**
Environment variables:
- `SSL_CONNECT_TIMEOUT_MS` (default `5000`)
- `SSL_READ_TIMEOUT_MS` (default `7000`)
- `SSL_EXPIRING_DAYS` (default `7`)
- `SSL_BULK_CONCURRENCY` (default `16`)
- `SSL_BULK_TIMEOUT_MS` (default `180000`)

Bulk constraints:
- Maximum 300 items per bulk request.

## Run Locally
```bash
./mvnw spring-boot:run
```

Example:
```bash
curl "http://localhost:8011/api/v1/ssl/expiry?host=erp.bometwater.co.ke"
```

Bulk example:
```bash
curl -X POST "http://localhost:8011/api/v1/ssl/expiry/bulk" \
  -H "Content-Type: application/json" \
  -d '[
    {
      "client_name": "BOMET WATER AND SANITATION COMPANY",
      "client_ip": "102.217.125.27",
      "client_domain": "erp.bometwater.co.ke"
    },
    {
      "client_name": "CHEmUSUSU WATER COMPANY",
      "client_ip": "102.217.125.172",
      "client_domain": "erp.chewasco.co.ke"
    }
  ]'
```

Sample response:
```json
{
  "host": "test.****.co.ke",
  "port": 443,
  "expiresAt": "2026-03-01T12:34:56Z",
  "daysRemaining": 24,
  "status": "OK",
  "errorMessage": null,
  "checkedAt": "2026-02-05T10:30:00Z",
  "chainTrusted": true
}
```

## Docker
Build and run:
```bash
docker build -t ssl-expiry-checker .
docker run --rm -p 8011:8011 ssl-expiry-checker
```

## Docker Compose
```bash
docker compose up --build
```

## Deployment Script
```bash
chmod +x deploy.sh
./deploy.sh
```

## License
Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0). See `LICENSE`.
