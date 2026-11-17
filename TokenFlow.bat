@echo off
setlocal
cd /d "%~dp0"

rem Abre a interface do TokenFlow com dois cliques.
rem
rem A interface usa o JavaFX, que so vem em alguns Java (o JDK 8 da Oracle
rem ate 2025, o Liberica "Full", o Zulu "FX"...). Este script procura, nesta
rem ordem:
rem   1. a pasta java ao lado dele (o Java portatil do pacote
rem      TokenFlow-windows.zip, ou o que este script baixou antes);
rem   2. o JAVA_HOME;
rem   3. os Java instalados nas pastas de costume de Program Files;
rem   4. o java do PATH;
rem e usa o primeiro que tiver JavaFX (quem confere e o proprio jar, com
rem --verificar). Se nenhum tiver, oferece baixar um Java portatil com JavaFX
rem para a pasta java: nada e instalado no sistema, e da proxima vez ele ja e
rem achado no passo 1.
rem
rem   TokenFlow.bat                 abre a interface
rem   TokenFlow.bat --baixar-java   so baixa o Java portatil (usado pelo empacotar.bat)

set "PASTA=%~dp0"
set "JAR=%PASTA%TokenFlow.jar"
set "PAGINA=https://github.com/gabrielmarcone/tokenflow-compilador-minipascal/releases"

rem Java portatil: Liberica JRE 8u432 Full (com JavaFX) para Windows 64 bits,
rem da BellSoft (licenca GPLv2 com Classpath Exception), e o SHA-256 do zip.
set "URL_JAVA=https://github.com/bell-sw/Liberica/releases/download/8u432%%2B7/bellsoft-jre8u432%%2B7-windows-amd64-full.zip"
set "SHA256_JAVA=7eff95e69c17e0355b8339e423f58bd4726005164f65c8b021e2857008061d3c"
set "PASTA_NO_ZIP=jre8u432-full"

if not exist "%JAR%" goto sem_jar
if /i "%~1"=="--baixar-java" goto so_baixar

set "JAVA="
call :testar "%PASTA%java\bin\"
if not defined JAVA if defined JAVA_HOME call :testar "%JAVA_HOME%\bin\"
if not defined JAVA for /d %%D in ("%ProgramFiles%\Java\*" "%ProgramFiles%\BellSoft\*" "%ProgramFiles%\Zulu\*" "%ProgramFiles%\Eclipse Adoptium\*" "%ProgramFiles(x86)%\Java\*") do if not defined JAVA call :testar "%%~D\bin\"
if not defined JAVA for %%J in (java.exe) do if not "%%~$PATH:J"=="" call :testar "%%~dp$PATH:J"
if not defined JAVA goto sem_java

:abrir
start "" "%JAVA%javaw.exe" -jar "%JAR%"
exit /b 0


rem Usa a pasta bin dada (com a barra no fim) se ela tiver um Java com JavaFX.
:testar
if not exist "%~1java.exe" exit /b
if not exist "%~1javaw.exe" exit /b
"%~1java.exe" -jar "%JAR%" --verificar >nul 2>&1
if errorlevel 1 exit /b
set "JAVA=%~1"
exit /b


:sem_java
echo.
echo Nao foi encontrado neste computador um Java com JavaFX, que a interface
echo do TokenFlow usa.
echo.
echo Posso baixar agora um Java portatil (Liberica JRE 8 Full, com JavaFX,
echo cerca de 80 MB) para a pasta "java", ao lado deste arquivo. Nada e
echo instalado no sistema; para remover, basta apagar a pasta.
echo.
set "RESPOSTA="
set /p "RESPOSTA=Baixar agora? Digite S e tecle Enter (ou so Enter para nao baixar): "
if /i not "%RESPOSTA%"=="S" goto outras_formas
call :baixar
if errorlevel 1 goto outras_formas
call :testar "%PASTA%java\bin\"
if not defined JAVA goto outras_formas
goto abrir

