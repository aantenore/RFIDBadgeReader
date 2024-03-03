@echo off
setlocal

if "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
    set MSI_FILE=app\drivers\UsbDk_1.0.22_x64.msi
) else (
    set MSI_FILE=app\drivers\UsbDk_1.0.22_x86.msi
)

set "source_bat=start.bat"
set "link_name=lettore badge"
set "source_bat_path=%CD%\%source_bat%"
set "link_path=%CD%\%link_name%.lnk"
set "final_link_path=%startup_folder%\%link_name%.lnk
set "startup_folder=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
	
rem Verifica se il file MSI specificato esiste
if exist "%MSI_FILE%" (

	(
		echo Set oWS = WScript.CreateObject^("WScript.Shell"^)
		echo sLinkFile = "%link_path%"
		echo Set oLink = oWS.CreateShortcut^(sLinkFile^)
		echo oLink.TargetPath = "%source_bat_path%"
		echo oLink.Save
	) > "%TEMP%\CreateShortcut.vbs"

	cscript //nologo "%TEMP%\CreateShortcut.vbs"
	del "%TEMP%\CreateShortcut.vbs"
	
	move "%link_path%" "%startup_folder%\" >nul
	
	echo Copia del collegamento completata nella cartella di avvio di Windows.
	
    echo Installing %MSI_FILE% ...
    start /w %MSI_FILE%
    echo Installation completed. Restarting the computer...

    shutdown /r /t 5 /c "Driver installation completed. Restarting computer."
) else (
    echo Il file MSI %MSI_FILE% non è presente. Assicurati che il file sia nella stessa directory dello script.
)

endlocal
