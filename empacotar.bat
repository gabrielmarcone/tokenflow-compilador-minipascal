@echo off
setlocal
cd /d "%~dp0"

rem Monta o dist\TokenFlow-windows.zip: o TokenFlow com um Java portatil (com
rem JavaFX) dentro, para abrir com dois cliques em qualquer Windows 10 ou 11
rem de 64 bits, sem instalar nada. Esse zip vai para a pagina de versoes
rem (Releases) do GitHub; ele nao entra no repositorio (dist e java estao no
rem .gitignore).
rem
rem Rode depois do build.bat, para empacotar o TokenFlow.jar atual:
rem   .\build.bat teste
rem   .\empacotar.bat
rem
rem Conteudo do zip, dentro de uma pasta TokenFlow:
rem   TokenFlow.bat, TokenFlow.jar, LEIA-ME.txt, java\ e testes-integracao\

set "DESTINO=dist\TokenFlow-windows.zip"
set "PACOTE=dist\pacote\TokenFlow"

if not exist TokenFlow.jar goto sem_jar

rem Baixa o Java portatil para a pasta java, se ainda nao estiver la.
call TokenFlow.bat --baixar-java
if errorlevel 1 goto erro

echo Montando o pacote...
if exist dist\pacote rmdir /s /q dist\pacote
if exist "%DESTINO%" del "%DESTINO%"
mkdir "%PACOTE%"
if errorlevel 1 goto erro
copy /y TokenFlow.jar "%PACOTE%\" >nul
if errorlevel 1 goto erro
copy /y TokenFlow.bat "%PACOTE%\" >nul
if errorlevel 1 goto erro
copy /y docs\pacote\LEIA-ME.txt "%PACOTE%\" >nul
if errorlevel 1 goto erro
xcopy /e /i /q /y java "%PACOTE%\java" >nul
if errorlevel 1 goto erro
xcopy /e /i /q /y testes-integracao "%PACOTE%\testes-integracao" >nul
if errorlevel 1 goto erro

echo Compactando...
"%SystemRoot%\System32\tar.exe" -a -c -f "%DESTINO%" -C dist\pacote TokenFlow
if errorlevel 1 goto erro
rmdir /s /q dist\pacote

echo.
echo Pacote gerado: %DESTINO%
for %%Z in ("%DESTINO%") do echo Tamanho: %%~zZ bytes
goto fim

:sem_jar
echo TokenFlow.jar nao encontrado. Rode o build.bat antes.

:erro
echo.
echo O empacotamento falhou. Veja a mensagem acima.
endlocal
echo %cmdcmdline% | find /i "/c" >nul && pause
exit /b 1

:fim
endlocal
echo %cmdcmdline% | find /i "/c" >nul && pause
