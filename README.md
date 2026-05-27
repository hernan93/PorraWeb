# PorraWeb

App web para gestionar una porra privada del Mundial 2026 entre compañeros de equipo.

## Stack actual

- Kotlin Multiplatform Web para el frontend.
- Supabase para base de datos, autenticación del admin y funciones backend.
- Resend para emails de confirmación.
- Vercel para deploy.

## Estructura del frontend

El frontend esta separado por capas simples para que no quede todo en `Main.kt`:

- `domain/model` contiene los modelos de negocio usados por la UI.
- `domain/repository` define el contrato de datos.
- `data/mock` contiene datos de prueba en espanol para navegar sin backend.
- `navigation` contiene las rutas hash de la SPA.
- `presentation/app` conecta navegacion, estado y pantallas.
- `presentation/components` contiene componentes reutilizables.
- `presentation/screens` contiene las vistas principales.

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

## Supabase

La migracion inicial esta en `supabase/migrations/0001_initial_schema.sql`.
La guia para vincular el proyecto y crear tablas esta en `docs/SUPABASE.md`.

## Plan del MVP

- Los participantes no tienen login: completan nombre, correo y predicciones.
- El admin tiene login y aprueba pagos de 5 EUR por Bizum o efectivo.
- Hay dos formularios: fase de grupos y eliminatorias.
- El dashboard general muestra resultados, puntos y ranking.
- Firebase y Cloudflare quedan fuera del MVP para mantener el proyecto simple.

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
