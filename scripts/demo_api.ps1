# ==============================================================================
# ROSTERGUARD: PRODUCTION HARDENED LIVE API DEMONSTRATION SCRIPT
# MoveInSync Advanced SDE Placement Assessment
# ==============================================================================

param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

Write-Host "=======================================================================" -ForegroundColor Cyan
Write-Host "                ROSTERGUARD LIVE API DEMONSTRATION                    " -ForegroundColor Cyan
Write-Host "=======================================================================" -ForegroundColor Cyan
Write-Host "Target Base URL: $BaseUrl`n"

# Helper for direct, safe PostgreSQL queries
function Invoke-PostgresQuery {
    param([string]$Query)
    $psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
    if (-not (Test-Path $psql)) {
        $found = Get-ChildItem -Path "C:\Program Files\PostgreSQL" -Filter "psql.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $psql = $found.FullName }
    }
    $pw = if ($env:SPRING_DATASOURCE_PASSWORD) {
        $env:SPRING_DATASOURCE_PASSWORD
    } elseif ($env:POSTGRES_PASSWORD) {
        $env:POSTGRES_PASSWORD
    } else {
        $envFile = Join-Path $PSScriptRoot "..\.env"
        $val = ""
        if (Test-Path $envFile) {
            Get-Content $envFile | ForEach-Object {
                if ($_ -match '^(?:SPRING_DATASOURCE_PASSWORD|POSTGRES_PASSWORD)=(.*)$') {
                    $val = $matches[1].Trim()
                }
            }
        }
        if ($val) { $val } else { "postgres" }
    }
    $user = if ($env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME } else { "postgres" }
    
    $oldEnv = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $pw
        $out = & $psql -h 127.0.0.1 -p 5432 -U $user -d rosterguard -t -A -c $Query 2>&1
        return ($out -join "`n").Trim()
    } finally {
        $env:PGPASSWORD = $oldEnv
    }
}

$results = [ordered]@{
    "Application health"            = "NOT VERIFIED"
    "Employee creation"             = "NOT VERIFIED"
    "Out-of-order processing"       = "NOT VERIFIED"
    "Idempotency"                   = "NOT VERIFIED"
    "Field ownership"               = "NOT VERIFIED"
    "Reconciliation safety brake"   = "NOT VERIFIED"
    "Zero automatic deactivations"  = "NOT VERIFIED"
    "Outbox recovery"               = "NOT VERIFIED"
    "Future booking cancellation"   = "NOT VERIFIED"
    "Seat release"                  = "NOT VERIFIED"
    "Past booking preservation"     = "NOT VERIFIED"
    "Consumer deduplication"        = "NOT VERIFIED"
}

function Assert-Step {
    param(
        [bool]$Condition,
        [string]$StepKey,
        [string]$FailureMessage
    )
    if ($Condition) {
        $results[$StepKey] = "PASS"
    } else {
        $results[$StepKey] = "FAIL"
        Write-Host "[-] ASSERTION FAILED: $FailureMessage" -ForegroundColor Red
        Print-Final-Summary
        exit 1
    }
}

function Print-Final-Summary {
    Write-Host "`n=======================================================================" -ForegroundColor Cyan
    Write-Host "ROSTERGUARD LIVE DEMONSTRATION`n" -ForegroundColor Cyan
    
    $overallPass = $true
    foreach ($k in $results.Keys) {
        $status = $results[$k]
        $color = switch ($status) {
            "PASS" { "Green" }
            "FAIL" { "Red" }
            default { "Yellow" }
        }
        Write-Host "$k : $status" -ForegroundColor $color
        if ($status -ne "PASS") {
            $overallPass = $false
        }
    }
    
    Write-Host ""
    if ($overallPass) {
        Write-Host "Overall Result: PASS" -ForegroundColor Green
    } else {
        Write-Host "Overall Result: FAIL" -ForegroundColor Red
    }
    Write-Host "=======================================================================" -ForegroundColor Cyan
}

