@echo off
set APP_HOME=%~dp0
set JAVA_CMD=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_CMD%" goto run
set JAVA_CMD=java
:run
"%JAVA_CMD%" -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*

