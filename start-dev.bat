@echo off
title Programa de Estudos IA - Launcher
rem Mesmo fluxo do start-dev.ps1: Docker/PostgreSQL, backend e frontend com reinicio automatico e health check.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-dev.ps1"
pause
