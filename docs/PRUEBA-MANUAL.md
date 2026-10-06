# Prueba manual en teléfono real

Hace falta otro teléfono (o WhatsApp Web) para enviar mensajes y llamar. Repítela con cada cambio
en la cadena de alarma, junto con `./gradlew testDebugUnitTest assembleDebug`.

## Base

1. Instala la APK y completa la configuración guiada. En **Ajustes → Estado del sistema**, todo en
   verde salvo lo que decidas no corregir.
2. **Probar con el teléfono bloqueado (15 s)** y bloquea. Debe encenderse la pantalla y verse
   «PRUEBA DE ALARMA» sobre el bloqueo, sonar en bucle y vibrar. Espera más de 1 minuto: no se
   para sola. DETENER ALARMA → silencio, y tu volumen de alarma vuelve a su valor.
3. Toca el escudo de Inicio: debe animarse, vibrar levemente y decir «Modo Guardia activo».

## Chats (WhatsApp; repite con Telegram si lo usas)

4. Desde el otro teléfono escribe por **un grupo** y por **chat individual**. En **Reglas →
   WhatsApp → Elegir chats** deben aparecer los dos (Personas / Grupos), con su nombre exacto.
5. Elige solo el grupo `GUARDIAS SOPORTE` («Solo los chats que elijas»). Activa la guardia y bloquea.
6. Escribe en el grupo `URGENTE: servidor principal caído.` → «ALERTA DE GUARDIA», WhatsApp,
   «GUARDIAS SOPORTE» y el mensaje.
7. DETENER. Escribe en **otro** grupo y por **chat individual** → no suena; en Historial → Todo,
   «Notificación ignorada · WhatsApp».
8. Añade a la persona del otro teléfono (Personas). Te escribe por chat individual → suena.
   Esa misma persona escribe en un grupo que no vigilas → NO suena.
9. Activa **Diagnóstico** (Estado del sistema) y escribe: comprueba «Tipo de conversación» y
   «Conversación reconocida».
10. **Identificar con un mensaje**: Reglas → WhatsApp → «Identificar con un mensaje». Desde el otro
    teléfono escribe en un grupo nuevo → aparece en segundos con su nombre y «Grupo». «Vigilar» →
    «Vigilado». Sal de la pantalla y vuelve: ya no hay avisos de antes (no se guarda nada).
    Repite con Telegram (o Slack/Signal) desde «Añadir aplicación».
11. **Compartir desde WhatsApp**: abre un chat que nunca te haya escrito → ⋮ → Más → Exportar chat →
    Sin archivos → «Vigilar en Modo Guardia». Debe salir su nombre exacto; elige «No estoy seguro» y
    «Vigilar este chat». En Reglas → WhatsApp aparece como persona y como grupo. Ese chat te escribe
    con la guardia activa → suena.
12. Comparte un enlace o un mensaje desde Telegram con «Vigilar en Modo Guardia» → explica que
    Telegram no dice el chat y ofrece «Identificar con un mensaje».

## Llamadas

10. **Llamadas → Todas las llamadas** (concede «Teléfono»). Llama → suena la alarma además del
    timbre. Contesta → la alarma se para y la llamada funciona.
11. **Solo contactos y números elegidos → Añadir contacto**: elige el otro teléfono en el selector
    (no debe pedir permiso de contactos). Llama desde él → «LLAMADA DE GUARDIA» con su nombre.
12. Llama desde un tercer teléfono (o pide que te llamen) → NO suena; en Historial → Todo,
    «Llamada ignorada».
13. Borra el contacto de la regla y **Añadir número** con el número del otro teléfono, sin que
    esté en tu agenda → suena al llamar. Prueba «Identificar números exactos» y repite.
13b. Llama con el número oculto (#31# delante del número) → «LLAMADA DE GUARDIA» como «Llamada
    sin identificar» (con «Despertarme igualmente»). Con «No despertarme», no suena. Repite
    colgando a los 2 segundos: debe comportarse igual.

## Robustez

14. Con la guardia activa, **reinicia** y desbloquea → notificación «Modo Guardia activo» y en
    Historial «Guardia restaurada». Repite el paso 6.
15. Activa «No molestar» en silencio total → Inicio muestra «Acción necesaria» y el servicio avisa
    en menos de 1 min.
16. Quita el acceso a notificaciones con la guardia activa → aviso
    «MODO GUARDIA PUEDE NO FUNCIONAR CORRECTAMENTE» en menos de 1 min.
17. Desliza para quitar la notificación «Modo Guardia activo» → vuelve en menos de 1 min.
