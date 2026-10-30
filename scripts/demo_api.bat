@echo off
setlocal enabledelayedexpansion

echo =======================================================
echo          ROSTERGUARD LIVE API DEMONSTRATION
echo =======================================================

set BASE_URL=http://localhost:8080

echo [1] Authenticating as HR_SYNC user...
curl -s -X POST %BASE_URL%/api/v1/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"tenantId\":\"tenant-moveinsync\",\"username\":\"hr_sync\",\"password\":\"secret123\"}" > auth_hr.json
type auth_hr.json
echo.

echo.
echo [2] Ingesting Version 7 HR Event (Office Transfer to HYD-01)...
curl -s -X POST %BASE_URL%/api/v1/hr/events ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %TOKEN%" ^
  -d "{\"eventId\":\"evt-demo-v7\",\"sourceSystem\":\"WORKDAY\",\"employeeId\":\"EMP-DEMO-01\",\"sourceVersion\":7,\"eventType\":\"UPDATE\",\"name\":\"Devin Archer\",\"email\":\"devin@moveinsync.com\",\"phone\":\"+919876543210\",\"managerId\":\"MGR-01\",\"officeCode\":\"HYD-01\"}"
echo.

echo.
echo [3] Ingesting Out-of-Order Version 6 Event (Should be STALE no-op)...
curl -s -X POST %BASE_URL%/api/v1/hr/events ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %TOKEN%" ^
  -d "{\"eventId\":\"evt-demo-v6\",\"sourceSystem\":\"WORKDAY\",\"employeeId\":\"EMP-DEMO-01\",\"sourceVersion\":6,\"eventType\":\"UPDATE\",\"name\":\"Devin Archer\",\"email\":\"devin@moveinsync.com\",\"phone\":\"+919876543210\",\"managerId\":\"MGR-01\",\"officeCode\":\"BLR-01\"}"
echo.

echo.
echo [4] Replaying Version 7 (Duplicate check)...
curl -s -X POST %BASE_URL%/api/v1/hr/events ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %TOKEN%" ^
  -d "{\"eventId\":\"evt-demo-v7\",\"sourceSystem\":\"WORKDAY\",\"employeeId\":\"EMP-DEMO-01\",\"sourceVersion\":7,\"eventType\":\"UPDATE\",\"name\":\"Devin Archer\",\"email\":\"devin@moveinsync.com\",\"phone\":\"+919876543210\",\"managerId\":\"MGR-01\",\"officeCode\":\"HYD-01\"}"
echo.

echo.
echo =======================================================
echo          DEMONSTRATION COMPLETED
echo =======================================================
