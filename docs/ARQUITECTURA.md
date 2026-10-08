# Arquitectura

```
StatusBarNotification ─► GuardNotificationListener ─► NotificationParser ─► NotificationEvent
                               │                                                 │
                               └─► SeenChats («conversaciones detectadas»)       │
                                                                                 ▼
PHONE_STATE (RINGING [+ número] / OFFHOOK / IDLE) ─► PhoneStateReceiver ──► GuardEngine
                                     filtros: guardia activa · anterior a la guardia · resúmenes
                                              · respuestas propias · solo mensajes nuevos
                                     RuleEngine.evaluate      ─► Match / NoMatch (+ grupo no identificado)
                                     RuleEngine.evaluateCall  ─► Match / NoMatch / Unidentified
                                     Deduplicator
                                                                                 ▼
                                                                         AlarmController
                                     1) AlarmService (servicio en primer plano, mediaPlayback)
                                     2) en el proceso, protegido por GuardService
                                     3) notificación insistente de reserva
                                                                                 ▼
                                     AlarmPlayer (uso ALARM + volumen + vibración)
                                     GuardAlarmActivity (full-screen intent, sobre el bloqueo)
```

| Paquete | Contenido |
|---|---|
| `domain/` | Kotlin puro con pruebas JVM: `NotificationEvent`, `Rule` (+ `RuleType`), `RuleEngine` (la única lógica de «¿despierta?»), `Conversations` (grupo / persona / desconocido y sus nombres), `Calls` (`CallMode`, `PhoneNumbers`, `CallerId`), `SeenChats`, `ChatCapture` («Identificar con un mensaje»), `SharedChats` (chat compartido desde WhatsApp), `RuleEdits` (cambios de reglas sin duplicados), `WatchSummary` (resumen para el panel), `TextNormalizer`, `Deduplicator`, `GuardEngine`, `UpdatePolicy`. |
| `data/` | `SettingsRepository` (DataStore: ajustes y reglas en JSON, compatible con lo guardado por 1.0/1.1), `RuleCodec`, `SeenChatsRepository`, `History` (Room). |
| `service/` | `GuardNotificationListener`, `ListenerRecovery` (reconexión real del lector), `NotificationParser`, `GuardService` (FGS specialUse + revisión de salud cada minuto, que reconecta el lector si se cae), `GuardController`, `DiagnosticsStore` y `ChatCaptureStore` (solo memoria). |
| `alarm/` | `AlarmController`, `AlarmService`, `AlarmPlayer` (+ `VolumeRestore`), `GuardNotifications`, `AlarmState`, `GuardAlarmActivity`. |
| `call/` | `PhoneStateReceiver`. |
| `receiver/` | `BootReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED). |
| `system/` | `SystemStatusChecker` (estado real) y `SystemSettings` (accesos a Ajustes). |
| `update/` | `Updater` (manifiesto de carlospb.es, descarga, instalador). |
| `ui/` | Compose + Material 3. `theme/` (paleta, Manrope, marca), `Design.kt` (componentes), pantallas y `ViewModels.kt`. Las pantallas no deciden nada: llaman a los ViewModels, que usan `domain/`. |

## Modelo de reglas

Una regla es «qué despierta». Se guardan en una lista en DataStore:

| Tipo | Significado | Campos |
|---|---|---|
| `APP` | Todos los avisos de una app | `packageName` |
| `WHATSAPP_ANY` | Heredada de 1.0/1.1: todos los avisos de WhatsApp y WhatsApp Business | — |
| `GROUP` / `PERSON` | Un grupo o un chat individual de una app | `packageName` (null solo en reglas heredadas: las dos WhatsApp), `name`, `conversationId` (shortcutId, opcional) |
| `KEYWORDS` | Avisos con ciertas palabras | `packageName` (null = cualquier app), `keywords`, `matchAll`, `ignoreCase` |
| `CALL` | Llamada de un contacto o número (con «solo contactos elegidos») | `aliases` (nombres del contacto; vacío en un número a mano), `numbers`, `name` (para mostrar) |

Cada regla es de UNA app: WhatsApp (`com.whatsapp`) y WhatsApp Business (`com.whatsapp.w4b`) se
vigilan por separado, porque a menudo una es la personal y otra la del trabajo. Las reglas de
versiones anteriores que valían para las dos (`WHATSAPP_GROUP` / `WHATSAPP_CHAT` / `WHATSAPP_ANY`, y
las de WhatsApp creadas por la 1.2.0) se leen sin cambios y, al arrancar,
`RuleEdits.splitLegacyWhatsApp` las divide en una regla por cada WhatsApp instalado: siguen sonando
igual y ya se pueden ajustar por separado. Añadir una app nueva no requiere código: sus reglas llevan
su package name.

## Decisiones

* **Servicio de guardia en primer plano** mientras la guardia está activa: Android no congela ni
  mata el proceso como a una app en caché, los wakelocks siguen valiendo en Doze y la alarma puede
  arrancar desde segundo plano. Su notificación persistente muestra el estado y solo se vuelve a
  publicar si cambia o si el usuario la quitó.
* **Deduplicación**: huella exacta durante 12 h (último mensaje, o clave + título + texto + `when`)
  y huella de contenido durante 2 min. Al iniciar la guardia se marcan como vistos los avisos que
  ya estaban en la bandeja.
* **Grupo o persona** (`Conversations.kind`): `EXTRA_IS_GROUP_CONVERSATION` si la app lo da;
  `conversationTitle` distinto del título → grupo; en WhatsApp, el canal como pista; si no se sabe,
  «desconocido» y no se adivina. Una regla de persona nunca coincide con un aviso de grupo.
* **Nombre del grupo**: igualdad normalizada (mayúsculas, tildes, espacios, marcas invisibles,
  sufijo «(3 mensajes)») contra `conversationTitle`, el prefijo de `title` («Grupo: Remitente»),
  la forma antigua «Remitente @ Grupo» y `title` si la app marca la conversación como grupal.
  Además, el `shortcutId` de la conversación (si la app lo da) sigue reconociendo un grupo
  renombrado. Sin candidatos → «grupo no identificado» en el historial.
* **Conversaciones detectadas**: de cada aviso de chat se guarda app, nombre, tipo, shortcutId y
  hora (nunca el mensaje), como mucho 200 y 90 días sin avisar; se pueden desactivar y borrar.
* **Identificar el chat exacto** sin esperar a la lista, con lo único que Android hace público:
  * «Identificar con un mensaje» (`ChatCapture` + `ChatCaptureStore`): mientras la pantalla está
    abierta (como mucho 10 min) el lector pasa cada aviso; se enseña de qué chat viene, con las dos
    lecturas si Android no dice si es grupo o persona, y con su shortcutId para que la regla lo
    reconozca aunque el aviso sea ambiguo. Solo en memoria; al salir se olvida.
  * Compartir desde WhatsApp (`ShareChatActivity` + `SharedChats`): «Exportar chat» trae el asunto
    «Chat de WhatsApp con …» y el nombre del archivo. Solo se leen esos dos textos; nunca se abre el
    archivo ni se lee el texto compartido. Otras apps no dicen el chat al compartir: se explica y se
    ofrece «Identificar con un mensaje».
* **Llamadas con «solo contactos elegidos»** (`GuardEngine`, sesión de llamada): la señal RINGING
  (con el número solo si hay «Registro de llamadas») y el aviso del marcador (nombre o número)
  llegan por separado y en cualquier orden; se acumulan y se decide una vez. Una llamada solo se
  descarta si lo que se sabe descarta a TODOS los elegidos (un contacto se descarta por otro nombre
  o por otro número; un número a mano, solo por otro número). Si no, «sin identificar» → la
  preferencia del usuario (por defecto, despertar) a los 6 s.
* **Solo mensajes nuevos**: de un MessagingStyle se evalúan los mensajes posteriores al inicio de
  la guardia; una palabra clave antigua del chat no hace sonar la alarma con un mensaje nuevo.
* **Llamada contestada → alarma detenida**, solo si la alarma la provocó una llamada.
* **Volumen**: el original se guarda en disco antes de cambiarlo y se restaura al detener o, si
  el proceso murió sonando, al volver a arrancar.
* **Interfaz**: solo tema oscuro (se usa de noche), Manrope incluida en la app, estados con color y
  texto (nunca solo color), áreas táctiles de 48 dp o más, animaciones solo donde informan
  (activar la guardia, cambiar de estado, elegir). La comprobación del estado cada 3 s solo corre
  con la app en pantalla.
