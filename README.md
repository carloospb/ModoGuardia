# MODO GUARDIA

**© 2026 CarlosPB · Modo Guardia**

App **Android** para personas de guardia: activas **Modo Guardia**, te duermes, y si llega un aviso
que cumple tus reglas o una llamada, el teléfono lanza una **alarma potente y persistente** que no
para hasta que pulsas **DETENER ALARMA**.

> Autor y propietario: **CarlosPB** (`BuildConfig.AUTHOR`, Ajustes → Acerca de).

## Qué hace

* **Reglas** («¿Qué debe despertarme?»): cualquier aviso de una app · cualquier WhatsApp · un
  **grupo de WhatsApp** concreto · **palabras clave** (cualquiera / todas, con o sin mayúsculas y
  tildes), en una app o en todas.
* **Llamadas**: cualquier llamada telefónica entrante y, aparte, llamadas de apps (WhatsApp,
  Teams…). La llamada funciona con normalidad; la alarma se para al contestar.
* **Alarma**: canal de ALARMA de Android (suena en silencio/vibración), volumen máximo,
  configurable o actual (restaurado al terminar), vibración en bucle, pantalla de alerta sobre el
  bloqueo con full-screen intent. Sin límite de tiempo.
* **Estado del sistema** real: acceso a notificaciones, notificaciones propias, pantalla completa,
  batería, «No molestar», llamadas, servicio de guardia, fabricante.
* **Probar alarma** (3 s, o 15 s para bloquear el teléfono) por el mismo camino que una incidencia.
* **Diagnóstico de notificaciones** (solo en memoria) para ver cómo llegan los avisos de WhatsApp.
* **Historial** local, con borrado.
* **Asistente inicial** de 7 pasos.
* **Sin permiso de Internet.** Nada sale del teléfono.

## Compilar

Requisitos: JDK 17, Android SDK 35. Abrir la carpeta con Android Studio o:

```bash
./gradlew testDebugUnitTest assembleDebug   # pruebas + APK debug
./gradlew assembleRelease                   # APK release (R8)
```

APK: `app/build/outputs/apk/{debug,release}/`.

## Documentación

* [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) — módulos y flujo de un aviso.
* [docs/PERMISOS.md](docs/PERMISOS.md) — cada permiso y por qué.
* [docs/LIMITACIONES.md](docs/LIMITACIONES.md) — lo que Android no permite y cómo se gestiona.
* [docs/PRUEBA-MANUAL.md](docs/PRUEBA-MANUAL.md) — prueba en teléfono real.
