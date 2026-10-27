
## Isolated five-portal E2E run — 2026-10-27

- **Run ID:** PNS-E2E-20261027-01
- **Scope:** E2E-01 through E2E-05 and DP-01 through DP-40. Repository inspection and isolated automated checks only; no authenticated browser workflow was performed.
- **Database safety:** The configured runtime uses the existing local MySQL instance and service schemas. The dedicated isolated MySQL endpoint required by `run-all-services.ps1 -IsolatedTest` (`127.0.0.1:14306`) was unavailable. Its expected server UUID and isolated schema set therefore could not be verified. The launcher also moves backend services to a separate port range, which is incompatible with this requested no-port-change run. No service was started for this run, no portal write was made, and no existing database was modified.
- **Authentication prerequisite:** The most recent verified local cleanup left only `storemanagergachibowli`; no authorized Super Admin account remains for Phase 1. No privileged account or session was manufactured.
- **Notification/payment boundary:** No local OTP sink or sandbox payment integration was selected and verified. No OTP, email, SMS, carrier request, or live payment was triggered.

### Cross-role case matrix

| Test ID | Status | Browser/API evidence | Blocker |
|---|---|---|---|
| E2E-01 | BLOCKED | No browser steps or business API mutations executed. | No isolated database/browser stack; no disposable customer, store-manager, or delivery-partner records. |
| E2E-02 | BLOCKED | No browser steps or business API mutations executed. | No authorized Super Admin login and no isolated database. |
| E2E-03 | BLOCKED | No browser steps or business API mutations executed. | No isolated database or disposable customer/store/product/inventory records. |
| E2E-04 | BLOCKED | No browser steps or business API mutations executed. | No isolated database, eligible global order, or sandbox shipping integration. |
| E2E-05 | BLOCKED | No browser/API authorization probes executed in this run. | No isolated stack and no complete set of disposable role accounts. |

No website-created locations, users, products, stores, inventory, delivery partners, customers, orders, or shipments were created in this run. No browser screenshots or live API evidence are claimed.

### Automated regression checks (separate from browser E2E)

| Suite | Result |
|---|---|
| Frontend `node --test` | PASS — 256 tests, 0 failures. |
| Frontend `npm.cmd run lint` | PASS. |
| Frontend `npm.cmd run build` | PASS; Vite reported the existing large-chunk advisory. |
| Catalog Service Maven suite | PASS after rerun — 41 tests, 0 failures. The first run exposed a stale test fixture that used a recognized city while asserting it was unmapped. The test now uses `Unmapped Region`; the ambiguous-mapping assertion remains. |
| Org Access Maven suite | PASS — 77 tests, 0 failures. |
| Inventory Service Maven suite | PASS — 19 tests, 0 failures. |
| Order Service Maven suite | PASS — 109 tests, 0 failures. |
| Fulfillment Service Maven suite | PASS — 32 tests, 0 failures. |
| Cart Service Maven suite | PASS — 12 tests, 0 failures. |
| Customer Profile Service Maven suite | PASS — 9 tests, 0 failures. |
| Payment Service Maven suite | PASS — 13 tests, 0 failures. |
| Notification Service Maven suite | PASS — 15 tests, 0 failures. |
| `git diff --check` in both repositories | PASS. |

**Automated totals:** 256 frontend tests PASS; 327 backend tests PASS. These checks use unit/in-memory test configuration and do not count as portal E2E evidence.

**Browser E2E totals for this run:** PASS 0, FAIL 0, BLOCKED 5, NOT RUN 0. No defect was confirmed by browser execution. The Catalog test fixture correction is recorded above; no application source code was changed for this run.

**Next safe prerequisites:** provide an isolated disposable MySQL instance with a verified server UUID and reviewed schema provisioning, an authorized Super Admin test account in that isolated environment, a local OTP sink, and sandbox-only payment/shipping configuration. Then the five portal workflows can be executed without touching the configured shared MySQL server.

## Cross-Role workflow test attempt — 2026-10-27

- **Run ID:** PNS-E2E-20261027-02
- **Test request:** User supplied the five-row Cross-Role End-to-End Workflows matrix and requested execution.
- **Preflight evidence:** Read-only TCP checks found the isolated MySQL endpoint `127.0.0.1:14306` closed and `.local/isolated-test-14306` absent. Standard local frontend, gateway, catalog, and Org Access ports were open, confirming those are the shared runtime. A read-only role count in the configured `users_roles` schema returned 1 application user, 0 Super Admins, 0 Global Admins, and 1 Store Manager.
- **Safety result:** No sign-in attempt or business write was made. Without an isolated DB, ordinary signup, store, product, stock, order, payment, shipment, or OTP actions would mutate the shared runtime. No screenshots/API mutations are claimed.

| ID | Status | Issues / evidence |
|---|---|---|
| E2E-01 | BLOCKED | No isolated DB or disposable customer/order/partner fixtures; the active runtime is shared, so the COD checkout and delivery workflow was not started. |
| E2E-02 | BLOCKED | Read-only account check found 0 Super Admins and 0 Global Admins; no authorized admin account is available, and isolated DB is unavailable. |
| E2E-03 | BLOCKED | No isolated DB; configuring zones and submitting checkout would write to the shared runtime. |
| E2E-04 | BLOCKED | No isolated DB or Global Admin account; no disposable global order or sandbox shipping flow is available. |
| E2E-05 | BLOCKED | No isolated DB and no Super Admin/Global Admin accounts for authorized staff provisioning and role checks. |

**Current run totals:** PASS 0, FAIL 0, BLOCKED 5, NOT RUN 0. No test case was marked Passed based on documentation or unit tests. No new product, store, stock, customer, order, shipment, or staff record was created.

**Required prerequisites:** Start a separate disposable MySQL instance with a verified identity and isolated service configuration, and provision a legitimate Super Admin test account in that environment through the approved setup flow. Configure a local OTP sink and payment/shipping sandbox before attempting E2E-01 or E2E-04. Re-run this matrix only after those checks pass.
