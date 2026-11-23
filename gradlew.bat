@echo off
set VERSION=8.11.1
set BASE=%USERPROFILE%\.gradle\wrapper\dists\gradle-%VERSION%-bin\neonnet
set BIN=%BASE%\gradle-%VERSION%\bin\gradle.bat
if not exist "%BIN%" (
  if not exist "%BASE%" mkdir "%BASE%"
  powershell -NoProfile -Command "Invoke-WebRequest -Uri https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip -OutFile '%BASE%\gradle.zip'"
  powershell -NoProfile -Command "Expand-Archive -Force '%BASE%\gradle.zip' '%BASE%'"
)
call "%BIN%" %*
