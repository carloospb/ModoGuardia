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
| `INTERNET` | Solo el actualizador: leer `https://www.carlospb.es/modoguardia-version.json` al abrir la app y descargar la APK si el usuario acepta. No se envía ningún dato del usuario; la alarma no depende de la red. | Normal |
| `REQUEST_INSTALL_PACKAGES` | Entregar la APK descargada al instalador del sistema. El usuario concede «Instalar apps desconocidas» a mano la primera vez y confirma cada instalación. | Especial |
| `READ_PHONE_STATE` | Solo si se activa «Despertar con llamadas»: estado RINGING / OFFHOOK / IDLE. No da el número. | En ejecución |
| `READ_CALL_LOG` | **Opcional**, solo si el usuario añade números a mano en «Llamadas» y pulsa «Identificar números exactos»: con él Android incluye el número en la señal de llamada entrante (la entrega dos veces, con y sin número). No se lee el historial de llamadas. En Play es un permiso restringido (solo apps de teléfono); fuera de Play se puede pedir. | En ejecución (restringido) |

**Contactos sin permiso**: «Añadir contacto» abre el selector oficial de Android
(`ACTION_PICK` sobre los teléfonos). Android da permiso temporal de lectura SOLO sobre la fila
elegida (nombre, «Apellido, Nombre» y número), así que no hace falta `READ_CONTACTS` y la app nunca
ve la agenda.

**No se piden**: `READ_CONTACTS`, `QUERY_ALL_PACKAGES` (se usa `<queries>` del lanzador),
`READ_CALL_LOG`, `ANSWER_PHONE_CALLS`, `ACCESS_NOTIFICATION_POLICY`, `SCHEDULE_EXACT_ALARM`,
`SYSTEM_ALERT_WINDOW` ni servicios de accesibilidad.

## Google Play

Antes de publicar en Play habrá que: declarar el uso de `specialUse` y `USE_FULL_SCREEN_INTENT`
en la Play Console, justificar `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, quitar `READ_CALL_LOG`
(Play solo lo permite a apps de teléfono; los números a mano seguirían funcionando por el aviso del
marcador) y firmar con un keystore
propio (hoy la release se firma con la clave debug de la máquina, como el resto de apps de CarlosPB).
