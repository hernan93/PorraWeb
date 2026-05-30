# PorraWeb

App web para gestionar una porra privada del Mundial 2026 entre compañeros de equipo.

## Stack actual

- Kotlin Multiplatform Web para el frontend.
- Supabase para base de datos, autenticación del admin y funciones backend.
- Resend para emails de confirmación.
- Cloudflare Registrar/DNS para el dominio de envío `porraweb.us` usado por Resend.
- Vercel para deploy.

## Produccion

| Servicio | Uso |
|---|---|
| Vercel | Hosting de la SPA KMP Web en `https://porraweb.vercel.app`. |
| Supabase | Postgres, RLS, Supabase Auth para admin y Edge Functions. |
| Resend | Envio de correos de confirmacion con resguardo HTML de predicciones. |
| Cloudflare | Registro/DNS de `porraweb.us` para verificar DKIM/SPF/MX de Resend. |
| FIFA API | Fuente canonica de calendario, equipos oficiales y resultados. |

Variables publicas en Vercel:

```text
SUPABASE_URL
SUPABASE_PUBLISHABLE_KEY
```

Secrets de Supabase Edge Functions:

```text
RESEND_API_KEY
RESEND_FROM_EMAIL
SUPABASE_SECRET_KEYS
SUPABASE_SERVICE_ROLE_KEY
```

No pongas secrets en Vercel ni en el frontend. El navegador solo debe conocer la URL de Supabase y la publishable key.

## Estructura del frontend

El frontend esta separado por capas simples para que no quede todo en `Main.kt`:

- `domain/model` contiene los modelos de negocio usados por la UI.
- `domain/repository` define el contrato de datos.
- `data/mock` contiene datos de prueba en espanol para navegar sin backend.
- `navigation` contiene las rutas hash de la SPA.
- `presentation/app` conecta navegacion, estado y pantallas.
- `presentation/components` contiene componentes reutilizables.
- `presentation/screens` contiene las vistas principales.

La app carga `app_settings` desde Supabase para controlar fases:

- `groups_form_status = open`: muestra la pestana `Participar` y habilita formulario de grupos.
- `groups_form_status = closed`: oculta `Participar` y bloquea el envio de grupos.
- `knockouts_form_status = open`: muestra `Eliminatorias` y habilita formulario de eliminatorias.
- `knockouts_form_status = closed`: oculta `Eliminatorias` y bloquea el envio de eliminatorias.
- Si ambas fases estan cerradas, el flujo publico queda reducido a dashboard/admin.

Los formularios mantienen el borrador en memoria mientras el usuario cambia de pestana/ruta dentro de la SPA.

## Datos mock

Los mocks usan grupos/equipos 2026 como referencia para entender el flujo antes de conectar Supabase.
La fase de grupos genera los 6 cruces de cada grupo, permite elegir el orden final con selectores y agrega la selección de los 8 mejores terceros.
La vista de eliminatorias usa cruces mock desde ronda de 32 y selectores de equipos para que luego podamos comparar por `team_id`.

## Desarrollo local

Ejecuta la app web en modo desarrollo:

```powershell
.\gradlew.bat jsBrowserRun
```

Genera el build de producción:

```powershell
.\gradlew.bat jsBrowserProductionWebpack
```

La salida de producción queda en:

```text
build/kotlin-webpack/js/productionExecutable
```

## Vistas disponibles localmente

La app usa rutas hash para funcionar como SPA sin configurar servidor:

- `#/` reglas principales y llamada a participar.
- `#/predicciones/grupos` formulario de fase de grupos en modo maqueta.
- `#/predicciones/eliminatorias` formulario de eliminatorias en modo maqueta.
- `#/dashboard` ranking y resultados en modo maqueta.
- `#/admin/login` acceso admin en modo maqueta.
- `#/admin` panel admin.
- `#/admin/participantes` aprobacion de pagos.
- `#/admin/resultados` carga de resultados.
- `#/admin/configuracion` configuracion de fases.

## Flujo administrador

