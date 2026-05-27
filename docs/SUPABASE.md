# Supabase Setup

La app ya tiene una migracion inicial para crear las tablas del MVP. Todavia no conecte el frontend a Supabase; primero puedes probar las vistas localmente.

## Camino rapido

1. Inicia sesion en Supabase CLI:

```powershell
supabase login
```

2. Vincula este repo con tu proyecto Supabase:

```powershell
supabase link --project-ref TU_PROJECT_REF
```

3. Crea las tablas en Supabase:

```powershell
supabase db push
```

## Donde encontrar el Project Ref

En Supabase Dashboard entra a tu proyecto y revisa la URL:

```text
https://supabase.com/dashboard/project/TU_PROJECT_REF
```

## Variables que usaremos despues

Cuando conectemos la web necesitaremos estas variables en Vercel:

| Variable | Uso |
|---|---|
| `SUPABASE_URL` | URL publica del proyecto Supabase |
| `SUPABASE_ANON_KEY` | Clave publica anon para lecturas controladas |
| `RESEND_API_KEY` | Enviar emails desde Edge Functions |

No pongas estas claves dentro del codigo fuente.

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

## Siguiente paso backend

La forma mas segura sera guardar formularios con Supabase Edge Functions. Asi validamos duplicados, aprobaciones y emails sin exponer la clave de Resend en el navegador.