:outras_formas
echo.
echo Outras formas de abrir o TokenFlow:
echo   - baixe o pacote com o Java incluido (TokenFlow-windows.zip) em
echo     %PAGINA%
echo   - ou instale um Java com JavaFX, por exemplo o Liberica JDK 8 "Full"
echo     (https://bell-sw.com/pages/downloads/), e abra este arquivo de novo.
echo.
echo A linha de comando funciona com qualquer Java 8 ou mais novo, por exemplo:
echo   java -cp TokenFlow.jar minipascal.cli.AnaliseSemantica programa.txt saida.txt
echo.
pause
exit /b 1

:sem_jar
echo O arquivo TokenFlow.jar nao esta ao lado deste script:
echo   %JAR%
echo Se voce abriu o .zip direto no Explorador de Arquivos, extraia tudo antes
echo (botao direito no .zip, "Extrair tudo...") e abra o TokenFlow.bat da
echo pasta extraida.
echo.
pause
exit /b 1

:so_baixar
if exist "%PASTA%java\bin\javaw.exe" goto ja_baixado
call :baixar
exit /b %errorlevel%
:ja_baixado
echo O Java portatil ja esta na pasta java.
exit /b 0


rem Baixa o Java portatil, confere o SHA-256 e extrai na pasta java.
rem Usa o curl e o tar do Windows 10 e 11; se faltarem, o PowerShell.
:baixar
set "TEMP_JAVA=%PASTA%java-baixando"
set "ZIP_JAVA=%PASTA%java-baixando\java.zip"
if exist "%PASTA%java" goto pasta_java_existe
if exist "%TEMP_JAVA%" rmdir /s /q "%TEMP_JAVA%"
mkdir "%TEMP_JAVA%" 2>nul
if not exist "%TEMP_JAVA%" goto sem_permissao

echo.
echo Baixando o Java portatil...
set "OK="
if exist "%SystemRoot%\System32\curl.exe" "%SystemRoot%\System32\curl.exe" -L --fail --progress-bar -o "%ZIP_JAVA%" "%URL_JAVA%" && set "OK=1"
if not defined OK powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile($env:URL_JAVA, $env:ZIP_JAVA)" && set "OK=1"
if not defined OK goto falha_download

rem O zip so e usado se for exatamente o publicado pela BellSoft.
set "HASH="
for /f "skip=1 delims=" %%H in ('certutil -hashfile "%ZIP_JAVA%" SHA256') do if not defined HASH set "HASH=%%H"
if not defined HASH goto falha_hash
set "HASH=%HASH: =%"
if /i not "%HASH%"=="%SHA256_JAVA%" goto falha_hash

echo Extraindo...
set "OK="
if exist "%SystemRoot%\System32\tar.exe" "%SystemRoot%\System32\tar.exe" -xf "%ZIP_JAVA%" -C "%TEMP_JAVA%" && set "OK=1"
if not defined OK powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath $env:ZIP_JAVA -DestinationPath $env:TEMP_JAVA -Force" && set "OK=1"
if not defined OK goto falha_extrair
if not exist "%TEMP_JAVA%\%PASTA_NO_ZIP%\bin\javaw.exe" goto falha_extrair
move "%TEMP_JAVA%\%PASTA_NO_ZIP%" "%PASTA%java" >nul
if not exist "%PASTA%java\bin\javaw.exe" goto falha_extrair
rmdir /s /q "%TEMP_JAVA%"
echo Java portatil pronto na pasta java.
exit /b 0

:pasta_java_existe
echo.
echo Ja existe uma pasta "java" ao lado deste arquivo, mas o Java dela nao
echo funcionou. Apague essa pasta e abra este arquivo de novo.
exit /b 1

:sem_permissao
echo.
echo Nao foi possivel criar a pasta "java-baixando" ao lado deste arquivo
echo (pasta sem permissao de escrita?). Copie a pasta do TokenFlow para outro
echo lugar, como a Area de Trabalho, e abra este arquivo de novo.
exit /b 1

:falha_download
echo.
echo Nao foi possivel baixar o Java portatil (sem internet, ou a rede bloqueou?).
echo Endereco: %URL_JAVA%
rmdir /s /q "%TEMP_JAVA%" 2>nul
exit /b 1

:falha_hash
echo.
echo O arquivo baixado nao e o esperado (o SHA-256 nao confere); ele foi apagado.
rmdir /s /q "%TEMP_JAVA%" 2>nul
exit /b 1

:falha_extrair
echo.
echo Nao foi possivel extrair o Java portatil.
rmdir /s /q "%TEMP_JAVA%" 2>nul
exit /b 1
