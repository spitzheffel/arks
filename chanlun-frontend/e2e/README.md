# E2E Test Environment Guide

## Quick Start

### Verify Environment

Run the verification script to check if your E2E environment is correctly configured:

**Windows PowerShell:**
```powershell
cd chanlun-frontend
powershell -ExecutionPolicy Bypass -File .\scripts\verify-e2e-env.ps1
```

**Windows CMD:**
```cmd
cd chanlun-frontend\scripts
verify-e2e-env.cmd
```

## Environment Setup

E2E tests require both backend and frontend services running, with backend in Mock mode.

### 1. Start Backend (Mock Mode)

Backend needs `EXCHANGE_API_MOCK=true` environment variable and `e2e` profile.

**Windows CMD:**
```cmd
cd chanlun-frontend\scripts
start-e2e-backend.cmd
```

**Windows PowerShell:**
```powershell
cd chanlun-frontend\scripts
.\start-e2e-backend.ps1
```

**Manual Start:**
```cmd
set EXCHANGE_API_MOCK=true
set SPRING_PROFILES_ACTIVE=e2e
cd chanlun-backend
mvn spring-boot:run -DskipTests
```

### 2. Start Frontend

In another terminal window, start the frontend dev server:

```cmd
cd chanlun-frontend
npm run dev
```

### 3. Run E2E Tests

After both backend and frontend are running:

```cmd
cd chanlun-frontend
npm run test:e2e
```

**Additional test commands:**
- `npm run test:e2e:ui` - Run with Playwright UI
- `npm run test:e2e:headed` - Run with visible browser
- `npm run test:e2e:debug` - Run in debug mode

## Configuration Details

### Backend Config (application-e2e.yml)

- `app.exchange.api-mock: true` - Enable exchange API Mock
- Uses real database connection
- SQL logging disabled for performance

### Frontend Config (.env.e2e)

- `VITE_API_BASE_URL=/api/v1` - API base path
- `VITE_E2E_MODE=true` - E2E test flag

### Mock Mode Behavior

When `EXCHANGE_API_MOCK=true`:

1. **BinanceMockClient** replaces real BinanceClient
2. All exchange API calls return mock data
3. Connection tests always succeed
4. K-line data returns randomly generated mock data
5. Symbol list returns predefined test data

### Mock Test Data

**Spot Symbols:**
- BTCUSDT, ETHUSDT, BNBUSDT, XRPUSDT, ADAUSDT, SOLUSDT, DOGEUSDT, DOTUSDT
- ETHBTC, BNBBTC
- LUNAUSDT (HALT status)

**USDT-M Futures:**
- BTCUSDT, ETHUSDT, BNBUSDT, XRPUSDT, ADAUSDT, SOLUSDT

**COIN-M Futures:**
- BTCUSD_PERP, ETHUSD_PERP, BNBUSD_PERP

## Test File Structure

```
e2e/
├── smoke/
│   ├── datasource.spec.ts    # 31.2 DataSource CRUD + Connection Test
│   ├── history-sync.spec.ts  # 31.3 Manual History Sync
│   └── gap-fill.spec.ts      # 31.4 Gap Detection + Fill
├── confirm-dialog.spec.ts    # Confirm Dialog Tests
├── home.spec.ts              # Home Page Tests
└── README.md                 # This document
```

## Troubleshooting

### Backend Startup Failure

1. Check database connection
2. Confirm PostgreSQL service is running
3. Check if port 8080 is in use

### Frontend Startup Failure

1. Confirm dependencies installed: `npm install`
2. Check if port 5173 is in use

### E2E Test Failure

1. Confirm backend is running with Mock mode
2. Confirm frontend is running
3. Check Playwright browser installed: `npx playwright install chromium`
4. View test report: `chanlun-frontend/playwright-report/index.html`

### Common Issues

**"Connection refused" errors:**
- Backend not running or not on port 8080
- Frontend proxy not configured correctly

**"Element not found" errors:**
- Page not fully loaded, increase timeout
- UI structure changed, update selectors

**"Mock data not returned" errors:**
- Backend not using e2e profile
- EXCHANGE_API_MOCK not set to true
