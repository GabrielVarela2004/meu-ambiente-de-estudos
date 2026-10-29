@echo off
title Programa de Estudos IA - Launcher
echo ========================================================
echo   Iniciando Plataforma Inteligente de Estudos com IA
echo ========================================================
echo.

start "Estudos - Backend (Spring Boot :8080)" cmd /k "cd /d %~dp0backend && .\mvnw.cmd spring-boot:run"
start "Estudos - Frontend (Vite :5173)" cmd /k "cd /d %~dp0frontend && npm run dev"

echo [OK] Backend e Frontend iniciados em janelas proprias!
echo [OK] Backend disponivel em: http://localhost:8080
echo [OK] Frontend disponivel em: http://localhost:5173
echo.
