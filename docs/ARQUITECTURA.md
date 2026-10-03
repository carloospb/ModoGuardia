# Arquitectura

```
StatusBarNotification ─► GuardNotificationListener ─► NotificationParser ─► NotificationEvent
                                                                                 │
PHONE_STATE (RINGING / OFFHOOK) ─► PhoneStateReceiver ──────────────┐            ▼
                                                                    └──────► GuardEngine
                                     filtros: guardia activa · anterior a la guardia · resúmenes
                                              · respuestas propias · solo mensajes nuevos
                                     RuleEngine ─► Match / NoMatch (+ grupo no identificado)
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
| `domain/` | Kotlin puro con pruebas JVM: `NotificationEvent`, `Rule`, `RuleEngine`, `WhatsApp` (candidatos a nombre de grupo), `TextNormalizer`, `Deduplicator`, `GuardEngine`. |
| `data/` | `SettingsRepository` (DataStore: ajustes y reglas en JSON), `History` (Room: historial). |
| `service/` | `GuardNotificationListener`, `NotificationParser`, `GuardService` (FGS specialUse + revisión de salud cada minuto), `GuardController` (iniciar / finalizar / restaurar), `DiagnosticsStore` (solo memoria). |
| `alarm/` | `AlarmController`, `AlarmService`, `AlarmPlayer` (+ `VolumeRestore`), `GuardNotifications` (canales), `AlarmState`, `GuardAlarmActivity`. |
| `call/` | `PhoneStateReceiver`. |
| `receiver/` | `BootReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED). |
| `system/` | `SystemStatusChecker` (estado real) y `SystemSettings` (accesos a Ajustes: el gestor de permisos). |
| `ui/` | Compose + Material 3; `MainViewModel`, `RulesViewModel`, `HistoryViewModel`. |

## Decisiones

* **Servicio de guardia en primer plano** mientras la guardia está activa: Android no congela ni
  mata el proceso como a una app en caché, los wakelocks siguen valiendo en Doze y la alarma puede
  arrancar desde segundo plano. Su notificación persistente (obligatoria) muestra el estado.
* **Deduplicación**: huella exacta durante 12 h (último mensaje, o clave + título + texto + `when`)
  y huella de contenido durante 2 min. Al iniciar la guardia se marcan como vistos los avisos que
  ya estaban en la bandeja.
* **Grupo de WhatsApp**: igualdad normalizada (mayúsculas, tildes, espacios, marcas invisibles,
  sufijo «(3 mensajes)») contra `conversationTitle`, el prefijo de `title` («Grupo: Remitente»),
  la forma antigua «Remitente @ Grupo» y `title` si la app marca la conversación como grupal.
  Sin candidatos → «grupo no identificado» en el historial; nunca se adivina.
* **Solo mensajes nuevos**: de un MessagingStyle se evalúan los mensajes posteriores al inicio de
  la guardia; una palabra clave antigua del chat no hace sonar la alarma con un mensaje nuevo.
* **Llamada contestada → alarma detenida**, solo si la alarma la provocó una llamada.
* **Volumen**: el original se guarda en disco antes de cambiarlo y se restaura al detener o, si
  el proceso murió sonando, al volver a arrancar.
