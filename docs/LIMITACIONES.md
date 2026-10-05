# Limitaciones de Android y cómo se gestionan

| Situación | Qué pasa | Qué hace Modo Guardia |
|---|---|---|
| Chat o grupo silenciado en WhatsApp, o notificaciones de WhatsApp desactivadas | WhatsApp no publica ningún aviso: nadie puede verlo. | Lo explica en Reglas y muestra en Estado la hora del último aviso visto de cada app de las reglas. |
| WhatsApp no pone el nombre del grupo en un aviso | No se puede saber de qué grupo es. | No lo inventa: «grupo no identificado» en el historial; Diagnóstico muestra los campos recibidos. |
| «No molestar» en silencio total | Android silencia también las alarmas. | 🔴 en Estado y aviso del servicio de guardia. No se lo salta (en Android 15 una app tampoco puede anular el «No molestar» manual del usuario). |
| Horario nocturno de «No molestar» | Puede activarse después de iniciar la guardia. | El servicio lo detecta en su revisión de cada minuto y avisa. |
| Optimización de batería / Doze | Retrasos o cortes de madrugada. | Servicio en primer plano + petición de exclusión; 🟠 si no se concede. |
| Pausa de apps no usadas (11+) | Si no se abre en meses, Android retira permisos y la hiberna. | 🟠 hasta que se desactive «Pausar actividad si no se usa» (`isAutoRevokeWhitelisted`). |
| Restricción en segundo plano | La app no puede funcionar en segundo plano. | 🔴 con acceso a Ajustes. |
| Ahorro de energía del fabricante (Xiaomi, Huawei, Samsung…) | No existe API para comprobarlo. | Línea informativa con instrucciones; no se muestra un estado inventado. |
| Android rechaza iniciar el servicio de alarma en segundo plano | — | Escalón 2 (reproducción en el proceso protegido por GuardService) o 3 (notificación insistente con sonido de alarma). |
| Permiso de pantalla completa retirado (14+) | La alerta no se abre sola sobre el bloqueo. | 🟠; la alarma suena igual y su notificación tiene DETENER ALARMA. |
| Ajustes restringidos (apps instaladas fuera de Play, 13+) | El interruptor de acceso a notificaciones aparece bloqueado. | Explica cómo «Permitir ajustes restringidos». |
| Reinicio | Hasta el primer desbloqueo no hay ajustes ni lector de notificaciones. | Tras desbloquear, BOOT_COMPLETED reanuda la guardia; si no puede, lo avisa. |
| «Forzar detención» | Android no entrega nada a la app hasta que se abre. | No detectable desde dentro; se explica en Estado. |
| Llamadas sin `READ_PHONE_STATE` | No llega el estado del teléfono. | Detección de reserva por la notificación del marcador; 🟠 en Estado. |
| Número de quien llama | Exigiría `READ_CALL_LOG`. | No se pide: se muestra el nombre solo si el marcador lo pone en su aviso. |
| Lista de chats de WhatsApp | WhatsApp no deja a otras apps leer sus chats (solo con su base de datos o accesibilidad, que no se usan). | «Elegir chats de WhatsApp» lista los grupos y chats de los que ya llegó algún aviso, con el nombre exacto de la notificación; el resto se puede escribir a mano. |
| Chat individual con el contacto sin guardar | La notificación trae el número o el nombre de perfil, no un nombre de contacto. | La regla usa lo que trae la notificación; elegirlo de la lista evita errores. |