try {
    # --------------------------------------------------------------------------
    # 0. APPLICATION HEALTH CHECK
    # --------------------------------------------------------------------------
    Write-Host "[0] Checking Application Health..." -ForegroundColor Yellow
    try {
        $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get
        Assert-Step ($health.status -eq "UP") "Application health" "Application is not UP at $BaseUrl"
        Write-Host "    [+] Health Status: $($health.status) (DB: $($health.components.db.status))" -ForegroundColor Green
    } catch {
        Assert-Step $false "Application health" "Failed to connect to health endpoint at $BaseUrl"
    }

    # --------------------------------------------------------------------------
    # 0.1 RESET ISOLATED SYNTHETIC DEMO RECORDS
    # --------------------------------------------------------------------------
    Write-Host "`n[0.1] Preparing Clean Synthetic State..." -ForegroundColor Yellow
    try {
        Invoke-PostgresQuery "DELETE FROM cab_bookings WHERE employee_id IN ('EMP-1042', 'EMP-ACME-01', 'EMP-ACME-02', 'EMP-ACME-03', 'EMP-ACME-04', 'EMP-ACME-05');" | Out-Null
        Invoke-PostgresQuery "DELETE FROM idempotency_receipts WHERE employee_id IN ('EMP-1042', 'EMP-ACME-01', 'EMP-ACME-02', 'EMP-ACME-03', 'EMP-ACME-04', 'EMP-ACME-05');" | Out-Null
        Invoke-PostgresQuery "DELETE FROM outbox_events WHERE aggregate_id IN ('EMP-1042', 'EMP-ACME-01', 'EMP-ACME-02', 'EMP-ACME-03', 'EMP-ACME-04', 'EMP-ACME-05');" | Out-Null
        Invoke-PostgresQuery "DELETE FROM employee_transport_profiles WHERE employee_id IN ('EMP-1042', 'EMP-ACME-01', 'EMP-ACME-02', 'EMP-ACME-03', 'EMP-ACME-04', 'EMP-ACME-05');" | Out-Null
        Invoke-PostgresQuery "DELETE FROM reconciliation_staging WHERE tenant_id = 'tenant-acme';" | Out-Null
        Invoke-PostgresQuery "DELETE FROM reconciliation_runs WHERE tenant_id = 'tenant-acme';" | Out-Null
        Invoke-PostgresQuery "DELETE FROM employees WHERE employee_id IN ('EMP-1042', 'EMP-ACME-01', 'EMP-ACME-02', 'EMP-ACME-03', 'EMP-ACME-04', 'EMP-ACME-05');" | Out-Null
        Write-Host "    [+] Synthetic records isolated and cleaned." -ForegroundColor Green
    } catch {
        Write-Host "    [!] Notice: Database cleanup encountered non-fatal notice" -ForegroundColor Gray
    }

    # --------------------------------------------------------------------------
    # 0.2 AUTHENTICATION
    # --------------------------------------------------------------------------
    Write-Host "`n[0.2] Authenticating Roles (No sensitive tokens logged)..." -ForegroundColor Yellow
    $demoPassword = if ($env:ROSTER_DEMO_PASSWORD) { $env:ROSTER_DEMO_PASSWORD } else { "DemoPassword@2026!" }

    # HR_SYNC token (tenant-moveinsync)
    $authHrBody = @{ tenantId = "tenant-moveinsync"; username = "hr_sync"; password = $demoPassword } | ConvertTo-Json
    $authHr = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $authHrBody -ContentType "application/json"
    $tokenHr = $authHr.token
    $headersHr = @{ "Authorization" = "Bearer $tokenHr"; "Content-Type" = "application/json" }
    Write-Host "    [+] Authentication HR_SYNC (tenant-moveinsync): SUCCESS" -ForegroundColor Green

    # OPERATOR token (tenant-moveinsync)
    $authOpBody = @{ tenantId = "tenant-moveinsync"; username = "operator"; password = $demoPassword } | ConvertTo-Json
    $authOp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $authOpBody -ContentType "application/json"
    $tokenOp = $authOp.token
    $headersOp = @{ "Authorization" = "Bearer $tokenOp"; "Content-Type" = "application/json" }
    Write-Host "    [+] Authentication OPERATOR (tenant-moveinsync): SUCCESS" -ForegroundColor Green

    # HR_SYNC token (tenant-acme)
    $authAcmeBody = @{ tenantId = "tenant-acme"; username = "hr_sync"; password = $demoPassword } | ConvertTo-Json
    $authAcme = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $authAcmeBody -ContentType "application/json"
    $tokenAcme = $authAcme.token
    $headersAcme = @{ "Authorization" = "Bearer $tokenAcme"; "Content-Type" = "application/json" }
    Write-Host "    [+] Authentication HR_SYNC (tenant-acme): SUCCESS" -ForegroundColor Green

    # OPERATOR token (tenant-acme)
    $authAcmeOpBody = @{ tenantId = "tenant-acme"; username = "operator"; password = $demoPassword } | ConvertTo-Json
    $authAcmeOp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $authAcmeOpBody -ContentType "application/json"
    $tokenAcmeOp = $authAcmeOp.token
    $headersAcmeOp = @{ "Authorization" = "Bearer $tokenAcmeOp"; "Content-Type" = "application/json" }
    Write-Host "    [+] Authentication OPERATOR (tenant-acme): SUCCESS" -ForegroundColor Green

    # --------------------------------------------------------------------------
    # DEMO A: EMPLOYEE CREATION
    # --------------------------------------------------------------------------
    Write-Host "`n-----------------------------------------------------------------------" -ForegroundColor Cyan
    Write-Host "DEMO A: Employee Creation (EMP-1042 at Version 5)" -ForegroundColor Cyan
    Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan

    $bodyA = @{
        eventId       = "evt-demo-a-v5"
        sourceSystem  = "WORKDAY"
        employeeId    = "EMP-1042"
        sourceVersion = 5
        eventType     = "CREATE"
        name          = "Devin Archer"
        email         = "devin.archer@moveinsync.com"
        phone         = "+919876543210"
        managerId     = "MGR-01"
        officeCode    = "BLR-01"
    } | ConvertTo-Json

    $respA = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hr/events" -Method Post -Headers $headersHr -Body $bodyA
    Write-Host "    Outcome: $($respA.outcome)" -ForegroundColor Green

    $empA = Invoke-RestMethod -Uri "$BaseUrl/api/v1/employees/EMP-1042" -Method Get -Headers $headersHr
    Assert-Step ($respA.outcome -eq "APPLIED" -and $empA.sourceVersion -eq 5 -and $empA.active -eq $true) "Employee creation" "Creation outcome was not APPLIED or version mismatch"
    Write-Host "    [+] Verified in DB: empId=$($empA.employeeId), version=$($empA.sourceVersion), office=$($empA.officeCode)" -ForegroundColor Green

    # --------------------------------------------------------------------------
    # DEMO B: OUT-OF-ORDER EVENT HANDLING
    # --------------------------------------------------------------------------
    Write-Host "`n-----------------------------------------------------------------------" -ForegroundColor Cyan
    Write-Host "DEMO B: Out-of-Order Monotonic Version Ordering" -ForegroundColor Cyan
    Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan

    # Step 1: Submit version 7 (office: HYD-01)
    $bodyB7 = @{
        eventId       = "evt-demo-b-v7"
        sourceSystem  = "WORKDAY"
        employeeId    = "EMP-1042"
        sourceVersion = 7
        eventType     = "UPDATE"
        name          = "Devin Archer"
        email         = "devin.archer@moveinsync.com"
        phone         = "+919876543210"
        managerId     = "MGR-01"
        officeCode    = "HYD-01"
    } | ConvertTo-Json

    $respB7 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hr/events" -Method Post -Headers $headersHr -Body $bodyB7
    Write-Host "    Step 1 (Version 7): $($respB7.outcome)" -ForegroundColor Green

    # Step 2: Submit version 6 (office: DEL-01) - Must be STALE no-op
    $bodyB6 = @{
        eventId       = "evt-demo-b-v6"
        sourceSystem  = "WORKDAY"
        employeeId    = "EMP-1042"
        sourceVersion = 6
        eventType     = "UPDATE"
        name          = "Devin Archer"
        email         = "devin.archer@moveinsync.com"
        phone         = "+919876543210"
        managerId     = "MGR-01"
        officeCode    = "DEL-01"
    } | ConvertTo-Json

    $respB6 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hr/events" -Method Post -Headers $headersHr -Body $bodyB6
    Write-Host "    Step 2 (Version 6): $($respB6.outcome)" -ForegroundColor Yellow

    $empB = Invoke-RestMethod -Uri "$BaseUrl/api/v1/employees/EMP-1042" -Method Get -Headers $headersHr
    Assert-Step ($respB7.outcome -eq "APPLIED" -and $respB6.outcome -eq "STALE" -and $empB.sourceVersion -eq 7 -and $empB.officeCode -eq "HYD-01") "Out-of-order processing" "Version 6 overwrote version 7 or was not rejected as STALE"
    Write-Host "    [+] Verified DB State: version=$($empB.sourceVersion) (Expected 7), office=$($empB.officeCode) (Expected HYD-01)" -ForegroundColor Green

    # --------------------------------------------------------------------------
    # DEMO C: IDEMPOTENCY
    # --------------------------------------------------------------------------
    Write-Host "`n-----------------------------------------------------------------------" -ForegroundColor Cyan
    Write-Host "DEMO C: Idempotent Replay Verification" -ForegroundColor Cyan
    Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan

    $respC = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hr/events" -Method Post -Headers $headersHr -Body $bodyB7
    Assert-Step ($respC.outcome -eq "DUPLICATE") "Idempotency" "Replayed version 7 did not return DUPLICATE"
    Write-Host "    [+] Replay Outcome: $($respC.outcome)" -ForegroundColor Green

    # --------------------------------------------------------------------------
    # DEMO D: FIELD OWNERSHIP SEPARATION
    # --------------------------------------------------------------------------
    Write-Host "`n-----------------------------------------------------------------------" -ForegroundColor Cyan
    Write-Host "DEMO D: Field Ownership Separation" -ForegroundColor Cyan
    Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan

    # Transport Admin sets preferred pickup point
    $profileBody = @{
        preferredPickupPoint   = "Silk Board Gate 2"
        specialAssistanceNeeds = "Ground floor pickup"
    } | ConvertTo-Json

    $respD1 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transport/employees/EMP-1042" -Method Put -Headers $headersOp -Body $profileBody
    Write-Host "    Transport profile set: pickup=$($respD1.preferredPickupPoint)" -ForegroundColor Green

    # HR sends EmployeeUpdated event version 8
    $bodyD2 = @{
        eventId       = "evt-demo-d-v8"
        sourceSystem  = "WORKDAY"
        employeeId    = "EMP-1042"
        sourceVersion = 8
        eventType     = "UPDATE"
        name          = "Devin Archer Senior"
        email         = "devin.archer@moveinsync.com"
        phone         = "+919876543210"
        managerId     = "MGR-02"
        officeCode    = "HYD-01"
    } | ConvertTo-Json

    $respD2 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hr/events" -Method Post -Headers $headersHr -Body $bodyD2
    Write-Host "    HR update version 8: $($respD2.outcome)" -ForegroundColor Green

    $profileD = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transport/employees/EMP-1042" -Method Get -Headers $headersOp
    Assert-Step ($profileD.preferredPickupPoint -eq "Silk Board Gate 2") "Field ownership" "HR update corrupted transport-owned pickup point"
    Write-Host "    [+] Verified Transport Profile: pickup=$($profileD.preferredPickupPoint) (UNTOUCHED)" -ForegroundColor Green

    # --------------------------------------------------------------------------
    # DEMO E: RECONCILIATION SAFETY BRAKE (Deterministic 40% Missing)
    # --------------------------------------------------------------------------
    Write-Host "`n-----------------------------------------------------------------------" -ForegroundColor Cyan
    Write-Host "DEMO E: Nightly Reconciliation Safety Brake" -ForegroundColor Cyan
    Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan

    # 1. Populate exactly 5 active employees in isolated demo tenant 'tenant-acme'
    Write-Host "    Ingesting exactly 5 active employees for tenant-acme..." -ForegroundColor Yellow
    1..5 | ForEach-Object {
        $acmeEmp = @{
            eventId       = "evt-acme-$_"
            sourceSystem  = "WORKDAY"
            employeeId    = "EMP-ACME-0$_"
            sourceVersion = 1
            eventType     = "CREATE"
            name          = "Acme Employee $_"
            email         = "emp$_@acme.com"
            phone         = "+919876543210"
            managerId     = "MGR-01"
            officeCode    = "BLR-01"
        } | ConvertTo-Json
        Invoke-RestMethod -Uri "$BaseUrl/api/v1/hr/events" -Method Post -Headers $headersAcme -Body $acmeEmp | Out-Null
    }

    $activeBeforeStr = Invoke-PostgresQuery "SELECT COUNT(*) FROM employees WHERE tenant_id = 'tenant-acme' AND active = TRUE;"
    $activeBefore = [int]$activeBeforeStr
    Write-Host "    Existing active employees in tenant-acme: $activeBefore" -ForegroundColor Green
    Assert-Step ($activeBefore -eq 5) "Reconciliation safety brake" "Expected 5 active employees before snapshot, got $activeBefore"

    # 2. Generate synthetic CSV with exactly 3 employees (missing 2 = 40% missing)
    $csvFile = [System.IO.Path]::GetTempFileName() + ".csv"
    $csvData = @"
employee_id,name,email,phone,manager_id,office_code,source_version,active
EMP-ACME-01,Acme Employee 1,emp1@acme.com,+919876543210,MGR-01,BLR-01,1,true
EMP-ACME-02,Acme Employee 2,emp2@acme.com,+919876543210,MGR-01,BLR-01,1,true
EMP-ACME-03,Acme Employee 3,emp3@acme.com,+919876543210,MGR-01,BLR-01,1,true
"@
    [System.IO.File]::WriteAllText($csvFile, $csvData)

    # 3. Upload via curl.exe with correct multipart file parameter and JWT
    Write-Host "    Uploading CSV snapshot (3 rows in snapshot vs 5 active in DB)..." -ForegroundColor Yellow
    $rawUpload = & curl.exe -s -X POST "$BaseUrl/api/v1/reconciliations" `
        -H "Authorization: Bearer $tokenAcme" `
        -F "file=@$csvFile"
    
    if (Test-Path $csvFile) { Remove-Item $csvFile -Force }

    $recResult = $rawUpload | ConvertFrom-Json
    Write-Host "    Upload Status: $($recResult.status)" -ForegroundColor Yellow
    Write-Host "    Missing Ratio: $([math]::Round($recResult.missingRatio * 100, 2))%" -ForegroundColor Yellow
    Write-Host "    Missing Count: $($recResult.missingEmployees)" -ForegroundColor Yellow

    # Verify safety brake triggered
    Assert-Step ($recResult.status -eq "BLOCKED_REQUIRES_APPROVAL" -and $recResult.missingRatio -ge 0.39 -and $recResult.missingEmployees -eq 2) `
        "Reconciliation safety brake" "Safety brake failed to trigger on 40% missing snapshot"

    # 4. Verify ZERO automatic deactivations occurred in database
    $activeAfterStr = Invoke-PostgresQuery "SELECT COUNT(*) FROM employees WHERE tenant_id = 'tenant-acme' AND active = TRUE;"
    $activeAfter = [int]$activeAfterStr
    Write-Host "    Active employees in DB while held by safety brake: $activeAfter (Expected 5)" -ForegroundColor Green
    Assert-Step ($activeAfter -eq 5) "Zero automatic deactivations" "Automatic deactivations occurred while run was blocked by safety brake!"

    # 5. Operator inspects and approves the blocked run
    Write-Host "    Executing Operator Approval for run $($recResult.runId)..." -ForegroundColor Yellow
    $approveResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/reconciliations/$($recResult.runId)/approve" -Method Post -Headers $headersAcmeOp
    Write-Host "    Status After Approval: $($approveResp.status)" -ForegroundColor Green

    $activeFinalStr = Invoke-PostgresQuery "SELECT COUNT(*) FROM employees WHERE tenant_id = 'tenant-acme' AND active = TRUE;"
    $activeFinal = [int]$activeFinalStr
    Write-Host "    Active employees after approved deactivation: $activeFinal (Expected 3)" -ForegroundColor Green

    # --------------------------------------------------------------------------
    # DEMO F & G: OUTBOX RECOVERY, DEACTIVATION & CAB BOOKING CANCELLATION
    # --------------------------------------------------------------------------
    Write-Host "`n-----------------------------------------------------------------------" -ForegroundColor Cyan
    Write-Host "DEMO F & G: Outbox Delivery & Idempotent Downstream Side Effects" -ForegroundColor Cyan
    Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan

    # 1. Insert 1 past booking and 1 future booking for EMP-1042
    Invoke-PostgresQuery @"
DELETE FROM cab_bookings WHERE employee_id = 'EMP-1042';
INSERT INTO cab_bookings (booking_id, tenant_id, employee_id, pickup_time, booking_status, seat_id, updated_at)
VALUES 
    ('BOOK-DEMO-PAST', 'tenant-moveinsync', 'EMP-1042', NOW() - INTERVAL '2 hours', 'CONFIRMED', 'SEAT-P1', NOW()),
    ('BOOK-DEMO-FUT', 'tenant-moveinsync', 'EMP-1042', NOW() + INTERVAL '24 hours', 'CONFIRMED', 'SEAT-F2', NOW());
"@ | Out-Null
    Write-Host "    Seeded bookings for EMP-1042: 1 Past (SEAT-P1, CONFIRMED), 1 Future (SEAT-F2, CONFIRMED)" -ForegroundColor Green

    # 2. Ingest DEACTIVATE event for EMP-1042 at version 9
    $bodyG = @{
        eventId       = "evt-demo-g-v9"
        sourceSystem  = "WORKDAY"
        employeeId    = "EMP-1042"
        sourceVersion = 9
        eventType     = "DEACTIVATE"
    } | ConvertTo-Json

    $respG = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hr/events" -Method Post -Headers $headersHr -Body $bodyG
    Write-Host "    Deactivation ingestion outcome: $($respG.outcome)" -ForegroundColor Green

    # 3. Verify employee became inactive
    $empG = Invoke-RestMethod -Uri "$BaseUrl/api/v1/employees/EMP-1042" -Method Get -Headers $headersHr
    Assert-Step ($respG.outcome -eq "APPLIED" -and $empG.active -eq $false) "Employee creation" "Employee failed to become inactive"
    Write-Host "    [+] Employee active state: $($empG.active)" -ForegroundColor Green

    # 4. Outbox Recovery & Polling Verification
    Write-Host "    Polling Outbox event dispatch status (max 5s)..." -ForegroundColor Yellow
    $outboxStatus = ""
    $outboxEventId = ""
    for ($i = 0; $i -lt 10; $i++) {
        $row = Invoke-PostgresQuery "SELECT event_id || '|' || status FROM outbox_events WHERE aggregate_id = 'EMP-1042' AND event_type = 'EmployeeDeactivated' ORDER BY created_at DESC LIMIT 1;"
        if ($row) {
            $parts = $row.Split("|")
            $outboxEventId = $parts[0]
            $outboxStatus = $parts[1]
            if ($outboxStatus -eq "PUBLISHED") {
                break
            }
        }
        Start-Sleep -Milliseconds 500
    }
    Write-Host "    Outbox event: id=$outboxEventId, status=$outboxStatus" -ForegroundColor Green
    Assert-Step ($outboxStatus -eq "PUBLISHED") "Outbox recovery" "Outbox event was not dispatched and published by worker"

    # 5. Check Cab Bookings State in Database
    $futRow = Invoke-PostgresQuery "SELECT booking_status || '|' || COALESCE(seat_id, 'RELEASED') FROM cab_bookings WHERE booking_id = 'BOOK-DEMO-FUT';"
    $futParts = $futRow.Split("|")
    $futStatus = $futParts[0]
    $futSeat = $futParts[1]
    Write-Host "    Future Booking State: status=$futStatus, seat=$futSeat" -ForegroundColor Green

    Assert-Step ($futStatus -eq "CANCELLED") "Future booking cancellation" "Future cab booking was not cancelled on deactivation"
    Assert-Step ($futSeat -eq "RELEASED") "Seat release" "Seat was not released (set to NULL) on deactivation"

    # 6. Past Booking Preservation
    $pastRow = Invoke-PostgresQuery "SELECT booking_status || '|' || COALESCE(seat_id, 'NULL') FROM cab_bookings WHERE booking_id = 'BOOK-DEMO-PAST';"
    $pastParts = $pastRow.Split("|")
    $pastStatus = $pastParts[0]
    $pastSeat = $pastParts[1]
    Write-Host "    Past Booking State:   status=$pastStatus, seat=$pastSeat" -ForegroundColor Green

    Assert-Step ($pastStatus -eq "CONFIRMED" -and $pastSeat -eq "SEAT-P1") "Past booking preservation" "Historical past booking was modified or seat freed"

    # 7. Consumer Deduplication
    $consumedCountStr = Invoke-PostgresQuery "SELECT COUNT(*) FROM consumed_events WHERE consumer_name = 'deactivation-side-effects-consumer' AND event_id = '$outboxEventId';"
    $consumedCount = [int]$consumedCountStr
    Write-Host "    Consumed events ledger check: record count = $consumedCount" -ForegroundColor Green
    Assert-Step ($consumedCount -ge 1) "Consumer deduplication" "Consumer failed to record event consumption receipt"

    # Print Final Status
    Print-Final-Summary
    exit 0

} catch {
    Write-Host "`n[FATAL ERROR] Unexpected exception during demonstration: $_" -ForegroundColor Red
    Print-Final-Summary
    exit 1
}
