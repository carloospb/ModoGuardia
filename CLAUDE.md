# MODO GUARDIA

Autor y propietario: **CarlosPB**. Esta identificación es permanente: no eliminar
(`BuildConfig.AUTHOR`, Ajustes → Acerca de, este archivo y el README).

## Identidad del proyecto (usar ÚNICAMENTE estos datos)

| Ámbito | Valor |
|---|---|
| Autor | CarlosPB |
| Git (local del repo) | `user.name = CarlossPB`, `user.email = cpmbps4@gmail.com` |
| Remoto | `https://CarlossPB@github.com/CarlossPB/ModoGuardia.git` (rama `main`) |
| Credenciales git | Git Credential Manager con `usehttppath` (igual que KeyFix, NowOrNever y carlospb.es) |
| Vercel | Proyecto `carlospb-es` (`prj_gNoUmbfFRjpdrAsoJKKhD6KcxoCE`), team `team_qYTM2z7J5HwTQ2UNVbqTLPjk`, vinculado en `../docker-carlospb.es/.vercel` |
| Web / descarga | `https://www.carlospb.es/ModoGuardia.apk` (cuando se publique) |
| applicationId | `es.carlospb.modoguardia` |

Reglas de identidad:

* **No** usar la identidad git global de esta máquina (`instalacionesc10` / camarero10): el repo
  tiene configuración local propia.
* **No** usar `gh` para crear repos ni PRs: la sesión de `gh` es de `instalacionesc10`.
* **No** usar el conector MCP de Vercel de Claude: está autenticado en otra cuenta
  (`aragoneses6000-del's projects`), no en la de CarlosPB.
* Publicar en Vercel se hace desde `../docker-carlospb.es` (repo `CarlossPB/carlospb.es`), igual
  que NowOrNever y BT-LINKER: copiar la APK a `carlospb.es/public/`, añadir
  `modoguardia-version.json` y las cabeceras en `vercel.json`, commit y push. **Siempre pedir
  confirmación antes de publicar o hacer push.**

## Qué es

App Android para personas de guardia: mientras **Modo Guardia** está activo, una notificación
que cumpla una regla (app, WhatsApp, grupo de WhatsApp, palabras clave) o una llamada entrante
dispara una alarma potente y persistente que suena hasta que el usuario pulsa **DETENER ALARMA**.

Prioridad: **FIABILIDAD > SIMPLICIDAD > DISEÑO**.

## Reglas para Claude Code

1. Solo APIs públicas de Android. Nada de `@hide`, reflexión, bases de datos de WhatsApp ni
   accesibilidad para leer contenido.
2. Ninguna funcionalidad falsa: cada botón y cada estado visible tiene implementación real y
   comprueba el estado real del sistema.
3. Si Android limita algo, se dice en la interfaz (ver `docs/LIMITACIONES.md`), no se disimula.
4. Sin permiso `INTERNET`: todo es local. No añadir analítica ni red.
5. La lógica vive en `domain/` (pura, con pruebas JVM) y en servicios; nunca en Composables.
6. Cualquier cambio en la cadena de alarma se valida con `./gradlew testDebugUnitTest
   assembleDebug` y con la prueba manual de `docs/PRUEBA-MANUAL.md`.

## Stack

Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.9 · JDK 17 · compileSdk/targetSdk 35 · minSdk 29
(Android 10) · Compose + Material 3 · DataStore · Room (historial) · KSP.

SDK local: `C:/Users/PC/android-sdk` (en `local.properties`, no versionado).

## Arquitectura

Ver `docs/ARQUITECTURA.md`.
