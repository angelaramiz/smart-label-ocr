param (
    [string]$Version,
    [switch]$SkipBuild,
    [switch]$SkipGit
)

# Configuración de codificación para PowerShell
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Directorios y variables de entorno del proyecto
$gradlePath = "app/build.gradle.kts"
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME="C:\Users\angel\AppData\Local\Android\Sdk"
$env:STORE_PASSWORD="labelscan_secret_pass"
$env:KEY_PASSWORD="labelscan_secret_pass"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   LABELSCAN AI: SISTEMA AUTOMATIZADO DE RELEASES" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Validar existencia de build.gradle.kts
if (-not (Test-Path $gradlePath)) {
    Write-Error "No se encontro $gradlePath. Asegurate de ejecutar este script desde la raiz del proyecto."
    exit 1
}

# 2. Leer versionName y versionCode actuales
$gradleContent = Get-Content $gradlePath -Raw
$currentVersion = $null
$currentCode = $null

if ($gradleContent -match 'versionCode\s*=\s*(\d+)') {
    $currentCode = [int]$Matches[1]
}
if ($gradleContent -match 'versionName\s*=\s*"([^"]+)"') {
    $currentVersion = $Matches[1]
}

if ($null -eq $currentVersion -or $null -eq $currentCode) {
    Write-Error "No se pudo extraer versionName o versionCode de $gradlePath"
    exit 1
}

Write-Host "Version actual detectada: v$currentVersion (Codigo: $currentCode)" -ForegroundColor Yellow

# 3. Lógica de cálculo de versiones
$newCode = $currentCode + 1
$suggestedVersion = $null

$versionParts = $currentVersion -split '\.'
if ($versionParts.Length -eq 3) {
    $major = [int]$versionParts[0]
    $minor = [int]$versionParts[1]
    $patch = [int]$versionParts[2] + 1
    $suggestedVersion = "$major.$minor.$patch"
} else {
    $suggestedVersion = "$currentVersion.1"
}

# Determinar versión objetivo
$targetVersion = $Version
if ([string]::IsNullOrWhiteSpace($targetVersion)) {
    Write-Host "Ingresa la nueva version [Sugerida: $suggestedVersion] (o presiona Enter para usar la sugerida): " -NoNewline -ForegroundColor Cyan
    $inputVersion = Read-Host
    if ([string]::IsNullOrWhiteSpace($inputVersion)) {
        $targetVersion = $suggestedVersion
    } else {
        $targetVersion = $inputVersion.Trim()
    }
}

Write-Host "Estableciendo version v$targetVersion (Codigo de Version: $newCode)..." -ForegroundColor Cyan

# 4. Modificar app/build.gradle.kts
$updatedContent = $gradleContent -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode"
$updatedContent = $updatedContent -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$targetVersion`""

Set-Content $gradlePath $updatedContent -NoNewline
Write-Host "app/build.gradle.kts actualizada con exito!" -ForegroundColor Green

# 5. Compilación del Release APK
if (-not $SkipBuild) {
    Write-Host "`nCompilando APK en modo RELEASE firmada con Gradle..." -ForegroundColor Cyan
    # Limpieza previa y compilación
    .\gradlew.bat clean assembleRelease

    if ($LASTEXITCODE -ne 0) {
        Write-Error "Error en la compilación Gradle. Proceso abortado."
        exit 1
    }

    # Copiar APK a la raíz
    $apkPath = "app/build/outputs/apk/release/app-release.apk"
    if (Test-Path $apkPath) {
        Copy-Item $apkPath "smart-label-ocr-release.apk" -Force
        Write-Host "smart-label-ocr-release.apk actualizada en la raiz." -ForegroundColor Green
    } else {
        Write-Error "No se encontró la APK compilada en $apkPath"
        exit 1
    }
} else {
    Write-Host "`n[OMITIDO] Compilacion Gradle omitida por parametro." -ForegroundColor Yellow
}

# 6. Lanzamiento en Git y GitHub
if (-not $SkipGit) {
    Write-Host "`nIniciando operaciones Git y publicacion en GitHub..." -ForegroundColor Cyan

    # Añadir cambios
    git add $gradlePath
    git commit -m "chore: bump version to v$targetVersion"
    
    Write-Host "Subiendo cambios a la rama principal (push origin main)..." -ForegroundColor Cyan
    git push origin main

    if ($LASTEXITCODE -ne 0) {
        Write-Warning "El push a main fallo. Asegurate de tener permisos o estar en la rama correcta."
    }

    # Crear tag local y subirlo
    Write-Host "Creando tag v$targetVersion..." -ForegroundColor Cyan
    git tag -a "v$targetVersion" -m "Release v$targetVersion"
    git push origin "v$targetVersion"

    if ($LASTEXITCODE -ne 0) {
        Write-Warning "No se pudo subir la etiqueta v$targetVersion a origin."
    }

    # Crear Release en GitHub usando gh CLI
    Write-Host "Publicando lanzamiento en GitHub..." -ForegroundColor Cyan
    gh release create "v$targetVersion" "smart-label-ocr-release.apk" --title "Release v$targetVersion" --notes "Lanzamiento automatico de la version v$targetVersion compilada y firmada localmente."

    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n==========================================================" -ForegroundColor Green
        Write-Host " EXITO! Version v$targetVersion publicada correctamente en GitHub." -ForegroundColor Green
        Write-Host "==========================================================" -ForegroundColor Green
    } else {
        Write-Error "Hubo un error al crear la publicación en GitHub con 'gh release'."
    }
} else {
    Write-Host "`n[OMITIDO] Publicacion en Git y GitHub omitida por parametro." -ForegroundColor Yellow
}