1. Entrar en `#/admin/login` con Supabase Auth.
2. Ir a `Configuracion`.
3. Configurar fase de grupos/eliminatorias con los combos.
4. Configurar importe y telefono Bizum. Ese telefono se muestra al participante antes del envio.
5. Ir a `Pagos` para aprobar participantes cuando el Bizum este recibido.
6. Ir a `Resultados` y usar `Sincronizar FIFA ahora` para traer calendario/resultados oficiales.

La aprobacion marca al participante como `approved` y, si estaba pendiente, cambia el pago a `paid_bizum`.

## Supabase

Las migraciones estan en `supabase/migrations`:

- `0001_initial_schema.sql` crea las tablas base del MVP.
- `0002_fifa_sync_support.sql` agrega soporte para sincronizar calendario/resultados desde FIFA.
- `0003_score_recalculation.sql` agrega el recalculo automatico de puntajes.
- `0004_public_read_access.sql` habilita RLS y vistas publicas seguras para el frontend.

La Edge Function `supabase/functions/sync-fifa-matches` carga 104 partidos del Mundial 2026 desde la API publica de FIFA, guarda payloads/logs para auditoria y recalcula puntajes cuando hay resultados oficiales.

Endpoint FIFA usado:

```text
https://api.fifa.com/api/v3/calendar/matches?language=en&count=104&idCompetition=17&idSeason=285023
```

Funciones desplegadas:

- `sync-fifa-matches`: sincroniza FIFA y recalcula puntajes si hay resultados.
- `submit-groups`: registra solicitudes/predicciones de grupos y envia email de resguardo.
- `submit-knockouts`: registra predicciones de eliminatorias y envia email de resguardo.
- `approve-participant`: aprueba/rechaza participantes desde admin.
- `update-settings`: guarda fases, importe y Bizum desde admin.

El frontend lee Supabase si Vercel tiene `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY`. Si faltan, usa datos mock.

La guia para vincular el proyecto, aplicar migraciones y verificar la sincronizacion esta en `docs/SUPABASE.md`.

## Plan del MVP

- Los participantes no tienen login: completan nombre, correo y predicciones.
- El admin tiene login y aprueba pagos de 5 EUR por Bizum o efectivo.
- Hay dos formularios: fase de grupos y eliminatorias.
- El dashboard general muestra resultados, puntos y ranking.
- Firebase queda fuera del MVP para mantener el proyecto simple.
- Cloudflare solo se usa como registrador/DNS de `porraweb.us`; no aloja la aplicacion.
- La API publica de FIFA es la fuente canonica para calendario y resultados oficiales.

## Verificacion

El ultimo informe de pruebas esta en `docs/VERIFICATION_REPORT.md`.

## Reglas 2026

El Mundial 2026 cambia el flujo respecto a Qatar 2022:

- Hay 48 equipos.
- Hay 12 grupos de 4 equipos.
- Cada equipo juega 3 partidos de fase de grupos.
- Clasifican a eliminatorias 32 equipos: los 2 primeros de cada grupo y los 8 mejores terceros.
- La fase final empieza en ronda de 32, luego octavos, cuartos, semifinales, tercer puesto y final.

## Puntuación del MVP

Fase de grupos:

- Resultado correcto por partido: +1 punto.
- Marcador exacto por partido: +2 puntos adicionales.
- Posición exacta de un equipo en su grupo: +2 puntos.
- Orden completo de un grupo perfecto: +2 puntos extra.
- Equipo clasificado a ronda de 32: +1 punto, incluyendo primeros, segundos y mejores terceros.

Eliminatorias:

- Resultado correcto por partido: +3 puntos.
- Marcador exacto por partido: 5 puntos en total.
- Clasificado desde ronda de 32: +4 puntos por equipo.
- Clasificado a cuartos: +5 puntos por equipo.
- Clasificado a semifinales: +6 puntos por equipo.
- Clasificado a final o tercer puesto: +7 puntos por equipo.
- 4o puesto: +8 puntos.
- 3er puesto: +9 puntos.
- Subcampeón: +10 puntos.
- Campeón: +20 puntos.

Los cruces de eliminatorias en el mock son solo una maqueta. En la app real, el admin cargará los 32 clasificados oficiales y la app generará el formulario desde esos datos.
