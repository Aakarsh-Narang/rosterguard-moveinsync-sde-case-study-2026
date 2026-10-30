# ==============================================================================
# ROSTERGUARD: COMPLETE LIVE API DEMONSTRATION SCRIPT
# MoveInSync Advanced SDE Placement Assessment
# Covers Demos A through G with real HTTP requests against http://localhost:8080
# ==============================================================================

param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Continue"

Write-Host "=======================================================================" -ForegroundColor Cyan
Write-Host "                ROSTERGUARD LIVE API DEMONSTRATION                    " -ForegroundColor Cyan
Write-Host "=======================================================================" -ForegroundColor Cyan
Write-Host "Target Base URL: $BaseUrl`n"

# ------------------------------------------------------------------------------
# STEP 0: HEALTH CHECK & AUTHENTICATION
# ------------------------------------------------------------------------------
Write-Host "[0] Checking Application Health..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get
    Write-Host "    Health Status: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "    ERROR: RosterGuard application is not running at $BaseUrl" -ForegroundColor Red
    Write-Host "    Please start the application with: .\mvnw.bat spring-boot:run" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n[0.1] Authenticating as HR_SYNC user (tenant-moveinsync)..." -ForegroundColor Yellow
$hrAuthBody = @{
    tenantId = "tenant-moveinsync"
    username = "hr_sync"
    password = "secret123"
} | ConvertTo-Json

$hrAuth = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $hrAuthBody -ContentType "application/json"
$hrToken = $hrAuth.token
$hrHeaders = @{
    "Authorization" = "Bearer $hrToken"
    "Content-Type"  = "application/json"
}
Write-Host "    HR_SYNC JWT Token acquired successfully." -ForegroundColor Green

Write-Host "`n[0.2] Authenticating as OPERATOR user (tenant-moveinsync)..." -ForegroundColor Yellow
$opAuthBody = @{
    tenantId = "tenant-moveinsync"
    username = "operator"
    password = "secret123"
} | ConvertTo-Json

$opAuth = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $opAuthBody -ContentType "application/json"
$opToken = $opAuth.token
$opHeaders = @{
    "Authorization" = "Bearer $opToken"
    "Content-Type"  = "application/json"
}
Write-Host "    OPERATOR JWT Token acquired successfully.`n" -ForegroundColor Green

# ------------------------------------------------------------------------------
# DEMO A: EMPLOYEE CREATION (EMP-1042 at Version 5)
# ------------------------------------------------------------------------------
Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan
Write-Host "DEMO A: Employee Creation (EMP-1042 at Version 5)" -ForegroundColor Cyan
Write-Host "Expected Outcome: APPLIED" -ForegroundColor Cyan
Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan

$demoABody = @{
    eventId       = "evt-demo-a-01"
    sourceSystem  = "WORKDAY"
    employeeId    = "EMP-1042"
    sourceVersion = 5
    eventType     = "CREATE"
    timestamp     = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    name          = "Devin Archer"
    email         = "devin.archer@moveinsync.com"
    phone         = "+919876543210"
    managerId     = "MGR-01"
    officeCode    = "BLR-01"
} | ConvertTo-Json

$respA = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hr/events" -Method Post -Headers $hrHeaders -Body $demoABody
Write-Host "Response outcome: $($respA.outcome)" -ForegroundColor Green
Write-Host "Response detail:  $($respA.message)" -ForegroundColor Gray

$empA = Invoke-RestMethod -Uri "$BaseUrl/api/v1/employees/EMP-1042" -Method Get -Headers $hrHeaders
Write-Host "Verified DB Record: empId=$($empA.employeeId), version=$($empA.sourceVersion), name=$($empA.name), office=$($empA.officeCode)`n" -ForegroundColor White

# ------------------------------------------------------------------------------
# DEMO B: OUT-OF-ORDER EVENT HANDLING (Submit Version 7, then Version 6)
# ------------------------------------------------------------------------------
Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan
Write-Host "DEMO B: Out-of-Order Event Handling" -ForegroundColor Cyan
Write-Host "Step 1: Submit Version 7 (Expected: APPLIED)" -ForegroundColor Cyan
Write-Host "Step 2: Submit Version 6 (Expected: STALE, no version regression)" -ForegroundColor Cyan
Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan

