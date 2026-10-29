# FreshLedger Grocery Billing System

FreshLedger is a Java 21+ Spring Boot application with Thymeleaf screens for
store owners, cashiers, and customers. It uses a persistent H2 file database
for local development and supports PostgreSQL for production.

## Requirements

- JDK 21 or newer
- Windows PowerShell for the included start/stop scripts

The Maven Wrapper selects the JDK configured by `JAVA_HOME`. The managed
PowerShell launcher selects an installed Temurin 25 JDK or falls back to a
supported `JAVA_HOME`.

## Run locally

From the `billing-system` directory:

```powershell
.\start-billing.ps1
```

The launcher checks port 8080, starts the application, and waits for the login
page to respond. It writes logs under `%LOCALAPPDATA%\FreshLedger\logs`. To
stop an instance started by this launcher:

```powershell
.\start-billing.ps1 -Stop
```

Alternatively, start it in the foreground with `.\mvnw.cmd spring-boot:run`
and stop it with Ctrl+C. Open `http://localhost:8080/login`. On a new local
database, configure `APP_AUTH_OWNER_PASSWORD` to create the initial owner.
The optional `APP_AUTH_CASHIER_PASSWORD` and `APP_AUTH_CUSTOMER_PASSWORD`
variables create sample accounts when supplied. Passwords must be 12 to 72
UTF-8 bytes. Existing seeded accounts are never reset or overwritten at
startup, even if these variables are later changed or omitted. The login
screen does not disclose credentials. Use a separate registered account for
real customer data. `.env.example` is a template only: copy its values into
the environment or PowerShell session; Spring does not load `.env` files
automatically.

### Password recovery

The login page sends password-reset links to the email address saved on an
enabled account. Configure SMTP with `MAIL_HOST`, `MAIL_PORT`,
`MAIL_USERNAME`, `MAIL_PASSWORD`, and `MAIL_FROM`, and set
`APP_PUBLIC_BASE_URL` to the URL users use to reach the app. Local development
can use `APP_AUTH_OWNER_EMAIL` and `APP_AUTH_CASHIER_EMAIL` to populate a
missing email address for the seeded owner and cashier; these settings do not
replace an email already on an account. Registered customers use the email
provided during registration. For production, set `APP_BOOTSTRAP_OWNER_EMAIL`
when creating the initial owner. Existing production accounts without an
email need their email address added before they can use self-service recovery.
Reset links expire after 30 minutes and can only be used once.

VS Code tasks use the Maven Wrapper and the project root as their working
directory: run `Maven: Compile (wrapper)`, `Maven: Verify (wrapper)`, or
`Spring Boot: Run (wrapper)` from the Tasks menu.

## Database and product images

The default `dev` profile keeps H2 as the active database so existing local
workflows are unchanged. The H2 database is stored under
`${user.home}/.freshledger/billing` and uploaded product images under
`${user.home}/.freshledger/product-images`. These locations are outside the
project directory. They persist across application restarts. Do not delete
them to troubleshoot a build. Schema changes for local H2 are applied by the
existing `schema.sql`; Hibernate schema generation is disabled.

