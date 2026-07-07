# Provider Contracts Module

## Overview

The Provider Contracts module manages pricing agreements between the TPA (Third Party Administrator) and healthcare providers. It supports full contract lifecycle management, including creation, activation, suspension, termination, and pricing item management.

## Features

- **Contract Lifecycle Management**: DRAFT → ACTIVE → SUSPENDED/EXPIRED/TERMINATED
- **Pricing Negotiation**: Per-service pricing with automatic discount calculation
- **Business Rules**:
  - Only ONE active contract per provider at any time
  - Cannot activate an expired contract
  - Cannot have overlapping date ranges for same provider
  - Pricing modifications restricted based on contract status

## Architecture

```
providercontract/
├── controller/
│   └── ProviderContractController.java    # REST API endpoints
├── dto/
│   ├── ProviderContractCreateDto.java
│   ├── ProviderContractUpdateDto.java
│   ├── ProviderContractResponseDto.java
│   ├── ProviderContractStatsDto.java
│   ├── ProviderContractPricingItemCreateDto.java
│   ├── ProviderContractPricingItemUpdateDto.java
│   └── ProviderContractPricingItemResponseDto.java
├── entity/
│   ├── ProviderContract.java              # Main entity with ContractStatus & PricingModel enums
│   └── ProviderContractPricingItem.java   # Pricing items with auto-calculated discount
├── repository/
│   ├── ProviderContractRepository.java
│   └── ProviderContractPricingItemRepository.java
└── service/
    ├── ProviderContractService.java       # Contract business logic
    └── ProviderContractPricingItemService.java  # Pricing management
```

## Database Schema

### Tables

1. **provider_contracts** (enhanced from V16)
   - `contract_code` (unique)
   - `provider_id` (FK to providers)
   - `status` (DRAFT, ACTIVE, SUSPENDED, EXPIRED, TERMINATED)
   - `pricing_model` (FIXED, DISCOUNT, TIERED, NEGOTIATED)
   - `discount_percent`, `total_value`, `currency`
   - `start_date`, `end_date`, `signed_date`
   - `contact_person`, `contact_phone`, `contact_email`
   - Timestamps and audit fields

2. **provider_contract_pricing_items** (new)
   - `contract_id` (FK)
   - `medical_service_id` (FK)
   - `medical_category_id` (FK, optional override)
   - `base_price`, `contract_price`, `discount_percent` (auto-calculated)
   - `effective_from`, `effective_to`
   - Unique constraint: (contract_id, medical_service_id)

### Migrations

- `V20__enhanced_provider_contracts.sql` - Schema enhancements
- `V21__provider_contracts_rbac.sql` - RBAC permissions

## API Endpoints

### Contract CRUD

| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| GET | `/api/provider-contracts` | `provider_contracts.view` | List all contracts (paginated) |
| GET | `/api/provider-contracts/{id}` | `provider_contracts.view` | Get contract by ID |
| GET | `/api/provider-contracts/code/{code}` | `provider_contracts.view` | Get contract by code |
| GET | `/api/provider-contracts/search?q=&status=` | `provider_contracts.view` | Search contracts |
| GET | `/api/provider-contracts/stats` | `provider_contracts.view` | Get statistics |
| POST | `/api/provider-contracts` | `provider_contracts.create` | Create new contract |
| PUT | `/api/provider-contracts/{id}` | `provider_contracts.update` | Update contract |
| DELETE | `/api/provider-contracts/{id}` | `provider_contracts.delete` | Soft delete contract |

### Provider-Specific Queries

| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| GET | `/api/provider-contracts/provider/{providerId}` | `provider_contracts.view` | Get all contracts for provider |
| GET | `/api/provider-contracts/provider/{providerId}/active` | `provider_contracts.view` | Get active contract for provider |
| GET | `/api/provider-contracts/status/{status}` | `provider_contracts.view` | Get contracts by status |
| GET | `/api/provider-contracts/expiring?days=30` | `provider_contracts.view` | Get expiring contracts |

### Contract Lifecycle

| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| POST | `/api/provider-contracts/{id}/activate` | `provider_contracts.activate` | Activate contract |
| POST | `/api/provider-contracts/{id}/suspend?reason=` | `provider_contracts.activate` | Suspend contract |
| POST | `/api/provider-contracts/{id}/terminate?reason=` | `provider_contracts.activate` | Terminate contract |

### Pricing Management

| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| GET | `/api/provider-contracts/{contractId}/pricing` | `provider_contracts.pricing.view` | List pricing items |
| GET | `/api/provider-contracts/{contractId}/pricing/search?q=` | `provider_contracts.pricing.view` | Search pricing items |
| GET | `/api/provider-contracts/{contractId}/pricing/stats` | `provider_contracts.pricing.view` | Get pricing statistics |
| GET | `/api/provider-contracts/pricing/{pricingId}` | `provider_contracts.pricing.view` | Get pricing item by ID |
| POST | `/api/provider-contracts/{contractId}/pricing` | `provider_contracts.pricing.manage` | Add pricing item |
| POST | `/api/provider-contracts/{contractId}/pricing/bulk` | `provider_contracts.pricing.manage` | Bulk add pricing items |
| PUT | `/api/provider-contracts/pricing/{pricingId}` | `provider_contracts.pricing.manage` | Update pricing item |
| DELETE | `/api/provider-contracts/pricing/{pricingId}` | `provider_contracts.pricing.manage` | Delete pricing item |
| DELETE | `/api/provider-contracts/{contractId}/pricing` | `provider_contracts.pricing.manage` | Delete all pricing (DRAFT only) |

## RBAC Permissions

| Permission | Description |
|------------|-------------|
| `provider_contracts.view` | View contracts and basic pricing |
| `provider_contracts.create` | Create new contracts |
| `provider_contracts.update` | Update existing contracts |
| `provider_contracts.delete` | Soft delete contracts |
| `provider_contracts.activate` | Change contract status (activate, suspend, terminate) |
| `provider_contracts.pricing.view` | View pricing items |
| `provider_contracts.pricing.manage` | Create, update, delete pricing items |

## Business Rules

### Contract Status Transitions

```
                     ┌──────────────┐
                     │    DRAFT     │
                     └──────┬───────┘
                            │ activate()
                     ┌──────▼───────┐
         suspend()───│   ACTIVE     │───▶ EXPIRED (auto)
                     └──────┬───────┘
                            │ terminate()
         activate()  ┌──────▼───────┐
        ◄────────────│  SUSPENDED   │
                     └──────┬───────┘
                            │ terminate()
                     ┌──────▼───────┐
                     │  TERMINATED  │ (final)
                     └──────────────┘
```

### Allowed Status Changes

| From | To | Method |
|------|-----|--------|
| DRAFT | ACTIVE | `activate()` |
| ACTIVE | SUSPENDED | `suspend(reason)` |
| ACTIVE | TERMINATED | `terminate(reason)` |
| ACTIVE | EXPIRED | Auto (scheduled job) |
| SUSPENDED | ACTIVE | `activate()` |
| SUSPENDED | TERMINATED | `terminate(reason)` |

### Pricing Modification Rules

- **DRAFT/SUSPENDED**: Full pricing modifications allowed
- **ACTIVE**: Only price updates allowed
- **EXPIRED/TERMINATED**: No modifications allowed (read-only)

## Example Usage

### Create a New Contract

```http
POST /api/provider-contracts
Content-Type: application/json

{
  "providerId": 1,
  "contractCode": "CON-2024-001",  // Optional, auto-generated if omitted
  "pricingModel": "DISCOUNT",
  "discountPercent": 15.00,
  "startDate": "2024-01-01",
  "endDate": "2024-12-31",
  "totalValue": 500000.00,
  "currency": "LYD",
  "paymentTerms": "Net 30",
  "autoRenew": true,
  "contactPerson": "Dr. Ahmed",
  "contactPhone": "+218-91-1234567",
  "contactEmail": "ahmed@provider.ly",
  "notes": "Annual contract with 15% discount"
}
```

### Add Pricing Items

```http
POST /api/provider-contracts/1/pricing/bulk
Content-Type: application/json

[
  {
    "medicalServiceId": 101,
    "basePrice": 100.00,
    "contractPrice": 85.00,
    "unit": "visit",
    "effectiveFrom": "2024-01-01"
  },
  {
    "medicalServiceId": 102,
    "basePrice": 500.00,
    "contractPrice": 425.00,
    "unit": "procedure"
  }
]
```

### Activate Contract

```http
POST /api/provider-contracts/1/activate
```

### Get Effective Pricing for Claims

```http
GET /api/provider-contracts/provider/5/active
```

## Response Format

All responses follow the standard `ApiResponse<T>` wrapper:

```json
{
  "status": "success",
  "message": "Contract created successfully",
  "data": { /* ProviderContractResponseDto */ },
  "timestamp": "2024-12-24T10:30:00Z"
}
```

## Testing

### Run Module Tests

```bash
cd backend
./mvnw test -Dtest="ProviderContract*Test"
```

### Verify Migrations

```bash
./mvnw flyway:info -Dflyway.configFiles=flyway-mysql.conf
```

## Frontend Integration

The frontend module is located at `frontend/src/pages/provider-contracts/`:

- `ProviderContractsList.jsx` - List view with stats, filters, and DataTable
- `ProviderContractView.jsx` - Detail view with pricing table
- `data/providerContracts.mock.js` - Mock data for development

To integrate with real backend:
1. Replace mock data imports with API calls
2. Use `@tanstack/react-query` for data fetching
3. Update API base URL in `src/config.js`

## Authors

- TBA WAAD Development Team

## Version History

- **v1.0** (2024-12-24): Initial implementation with full CRUD, lifecycle management, and pricing
