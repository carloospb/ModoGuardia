# Permisos

| Permiso | Para qué | Tipo |
|---|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` (protege el servicio) | Leer los avisos. El usuario concede el acceso en Ajustes. | Especial |
| `POST_NOTIFICATIONS` | Notificación de guardia, alerta a pantalla completa, avisos de fallo. | En ejecución (13+) |
| `FOREGROUND_SERVICE` | Servicios en primer plano. | Normal |
| `FOREGROUND_SERVICE_SPECIAL_USE` | `GuardService`: vigilancia de guardia activada por el usuario. Ningún tipo estándar encaja; se declara `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. Se puede iniciar desde BOOT_COMPLETED también en Android 15. | Normal |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | `AlarmService`: reproduce la alarma. | Normal |
| `USE_FULL_SCREEN_INTENT` | Abrir la alerta sobre el bloqueo. En Android 14+ Google Play solo lo concede de serie a apps de alarma o llamadas; la app lo comprueba con `canUseFullScreenIntent()` y lleva a Ajustes. | Normal / especial en 14+ |
| `WAKE_LOCK` | CPU despierta mientras suena (`MediaPlayer.setWakeMode`). | Normal |
| `VIBRATE` | Vibración. | Normal |
| `RECEIVE_BOOT_COMPLETED` | Reanudar la guardia tras reiniciar o actualizar. | Normal |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Pedir la exclusión de Doze con el diálogo del sistema. Política de Play: uso aceptable cuando la función principal se rompe con Doze; hay que justificarlo al publicar. | Normal |
| `READ_PHONE_STATE` | Solo si se activa «Despertar con llamadas»: estado RINGING / OFFHOOK. No da el número. | En ejecución |

**No se piden**: `INTERNET`, `QUERY_ALL_PACKAGES` (se usa `<queries>` del lanzador),
`READ_CALL_LOG`, `ANSWER_PHONE_CALLS`, `ACCESS_NOTIFICATION_POLICY`, `SCHEDULE_EXACT_ALARM`,
`SYSTEM_ALERT_WINDOW` ni servicios de accesibilidad.

## Google Play

Antes de publicar en Play habrá que: declarar el uso de `specialUse` y `USE_FULL_SCREEN_INTENT`
en la Play Console, justificar `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` y firmar con un keystore
propio (hoy la release se firma con la clave debug de la máquina, como el resto de apps de CarlosPB).
