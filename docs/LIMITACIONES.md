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
| Número de quien llama | La señal de llamada solo lo trae con `READ_CALL_LOG`. | Ese permiso es opcional y solo se pide para los números añadidos a mano; si no, se usa el nombre o el número que el marcador pone en su aviso. |
| Número oculto o texto del marcador en otro idioma | El marcador muestra «Número privado», «Número privat», «Unknown»… | Se reconocen esos textos en los idiomas habituales y nunca cuentan como nombre; con «Registro de llamadas», un número oculto se sabe con certeza. En la duda, la llamada queda «sin identificar». |
| Colgaron antes de identificar la llamada | Sonó unos segundos y el marcador no dijo quién era. | Se decide al colgar: «sin identificar» → la preferencia del usuario (por defecto, despertar). |
| Lista de chats de WhatsApp | WhatsApp no deja a otras apps leer sus chats (solo con su base de datos o accesibilidad, que no se usan). | «Elegir chats de WhatsApp» lista los grupos y chats de los que ya llegó algún aviso, con el nombre exacto de la notificación; el resto se puede escribir a mano. |
| Chat individual con el contacto sin guardar | La notificación trae el número o el nombre de perfil, no un nombre de contacto. | La regla usa lo que trae la notificación; elegirlo de la lista evita errores. |
| Quién llama, sin «Registro de llamadas» | La señal de llamada no trae el número. | Se usa el aviso de llamada del marcador: nombre del contacto (en el orden de nombre que tenga el usuario; se guardan las dos formas) o, si no es contacto, el número. |
| El marcador muestra el nombre de una empresa en vez del número | Algunos marcadores identifican empresas que no están en la agenda. | Un número añadido a mano no se puede descartar por ese nombre: la llamada queda «sin identificar» y decide la preferencia (por defecto, despertar). Con «Registro de llamadas» se conoce el número exacto. |
| Marcador que no publica aviso de llamada | No hay nombre ni número. | A los 6 s de sonar, «sin identificar» → la preferencia del usuario. |
| «Números desconocidos» | Sin leer la agenda no se puede saber con seguridad si quien llama es un contacto (y el identificador de empresas lo confunde). | No se ofrece la opción, a propósito. |
| Contacto que llama desde un número no elegido | La señal da otro número. | Si el marcador muestra su nombre, se reconoce por el nombre. |
| Apps de chat sin MessagingStyle ni marca de grupo | No se puede distinguir grupo de persona. | «Tipo desconocido»: no aparecen en «Conversaciones detectadas» y una regla de persona no las acepta; «Todos los avisos» o las palabras clave sí funcionan. |
| Grupo renombrado | El nombre del aviso cambia. | Si la app da un identificador de conversación (shortcutId) se sigue reconociendo; si no, hay que elegirlo de nuevo. |
| Android 12+: tipos de notificación por lector | El usuario puede limitar qué tipos recibe cada lector de notificaciones. | La app pide todos los tipos; si se limitan, el Diagnóstico lo deja ver. |
| Notificaciones «sensibles» (Android 16+) | Android puede ocultar a los lectores ciertos contenidos sensibles (p. ej. códigos). | No afecta a nombres de chats ni de quien llama; una palabra clave dentro de un contenido ocultado no se vería. |
| Emulador / sin teléfono | La interfaz se prueba en la JVM (Robolectric) y la lógica con pruebas unitarias. | La prueba manual (docs/PRUEBA-MANUAL.md) en un teléfono real sigue siendo obligatoria. |
