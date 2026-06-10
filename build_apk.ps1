# Script para compilar la aplicación en modo RELEASE firmada y actualizar la APK en la raíz
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME="C:\Users\angel\AppData\Local\Android\Sdk"

# Contraseñas para firmar la APK con my-upload-key.jks
$env:STORE_PASSWORD="labelscan_secret_pass"
$env:KEY_PASSWORD="labelscan_secret_pass"

Write-Host "Compilando aplicación en modo RELEASE..." -ForegroundColor Cyan
.\gradlew.bat clean assembleRelease

if ($LASTEXITCODE -eq 0) {
    Write-Host "Copiando APK compilada (Release) a la raíz..." -ForegroundColor Green
    Copy-Item app/build/outputs/apk/release/app-release.apk smart-label-ocr-release.apk -Force
    Write-Host "¡Listo! smart-label-ocr-release.apk ha sido actualizada." -ForegroundColor Green
} else {
    Write-Error "Error durante la compilación Gradle."
}
