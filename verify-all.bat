@echo off
setlocal enabledelayedexpansion
title ResumeAI - Verify All Microservices
color 0B

echo ============================================================
echo        ResumeAI - Build ^& Test All Microservices
echo ============================================================
echo.

set BASE_DIR=%~dp0
set PASS=0
set FAIL=0

for %%s in (eureka-server api-gateway auth-service ai-service section-service job-service template-service resume-service notification-service payment-service latex-compiler-service) do (
    echo [Building] %%s ...
    cd /d "%BASE_DIR%%%s"
    call .\mvnw.cmd clean verify -q >nul 2>&1
    if !errorlevel! == 0 (
        echo    %%s : BUILD SUCCESS
        set /a PASS+=1
    ) else (
        echo    %%s : BUILD FAILURE
        set /a FAIL+=1
    )
)

echo.
echo ============================================================
echo    Results: %PASS% passed, %FAIL% failed
echo ============================================================
pause
