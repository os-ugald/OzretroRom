# Build — Oz RetroRom 0.6.0 Beta 4 Fix 2

```bash
cd ~
unzip ~/storage/downloads/Oz-RetroRom-Android-0.6.0-Beta4-Fix2-IisuLaunchGuard-Source.zip
cd ~/Oz-RetroRom-Android-0.6.0-Beta4-Fix2-IisuLaunchGuard
chmod +x gradlew *.sh
./02_COMPILAR_TERMUX.sh 2>&1 | tee build-beta4-fix2.txt
```

La prueba recomendada es abrir desde iiSU exactamente el mismo juego SNES que produjo el crash. Antes de probar, no es necesario borrar la ROM persistente. Fix 2 limpia automáticamente una fuente iiSU virtual recordada si existe.
