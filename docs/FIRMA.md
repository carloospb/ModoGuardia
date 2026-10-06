# Firma de la APK

## Historia

| Versiones | Clave |
|---|---|
| 1.0.0 – 1.3.0 | Clave debug de este PC (`CN=Android Debug`, SHA-256 `d6b8277a…5738bc7`) |
| 1.3.1 en adelante | Clave propia `CN=CarlosPB, O=CarlosPB, L=Madrid, C=ES` (SHA-256 `e29c15f9…f0ff6ff1`) |

El cambio se hizo con **rotación de clave** (APK Signature Scheme v3, Android 9+): cada APK desde la
1.3.1 lleva una prueba, firmada por la clave antigua, de que la nueva la sustituye. Android instala
la actualización encima de una 1.0.0–1.3.0 **conservando reglas, ajustes e historial**. No hace
falta desinstalar.

Motivo: Google Play Protect desconfía de las apps firmadas con un certificado de depuración.

## Dónde está la clave

Fuera del repositorio, en `C:\Users\PC\.modoguardia\`:

| Archivo | Qué es |
|---|---|
| `modoguardia-release.jks` | Clave de publicación (PKCS12, RSA 4096, válida 50 años) |
| `modoguardia-lineage.bin` | Prueba de rotación clave debug → clave propia |
| `signing.properties` | Rutas, alias y contraseña que usa la compilación |

**Haz copia de seguridad de esa carpeta** (fuera de este PC, cifrada). Si se pierde la clave, ninguna
versión nueva se podrá instalar encima de la que ya tienen los usuarios: tendrían que desinstalar y
perderían sus reglas e historial. Nunca se versiona (`.gitignore` excluye `*.jks` y propiedades de
firma).

## Cómo se firma

`./gradlew assembleRelease` genera `app-release-unsigned.apk` y la tarea `signReleaseApk` la firma
con `apksigner` (clave propia + `--lineage`, `--rotation-min-sdk-version 28`, solo esquema v3: la
app exige Android 10+, que verifica v3 primero, y así el certificado debug no aparece en ninguna firma) en
`app/build/outputs/apk/release/app-release.apk`, y la verifica. Sin `signing.properties` la
compilación de release falla a propósito: una release firmada con la clave debug ya no se
instalaría encima de la 1.3.1.

Las compilaciones debug (`es.carlospb.modoguardia.debug`) siguen con la clave debug: son otra app.

## Comprobar una APK

```
java -jar <sdk>/build-tools/<versión>/lib/apksigner.jar verify -v --print-certs app-release.apk
java -jar <sdk>/build-tools/<versión>/lib/apksigner.jar lineage --in app-release.apk --print-certs
```

La primera debe mostrar `CN=CarlosPB`; la segunda, la clave debug como firmante 1 y la de CarlosPB
como firmante 2, las dos con «installed data capability: true».
