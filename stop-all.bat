@echo off
title ResumeAI - Stop All Microservices
color 0C

echo ============================================================
echo        ResumeAI - Stopping All Microservices
echo ============================================================
echo.

echo Killing all Java processes running Spring Boot services...
echo.

for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8761 :8080 :8089 :8084 :8085 :8086 :8087 :8088 :8090 :900 " ^| findstr "LISTENING"') do (
    echo   Stopping PID %%a ...
    taskkill /PID %%a /F >nul 2>&1
)

echo.
echo All microservices stopped.
echo ============================================================
pause
