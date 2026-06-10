# Script para compilar la aplicación y actualizar la APK en la raíz del proyecto
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME="C:\Users\angel\AppData\Local\Android\Sdk"

Write-Host "Compilando aplicación..." -ForegroundColor Cyan
.\gradlew.bat assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "Copiando APK compilada a la raíz..." -ForegroundColor Green
    Copy-Item app/build/outputs/apk/debug/app-debug.apk smart-label-ocr-debug.apk -Force
    Write-Host "¡Listo! smart-label-ocr-debug.apk ha sido actualizada." -ForegroundColor Green
} else {
    Write-Error "Error durante la compilación Gradle."
}