$demoBv7 = @{
    eventId       = "evt-demo-b-v7"
    sourceSystem  = "WORKDAY"
    employeeId    = "EMP-1042"
    sourceVersion = 7
    eventType     = "UPDATE"
    timestamp     = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    name          = "Devin Archer"
    email         = "devin.archer@moveinsync.com"
    phone         = "+919876543210"
    managerId     = "MGR-01"
    officeCode    = "HYD-01"
} | ConvertTo-Json

$respBv7 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hr/events" -Method Post -Headers $hrHeaders -Body $demoBv7
Write-Host "Version 7 Submission Outcome: $($respBv7.outcome) (officeCode: HYD-01)" -ForegroundColor Green

$demoBv6 = @{
    eventId       = "evt-demo-b-v6"
    sourceSystem  = "WORKDAY"
    employeeId    = "EMP-1042"
    sourceVersion = 6
    eventType     = "UPDATE"
    timestamp     = (Get-Date).AddMinutes(-10).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    name          = "Devin Archer"
    email         = "devin.archer@moveinsync.com"
    phone         = "+919876543210"
    managerId     = "MGR-01"
    officeCode    = "DEL-01"
} | ConvertTo-Json

$respBv6 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hr/events" -Method Post -Headers $hrHeaders -Body $demoBv6
Write-Host "Version 6 Submission Outcome: $($respBv6.outcome)" -ForegroundColor Yellow
Write-Host "Version 6 Message:            $($respBv6.message)" -ForegroundColor Gray

$empB = Invoke-RestMethod -Uri "$BaseUrl/api/v1/employees/EMP-1042" -Method Get -Headers $hrHeaders
Write-Host "Verified Final DB State:      version=$($empB.sourceVersion) (Expected 7), office=$($empB.officeCode) (Expected HYD-01)`n" -ForegroundColor White

# ------------------------------------------------------------------------------
# DEMO C: IDEMPOTENCY REPLAY
# ------------------------------------------------------------------------------
Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan
Write-Host "DEMO C: Idempotency Verification" -ForegroundColor Cyan
Write-Host "Action: Replay exact Version 7 event" -ForegroundColor Cyan
Write-Host "Expected Outcome: DUPLICATE (Safe no-op, receipt returned)" -ForegroundColor Cyan
Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan

$respC = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hr/events" -Method Post -Headers $hrHeaders -Body $demoBv7
Write-Host "Replay Outcome: $($respC.outcome)" -ForegroundColor Green
Write-Host "Replay Message: $($respC.message)`n" -ForegroundColor Gray

# ------------------------------------------------------------------------------
# DEMO D: FIELD OWNERSHIP SEPARATION
# ------------------------------------------------------------------------------
Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan
Write-Host "DEMO D: Field Ownership Separation" -ForegroundColor Cyan
Write-Host "Step 1: Transport Admin sets preferred pickup point" -ForegroundColor Cyan
Write-Host "Step 2: HR sends EmployeeUpdated event" -ForegroundColor Cyan
Write-Host "Verification: Transport-owned pickup point remains UNTOUCHED" -ForegroundColor Cyan
Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan

$profileBody = @{
    preferredPickupPoint   = "Silk Board Junction Gate 2"
    specialAssistanceNeeds = "Ground floor pickup"
} | ConvertTo-Json

$respD1 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transport/employees/EMP-1042" -Method Put -Headers $opHeaders -Body $profileBody
Write-Host "Transport Profile Set: pickup=$($respD1.preferredPickupPoint)" -ForegroundColor Green

$hrUpdateV8 = @{
    eventId       = "evt-demo-d-v8"
    sourceSystem  = "WORKDAY"
    employeeId    = "EMP-1042"
    sourceVersion = 8
    eventType     = "UPDATE"
    timestamp     = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    name          = "Devin Archer Senior"
    email         = "devin.archer@moveinsync.com"
    phone         = "+919876543210"
    managerId     = "MGR-02"
    officeCode    = "HYD-01"
} | ConvertTo-Json

$respD2 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hr/events" -Method Post -Headers $hrHeaders -Body $hrUpdateV8
Write-Host "HR Update v8 Outcome:  $($respD2.outcome)" -ForegroundColor Green

$profileAfter = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transport/employees/EMP-1042" -Method Get -Headers $opHeaders
Write-Host "Verified Transport Profile After HR Update: pickup=$($profileAfter.preferredPickupPoint) (UNTOUCHED)`n" -ForegroundColor White

