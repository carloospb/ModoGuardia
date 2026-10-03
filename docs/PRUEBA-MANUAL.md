# Prueba manual en teléfono real

Hace falta otro teléfono (o WhatsApp Web) para enviar mensajes y llamar.

1. Instala la APK y completa el asistente. En **Estado**, todo 🟢 salvo lo que decidas no corregir.
2. **Estado → Probar con el teléfono bloqueado (15 s)** y bloquea. Debe encenderse la pantalla,
   verse 🚨 ALERTA DE GUARDIA sobre el bloqueo, sonar en bucle y vibrar. Espera más de 1 minuto:
   no se para sola. DETENER ALARMA → silencio, y tu volumen de alarma vuelve a su valor.
3. Crea la regla de grupo `GUARDIAS SOPORTE` (y ese grupo en WhatsApp, sin silenciar).
4. **INICIAR GUARDIA** y bloquea el teléfono.
5. Desde el otro teléfono escribe en el grupo `URGENTE: servidor principal caído.`
   → alarma con Origen WhatsApp, Regla GUARDIAS SOPORTE, el mensaje y la hora.
6. DETENER. Escribe en **otro** grupo → no suena; en Historial, «Notificación ignorada · WhatsApp».
7. Activa **Diagnóstico** y escribe en el grupo: comprueba «Grupo detectado».
8. Reglas → **Cualquier llamada entrante** (concede «Teléfono»). Llama → suena la alarma además
   del timbre. Contesta → la alarma se para y la llamada funciona.
9. Con la guardia activa, **reinicia** y desbloquea → notificación «Modo Guardia activo» y en
   Historial «Guardia restaurada». Repite el paso 5.
10. Activa «No molestar» en silencio total → Estado en 🔴 y aviso del servicio en menos de 1 min.
11. Quita el acceso a notificaciones con la guardia activa → aviso
    «⚠️ MODO GUARDIA PUEDE NO FUNCIONAR CORRECTAMENTE» en menos de 1 min.