To run against PostgreSQL, create an empty database, set
`SPRING_PROFILES_ACTIVE=prod`, and provide `DB_URL`, `DB_USERNAME`, and
`DB_PASSWORD`. For example:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/freshledger'
$env:DB_USERNAME = 'freshledger_app'
$env:DB_PASSWORD = '<set a unique secret in your environment>'
$env:APP_BOOTSTRAP_OWNER_PASSWORD = '<set a unique one-time owner password>'
.\mvnw.cmd spring-boot:run
```

The `prod` profile uses PostgreSQL, Flyway migrations, environment-only
credentials, and disables SQL init and Hibernate schema creation. Flyway
creates the schema without dropping tables. On a new production database, the
app requires `APP_BOOTSTRAP_OWNER_PASSWORD` once to create the first owner
when no owner row exists; remove that variable after the first successful
startup. Production does not create default cashier or customer passwords.
Registered users and their BCrypt hashes are stored in the database. Account
password changes are available to authenticated users through
`POST /api/account/password` (JSON fields `currentPassword` and `newPassword`,
with a CSRF token).

For a production deployment, configure the same environment variables in the
platform, use its PostgreSQL connection URL, and let `PORT` set the HTTP port.
Never commit `.env` or real credentials.

### H2-to-PostgreSQL migration

The current workstation has an H2 backup at
`%USERPROFILE%\.freshledger\backups\billing-20261020-181621.mv.db`; its SHA-256
matched the live `.mv.db` at backup time. Keep H2 unchanged until PostgreSQL is
available and the migration checks below pass. Uploaded product image files
are stored outside the database and must also be copied if existing uploads
need to remain available.

1. Stop the billing app and take a fresh copy of the H2 `.mv.db` file.
2. Create a **new, empty** PostgreSQL database and set the `prod` environment
   variables. Flyway creates the compatible schema on first startup.
3. Point `H2_MIGRATION_URL` at the backup file (omit the `.mv.db` suffix), and
   include `;IFEXISTS=TRUE;ACCESS_MODE_DATA=R` in the JDBC URL. Set
   `H2_MIGRATION_USERNAME` and `H2_MIGRATION_PASSWORD` if needed.
4. Set `APP_MIGRATION_H2_TO_POSTGRES_ENABLED=true` and start the app once. The
   importer refuses a non-empty target, copies users/products/transactions/
   line items in dependency order, preserves IDs, advances identity sequences,
   and rolls back on verification failure.
5. It verifies row counts, transaction subtotal/discount/tax/total sums,
   per-product IDs and stock, and customer/cashier/invoice-item relationships.
   Confirm its logged imported counts, then stop the app and set the migration
   flag back to `false` before normal production startup. The source URL is
   required to be read-only, and this process never writes to the H2 file.

This environment has no local PostgreSQL server/client, so an actual import,
PostgreSQL record-count comparison, and live connection test still need to be
performed after PostgreSQL is provisioned. Do not point the production profile
at an existing populated PostgreSQL database for this one-time importer.

## Build and test

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

Stop the application before running `clean`, because Windows may prevent Maven
from deleting build output that is currently in use.

## Implemented workflows and verification limits

- Role-protected owner, cashier, and customer pages; public registration only
  creates customer accounts. Passwords are BCrypt-hashed, CSRF is enabled, and
  role-specific access and login redirects are covered by automated tests.
- Password recovery issues hashed, one-use reset tokens with a 30-minute
  expiry. Automated tests verify the reset flow using a mocked SMTP sender;
  delivery through a real email provider has not been verified.
- Product catalog create, edit, deactivate, restock, image upload/removal,
  low-stock display, and stock-movement history.
- Server-calculated checkout totals, duplicate-line aggregation, stock locking
  against overselling, persisted invoices/orders, and idempotent operation IDs.
- Partial refunds with return reasons, approver audit, duplicate/quantity
  validation, optional restocking, and loyalty-ledger adjustments.
- Customer-specific order, bill, profile, and loyalty feeds. Sales export is
  available as CSV; the owner dashboard currently exposes summary metrics, not
  the full date-filtered profit/expense analytics requested in the project
  brief.
- Voice cart commands use the browser Speech Recognition API where available,
  with typed commands as a fallback. Product-photo handling currently detects
  barcodes in uploaded images on browsers that support `BarcodeDetector`; it
  does not identify products from their visual appearance.

The following requested capabilities are not implemented or not verified:
batch/expiry tracking and expired-stock sale prevention; visual product
recognition; full supplier, purchase-order, expense, employee, and shift
management; live camera scanning; microphone/camera hardware behavior; and
real SMTP delivery. PostgreSQL configuration and migrations are present, but
no live PostgreSQL connection or migration has been tested in this
environment. Do not treat a screen or navigation item alone as evidence that a
workflow is complete.

## GitHub and deployment notes

The `.gitignore` excludes build output, local environment files, databases,
logs, and generated uploads. Configure unique account passwords and database credentials for each
deployment. The default H2 database is intended for local use rather than a
multi-instance production deployment.
