@echo off
title ResumeAI - All Microservices Launcher
color 0A

echo ============================================================
echo        ResumeAI - Starting All Microservices
echo ============================================================
echo.

set BASE_DIR=%~dp0

echo [1/10] Starting Eureka Server (port 8761)...
start "Eureka Server - 8761" cmd /k "cd /d %BASE_DIR%eureka-server && .\mvnw.cmd spring-boot:run"
echo        Waiting 15s for Eureka to initialize...
timeout /t 15 /nobreak >nul

echo [2/10] Starting API Gateway (port 8080)...
start "API Gateway - 8080" cmd /k "cd /d %BASE_DIR%api-gateway && .\mvnw.cmd spring-boot:run"
timeout /t 5 /nobreak >nul

echo [3/10] Starting Auth Service (port 8089)...
start "Auth Service - 8089" cmd /k "cd /d %BASE_DIR%auth-service && .\mvnw.cmd spring-boot:run"
timeout /t 3 /nobreak >nul

echo [4/10] Starting AI Service (port 8084)...
start "AI Service - 8084" cmd /k "cd /d %BASE_DIR%ai-service && .\mvnw.cmd spring-boot:run"
timeout /t 3 /nobreak >nul

echo [5/10] Starting Section Service (port 8085)...
start "Section Service - 8085" cmd /k "cd /d %BASE_DIR%section-service && .\mvnw.cmd spring-boot:run"
timeout /t 3 /nobreak >nul

echo [6/10] Starting Job Service (port 8086)...
start "Job Service - 8086" cmd /k "cd /d %BASE_DIR%job-service && .\mvnw.cmd spring-boot:run"
timeout /t 3 /nobreak >nul

echo [7/10] Starting Template Service (port 8087)...
start "Template Service - 8087" cmd /k "cd /d %BASE_DIR%template-service && .\mvnw.cmd spring-boot:run"
timeout /t 3 /nobreak >nul

echo [8/10] Starting Resume Service (port 8088)...
start "Resume Service - 8088" cmd /k "cd /d %BASE_DIR%resume-service && .\mvnw.cmd spring-boot:run"
timeout /t 3 /nobreak >nul

echo [9/10] Starting Notification Service (port 8090)...
start "Notification Service - 8090" cmd /k "cd /d %BASE_DIR%notification-service && .\mvnw.cmd spring-boot:run"
timeout /t 3 /nobreak >nul

echo [10/10] Starting Payment Service (port 900)...
start "Payment Service - 900" cmd /k "cd /d %BASE_DIR%payment-service && .\mvnw.cmd spring-boot:run"

echo.
echo ============================================================
echo    All 10 microservices are launching!
echo ============================================================
echo.
echo    Eureka Dashboard : http://localhost:8761
echo    API Gateway      : http://localhost:8080
echo    Auth Service     : http://localhost:8089
echo    AI Service       : http://localhost:8084
echo    Section Service  : http://localhost:8085
echo    Job Service      : http://localhost:8086
echo    Template Service : http://localhost:8087
echo    Resume Service   : http://localhost:8088
echo    Notification Svc : http://localhost:8090
echo    Payment Service  : http://localhost:900
echo.
echo    Each service runs in its own window.
echo    Close this window or press any key to exit this launcher.
echo ============================================================
pause >nul
