# Global Store and Local Store Workflow

This document defines the intended boundary between the India-wide Global Store and independent local stores. The E2E workflows below still require execution before they can be marked passed.

## Operating model

| Area | India-wide Global Store | Local store |
|---|---|---|
| Ownership | Global Admin manages global catalog products, prices, availability, and inventory under `global-store`. | Location Admin and Store Manager manage their own local product offers and inventory under their own store IDs. |
| Delivery | Nationwide parcel delivery to a saved shipping address. Local delivery radius, zones, pickup, and local delivery partners do not apply. | Nearby delivery is offered only when the customer address matches that store's active delivery zone/radius. Local pickup and partner handoff use that store's settings. |
| Stock source | Product/variant inventory for `global-store`. | Product/variant inventory for the selected local store. |
| Order processing | Global Admin reviews Global Store stock, approves, packs, marks ready, ships with tracking, and records delivery. A global order is never reassigned to a local store. | Store staff confirm, pack, and prepare the order; a local delivery partner or pickup flow completes it. |
| Isolation | Global Admin manages only the Global Store and global catalog/stock. Global stock is not shared with local stores. | Local portals expose records only for their assigned location or store. |

## Cross-role E2E workflows

| ID | Workflow | Status |
|---|---|---|
| E2E-01 | Customer places a local COD order → its Store Manager processes it → a partner assigned to that local store completes OTP delivery → customer sees status and may review it. | Needs verification |
| E2E-02 | Global Admin creates a global product/category → publishes and maps the product only to `global-store` → records variant stock in Global Store inventory → customer sees it only when that stock is available. | Needs verification |
| E2E-03 | Store Manager configures local delivery zones and charges → customer address is checked against that store's service area → checkout shows an available local delivery method and correct price. | Needs verification |
| E2E-04 | Customer places an India-wide order from `global-store` → Global Admin reviews Global Store stock and approves → Global Admin packs and ships the parcel with tracking → customer follows shipment status to delivery. No local store assignment is part of this workflow. | Needs verification |
| E2E-05 | Super Admin provisions staff → each role can manage only its own global, location, or store records; Global Admin is limited to `global-store`. | Needs verification |

## Deployment boundary

Global parcel carrier integration and real nationwide serviceability/rate quotes are separate operational requirements. A Global Store record or local delivery radius does not verify those external shipping capabilities.
