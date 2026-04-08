echo off
cd %~dp0
if not exist "bin\jre-win\bin" (
   if exist "bin\jre-win.zip" (
      echo "Setting up runtime files: extracting bin\jre-win.zip into bin\jre-win"
      PowerShell Expand-Archive -Path "bin\jre-win.zip" -DestinationPath "bin"
   ) else (
      echo "Something is wrong. Didn't find folder bin\jre-win or file bin\jre-win.zip"
      cmd /k
   )
)
:restart
bin\jre-win\bin\java -cp "out\production\BACnetSC;lib\slf4j-api-1.7.28.jar;lib\logback-core-1.2.3.jar;lib\logback-classic-1.2.3.jar;lib\jython-standalone-2.7.2.jar" dev.bscs.common.Application
if %ERRORLEVEL% equ 2 goto restart
