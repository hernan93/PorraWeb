# Supabase Setup

La app ya tiene migraciones para crear las tablas del MVP y sincronizar el calendario oficial del Mundial 2026 desde la API publica de FIFA.

## Camino rapido

1. Inicia sesion en Supabase CLI:

```powershell
supabase login
```

2. Vincula este repo con tu proyecto Supabase:

```powershell
supabase link --project-ref TU_PROJECT_REF
```

3. Crea o actualiza las tablas en Supabase:

```powershell
supabase db push
```

4. Despliega la funcion que sincroniza FIFA:

```powershell
supabase functions deploy sync-fifa-matches
```

5. Ejecuta la sincronizacion con un `POST` a la funcion desplegada usando un JWT valido del proyecto.

La funcion activa es:

```text
https://xkdggawpuldcdfweanzy.supabase.co/functions/v1/sync-fifa-matches
```

## Donde encontrar el Project Ref

En Supabase Dashboard entra a tu proyecto y revisa la URL:

```text
https://supabase.com/dashboard/project/TU_PROJECT_REF
```

## Variables que usaremos despues

Cuando conectemos la web necesitaremos separar variables publicas de secretos. Esta separacion es importante: Vercel entrega variables al build/frontend; Supabase Edge Functions ejecuta secretos de backend.

| Variable | Uso |
|---|---|
| `SUPABASE_URL` | Vercel y Supabase Edge Functions. Es publica. |
| `SUPABASE_PUBLISHABLE_KEY` | Vercel/frontend. Es publica y solo puede leer lo permitido por RLS/policies. |
| `SUPABASE_SECRET_KEYS` | Supabase Edge Functions. Nunca va al navegador. |
| `SUPABASE_SERVICE_ROLE_KEY` | Legacy fallback de Edge Functions. No usar en frontend. |
| `RESEND_API_KEY` | Solo Supabase Edge Functions para enviar emails. |
| `RESEND_FROM_EMAIL` | Opcional. Remitente verificado en Resend; si falta se usa `PorraWeb <onboarding@resend.dev>` para pruebas. |
| `SYNC_FIFA_SECRET` | Opcional en Supabase Edge Functions si se programa una llamada externa sin JWT. |

No pongas estas claves dentro del codigo fuente. No pongas `SUPABASE_SECRET_KEYS`, `SUPABASE_SERVICE_ROLE_KEY`, `RESEND_API_KEY` ni `RESEND_FROM_EMAIL` en variables expuestas al frontend.

El frontend genera `config.js` en build con `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY`. Si faltan esas variables, la app usa el repositorio mock.

## Tablas creadas

- `participants`
- `teams`
- `tournament_groups`
- `group_teams`
- `matches`
- `match_results`
- `submissions`
- `match_predictions`
- `group_position_predictions`
- `third_place_qualifier_predictions`
- `knockout_predictions`
- `scores`
- `email_logs`
- `app_settings`
- `admin_users`

## Tablas y campos para FIFA

La migracion `0002_fifa_sync_support.sql` agrega soporte para sincronizar datos oficiales:

- `venues` guarda estadios y ciudades.
- `fifa_raw_payloads` guarda payloads completos para auditoria.
- `fifa_sync_logs` registra ejecuciones, conteos y errores.
- `score_events` prepara auditoria para el calculo automatico de puntos.
- `teams`, `tournament_groups`, `matches` y `match_results` tienen campos `fifa_*` para trazar el origen.

La migracion `0004_public_read_access.sql` habilita RLS y deja publico solo lo seguro:

- Tablas publicas de lectura: `teams`, `tournament_groups`, `group_teams`, `matches`, `match_results`, `venues`, `app_settings`.
- Vistas publicas: `public_dashboard_summary`, `public_ranking`, `public_latest_results`.
- Tablas sensibles bloqueadas al navegador: `participants`, `submissions`, predicciones, `scores`, `email_logs`, `admin_users`, payloads/logs internos.

## Sincronizacion FIFA

Fuente canonica usada:

```text
https://api.fifa.com/api/v3/calendar/matches?language=en&count=104&idCompetition=17&idSeason=285023
```

Verifica el estado de la migracion:

```powershell
supabase migration list
```

Verifica los datos sincronizados:

```powershell
supabase db query --linked "select (select count(*) from teams) as teams, (select count(*) from tournament_groups) as groups, (select count(*) from venues) as venues, (select count(*) from matches) as matches, (select count(*) from fifa_raw_payloads) as raw_payloads, (select count(*) from fifa_sync_logs where status = 'success') as successful_syncs;"
```

Resultado esperado despues de la primera sincronizacion:

| Tabla | Filas esperadas |
|---|---:|
| `teams` | 48 |
| `tournament_groups` | 12 |
| `venues` | 16 |
| `matches` | 104 |
| `fifa_raw_payloads` | 1 o mas |
| `fifa_sync_logs` exitosos | 1 o mas |

## Recalculo de puntajes

La migracion `0003_score_recalculation.sql` crea la funcion SQL `recalculate_scores(source)`.

La funcion recalcula desde cero:

- `score_events`: detalle actual de puntos por regla.
- `scores`: totales por participante para `groups`, `knockouts` y `total`.

La sincronizacion FIFA llama automaticamente a `recalculate_scores('fifa_sync')` cuando hay resultados para guardar. Tambien puedes ejecutarla manualmente:

```powershell
supabase db query --linked "select recalculate_scores('manual_recalc') as result;"
```

El ranking solo calcula participantes con `approval_status = 'approved'`.

## Frontend con Supabase

La app KMP Web intenta leer Supabase si existe `window.PORRAWEB_CONFIG` con URL y publishable key. En Vercel se genera desde variables de entorno durante el build.

Variables requeridas en Vercel:

```text
SUPABASE_URL=https://xkdggawpuldcdfweanzy.supabase.co
SUPABASE_PUBLISHABLE_KEY=<publishable key>
```

Verificacion rapida de acceso publico:

```powershell
vercel env ls
```

Debe mostrar `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY` en Production.

## Formularios y emails

Los formularios se guardan con Supabase Edge Functions. Asi validamos duplicados, aprobaciones y emails sin exponer la clave de Resend en el navegador.

Las funciones `submit-groups` y `submit-knockouts` envian un email de confirmacion con Resend despues de guardar la prediccion. Si Resend falla, la prediccion no se pierde: se guarda un registro en `email_logs` con estado `failed` para revisar el problema.

Para produccion, configura `RESEND_FROM_EMAIL` con un remitente de dominio verificado. El remitente `onboarding@resend.dev` sirve solo para pruebas controladas.
