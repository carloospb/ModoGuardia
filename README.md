# MODO GUARDIA

**© 2026 CarlosPB · Modo Guardia**

App **Android** para personas de guardia: activas **Modo Guardia**, te duermes, y si llega un aviso
que cumple tus reglas o una llamada, el teléfono lanza una **alarma potente y persistente** que no
para hasta que pulsas **DETENER ALARMA**.

> Autor y propietario: **CarlosPB** (`BuildConfig.AUTHOR`, Ajustes → Acerca de).

## Qué hace

* **Centro de control**: un gran escudo activa o desactiva la guardia (animación, vibración
  táctil, estado inequívoco) y debajo se ve qué se vigila: apps, chats, contactos de llamada y el
  estado real del sistema.
* **Qué te despierta**, por aplicación (WhatsApp, WhatsApp Business, Telegram, Signal, Mensajes,
  correo, monitorización…): **todos sus avisos** o **solo los chats elegidos**, separados en
  **personas** y **grupos**, más **palabras clave** (cualquiera / todas, con o sin mayúsculas y
  tildes). WhatsApp y WhatsApp Business se vigilan por separado (personal y trabajo).
* **Conversaciones detectadas**: ninguna app de chat deja leer su lista de conversaciones, así que
  cada chat que manda un aviso aparece en un selector (búsqueda, Todos / Personas / Grupos,
  selección múltiple). Se guarda solo el nombre, nunca los mensajes. También se pueden escribir.
* **Identificar un chat al momento**, sin esperar a que aparezca en la lista:
  * **Con un mensaje** (cualquier app: WhatsApp, Telegram, Slack, Signal…): pulsas el botón, alguien
    escribe en ese chat y Modo Guardia te dice cuál es para vigilarlo con un toque.
  * **Compartiéndolo desde WhatsApp / WhatsApp Business**: en el chat, Exportar chat → «Vigilar en
    Modo Guardia». Llega su nombre exacto; la conversación exportada no se abre.
* **Llamadas**: todas, o solo **contactos elegidos** (selector oficial de Android, sin leer la
  agenda) y **números concretos**. Si no se puede saber quién llama, decide el usuario (por defecto
  despierta). Aparte, llamadas de apps (WhatsApp, Teams…). La llamada funciona con normalidad; la
  alarma se para al contestar.
* **Alarma**: canal de ALARMA de Android (suena en silencio/vibración), volumen máximo,
  configurable o actual (restaurado al terminar), vibración en bucle, pantalla de alerta sobre el
  bloqueo («ALERTA DE GUARDIA» / «LLAMADA DE GUARDIA») con full-screen intent. Sin límite de tiempo.
* **Estado del sistema** real, con arreglo directo de cada punto: acceso a notificaciones,
  notificaciones propias, pantalla completa, batería, «No molestar», llamadas, servicio, fabricante.
* **Probar alarma** (al momento, o en 15 s para bloquear el teléfono), marcada como prueba.
* **Historial** local por días, sin el texto de los mensajes, con borrado.
* **Configuración guiada** en 6 pasos: acceso, qué te despierta, llamadas, alarma y activar.
* **Diagnóstico de notificaciones** (solo en memoria) para ver qué trae cada aviso y qué
  conversación o llamada reconoce Modo Guardia.
* **Actualizador automático**: al abrir la app comprueba carlospb.es y, si hay versión nueva, la
  ofrece, la descarga y abre el instalador (Android siempre pide confirmarlo).
* **Ningún dato sale del teléfono.** Internet solo se usa para el actualizador; sin analítica.

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
