@echo off
setlocal

cd /d "%~dp0"

set JAVA_HOME=.\app\java\jdk-11.0.21
set APP_JAR=.\app\BadgeApp.jar

start %JAVA_HOME%\bin\javaw.exe -jar %APP_JAR% 

exit
