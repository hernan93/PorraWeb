# PorraWeb Verification Report

Estado de verificacion antes de invitar participantes.

## Resumen

La app esta desplegada en produccion y conectada a Supabase real. El flujo publico de grupos, correo de confirmacion, RLS basico, dominio Resend y deploy Vercel estan verificados. Las funciones admin protegidas requieren una sesion real de admin; el login fue confirmado manualmente por el usuario y los endpoints rechazan llamadas sin autorizacion.

## Produccion Verificada

| Item | Resultado |
|---|---|
| URL publica | `https://porraweb.vercel.app` responde `200`. |
| `config.js` | Responde `200` y contiene Supabase URL + publishable key. |
| Bundle frontend | Responde `200`; contiene UI de Bizum y boton de sincronizacion FIFA. |
| Secrets en bundle | No se detectaron `sb_secret`, `SERVICE_ROLE` ni `RESEND_API_KEY`. |
| Vercel build | `BUILD SUCCESSFUL`; warning conocido de bundle `605 KiB`. |

## Supabase y Seguridad

| Prueba | Resultado |
|---|---|
| `teams` publico | `200`. |
| `app_settings` publico | `200`. |
| `participants` publico | `401`, bloqueado por RLS/permisos. |
| `submit-groups` con `{}` | `400`, validacion correcta. |
| `submit-knockouts` con `{}` | `400`, validacion correcta. |
| `approve-participant` sin auth | `401`. |
| `update-settings` sin auth | `401`. |
| `sync-fifa-matches` sin auth | `401`. |

## Email

| Prueba | Resultado |
|---|---|
| Dominio Resend | `porraweb.us` verificado en Resend. |
| DNS DKIM | `resend._domainkey.porraweb.us` resuelve via `1.1.1.1`. |
| DNS SPF | `send.porraweb.us` TXT resuelve via `1.1.1.1`. |
| DNS MX | `send.porraweb.us` apunta a Amazon SES feedback SMTP. |
| `RESEND_FROM_EMAIL` | Configurado en Supabase secrets. |
| Email real grupos | Enviado a `hernancit1993@gmail.com`, `email_logs.status = sent`. |
| Resguardo HTML | Confirmado por el usuario: el correo llega con predicciones ingresadas. |
| Copia admin | Implementada para grupos y eliminatorias mediante `ADMIN_RECEIPT_EMAIL` o fallback `hernancit1993@gmail.com`. |

## Admin y Fases

| Funcion | Estado |
|---|---|
| Login admin | Confirmado manualmente por el usuario. |
| Configuracion de fases | UI usa combos `open/closed`, no texto libre. |
| Bizum | El telefono configurado por admin se muestra al participante en el formulario. |
| Participar | La pestana se muestra solo si `groups_form_status = open`. |
| Eliminatorias | La pestana se muestra solo si `knockouts_form_status = open`. |
| Ambas fases cerradas | La navegacion publica queda reducida a Dashboard/Admin. |
| Sincronizacion FIFA | Admin UI tiene boton `Sincronizar FIFA ahora`; la funcion exige admin JWT o `SYNC_FIFA_SECRET`. |

## Datos De Prueba Dejados En Supabase

Se dejo este registro para prueba manual de admin. No se borro por instruccion del usuario.

| Nombre | Email | Pago | Aprobacion | Proposito |
|---|---|---|---|---|
| Hernan | `hernancit1993@gmailc.com` | `pending_payment` | `pending` | Validar que aparece en Admin > Pagos y que se puede aprobar manualmente. |

Al aprobarlo desde admin debe quedar `approved` y `paid_bizum`. Como el ranking publico incluye todos los aprobados con `COALESCE` de puntajes, debe aparecer con cero puntos si aun no tiene predicciones puntuadas.

## Prueba Manual Recomendada

1. Entrar a `https://porraweb.vercel.app/#/admin/login`.
2. Ir a `Configuracion`.
3. Confirmar `Fase de grupos = Abierta`, `Eliminatorias = Cerrada`.
4. Configurar telefono Bizum real y guardar.
5. Ir a `Pagos`.
6. Aprobar el participante `Hernan <hernancit1993@gmailc.com>`.
7. Ir a `Dashboard` y confirmar que Hernan aparece con cero puntos.
8. Enviar una prediccion real de grupos con un correo controlado.
9. Verificar que llega el correo con el resguardo HTML.
10. En `Resultados`, pulsar `Sincronizar FIFA ahora` y confirmar mensaje de exito.

## Riesgos Pendientes

| Riesgo | Estado |
|---|---|
| Eliminatorias reales | El formulario esta preparado, pero los equipos reales de ronda de 32 dependeran de FIFA cuando se conozcan clasificados. |
| Test completo admin autenticado | Falta ejecutarlo con sesion real desde navegador; no se pidio ni uso password del admin en CLI. |
| Telefono Bizum | No habia `bizum_phone` configurado al verificar; debe guardarse desde admin antes de invitar amigos. |
| Limpieza de datos QA | Pendiente hasta que el usuario confirme que quiere limpiar Supabase. |