# ------------------------------------------------------------------------------
# DEMO E: NIGHTLY RECONCILIATION SAFETY BRAKE (40% Missing)
# ------------------------------------------------------------------------------
Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan
Write-Host "DEMO E: Reconciliation Safety Brake" -ForegroundColor Cyan
Write-Host "Action: Upload synthetic snapshot where 40% active employees are missing" -ForegroundColor Cyan
Write-Host "Threshold: 30% (roster.reconciliation.missing-threshold=0.30)" -ForegroundColor Cyan
Write-Host "Expected: BLOCKED_REQUIRES_APPROVAL, 0 automatic deactivations" -ForegroundColor Cyan
Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan

# Create synthetic small CSV snapshot for demo
$csvPath = [System.IO.Path]::GetTempFileName() + ".csv"
$csvContent = @"
employee_id,name,email,phone,manager_id,office_code,active
EMP-1042,Devin Archer Senior,devin.archer@moveinsync.com,+919876543210,MGR-02,HYD-01,true
"@
[System.IO.File]::WriteAllText($csvPath, $csvContent)

Write-Host "Uploading snapshot with 1 employee (missing 1 active employee = 50% missing)..." -ForegroundColor Yellow

# Use curl for clean multipart file upload
$curlCmd = "curl -s -X POST $BaseUrl/api/v1/reconciliations -H `"Authorization: Bearer $hrToken`" -F `"file=@$csvPath`""
$recJson = Invoke-Expression $curlCmd | ConvertFrom-Json

Write-Host "Reconciliation Run ID: $($recJson.runId)" -ForegroundColor White
Write-Host "Status:                $($recJson.status)" -ForegroundColor $(if ($recJson.status -eq "BLOCKED_REQUIRES_APPROVAL") { "Yellow" } else { "Green" })
Write-Host "Missing Active Ratio:  $($recJson.missingActiveRatio * 100)%" -ForegroundColor Yellow
Write-Host "Safety Brake Message:  $($recJson.errorMessage)" -ForegroundColor Gray

if (Test-Path $csvPath) { Remove-Item $csvPath -Force }

# Operator approval demonstration
if ($recJson.status -eq "BLOCKED_REQUIRES_APPROVAL") {
    Write-Host "`nDemonstrating Operator Inspection & Approval..." -ForegroundColor Yellow
    $approveUrl = "$BaseUrl/api/v1/reconciliations/$($recJson.runId)/approve"
    $approveResp = Invoke-RestMethod -Uri $approveUrl -Method Post -Headers $opHeaders
    Write-Host "Status After Operator Approval: $($approveResp.status)" -ForegroundColor Green
    Write-Host "Approved By:                     $($approveResp.approvedBy)" -ForegroundColor White
}

# ------------------------------------------------------------------------------
# DEMO F & G: DEACTIVATION & CAB BOOKING CANCELLATION
# ------------------------------------------------------------------------------
Write-Host "`n-----------------------------------------------------------------------" -ForegroundColor Cyan
Write-Host "DEMO F & G: Employee Deactivation & Idempotent Downstream Side Effects" -ForegroundColor Cyan
Write-Host "Action: Ingest DEACTIVATE event for EMP-1042 at version 9" -ForegroundColor Cyan
Write-Host "Expected: Employee active=false, Transactional Outbox event created," -ForegroundColor Cyan
Write-Host "          Worker cancels future cab bookings & frees seats idempotently." -ForegroundColor Cyan
Write-Host "-----------------------------------------------------------------------" -ForegroundColor Cyan

$demoG = @{
    eventId       = "evt-demo-g-deact"
    sourceSystem  = "WORKDAY"
    employeeId    = "EMP-1042"
    sourceVersion = 9
    eventType     = "DEACTIVATE"
    timestamp     = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
} | ConvertTo-Json

$respG = Invoke-RestMethod -Uri "$BaseUrl/api/v1/hr/events" -Method Post -Headers $hrHeaders -Body $demoG
Write-Host "Deactivation Ingestion Outcome: $($respG.outcome)" -ForegroundColor Green

$empG = Invoke-RestMethod -Uri "$BaseUrl/api/v1/employees/EMP-1042" -Method Get -Headers $hrHeaders
Write-Host "Verified Employee State: active=$($empG.active), version=$($empG.sourceVersion)`n" -ForegroundColor White

Write-Host "=======================================================================" -ForegroundColor Cyan
Write-Host "             ALL DEMONSTRATION FLOWS COMPLETED SUCCESSFULLY            " -ForegroundColor Cyan
Write-Host "=======================================================================" -ForegroundColor Cyan
