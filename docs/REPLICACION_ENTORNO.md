# Replicacion de Entorno PorraWeb

Esta guia permite crear una copia independiente de PorraWeb para otro grupo de amigos sin desarrollar soporte multi-porra. La copia usa el mismo repositorio Git, pero tiene su propio Vercel, Supabase, base de datos, admin, configuracion y subdominio.

## Resultado Esperado

| Entorno | Web | Backend |
|---|---|---|
| Porra actual | `https://porraweb.us` | Supabase actual |
| Nueva copia | `https://porrauno.porraweb.us` | Nuevo proyecto Supabase |

El dominio principal queda sin cambios. El nuevo entorno vive en un subdominio.

## Camino Rapido

1. Crear un nuevo proyecto Supabase.
2. Crear un nuevo proyecto Vercel desde el mismo repo Git.
3. Configurar `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY` en Vercel.
4. Aplicar migraciones al nuevo Supabase.
5. Desplegar Edge Functions al nuevo Supabase.
6. Configurar secrets de Supabase Edge Functions.
7. Crear usuario admin en Supabase Auth.
8. Insertar ese usuario en `admin_users`.
9. Configurar `porrauno.porraweb.us` en Vercel y Cloudflare.
10. Probar formulario publico, login admin, dashboard y emails.

## Cuentas Necesarias

| Cuenta | Uso |
|---|---|
| GitHub | Repositorio fuente. Puede ser el mismo repo actual. |
| Vercel | Hosting del frontend para la copia. |
| Supabase | Base de datos, Auth y Edge Functions de la copia. |
| Cloudflare | DNS del subdominio. |
| Resend | Envio de emails. Puede reutilizar el dominio `porraweb.us`. |

## Datos Que Debes Guardar

No guardes secrets en el repo. Guarda estos valores en un gestor seguro.

| Dato | Donde sale | Sensible |
|---|---|---|
| `NUEVO_PROJECT_REF` | Supabase Dashboard URL | No |
| `NUEVO_SUPABASE_URL` | Supabase Project Settings, API | No |
| `NUEVA_SUPABASE_PUBLISHABLE_KEY` | Supabase Project Settings, API | No |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase Project Settings, API | Si |
| `RESEND_API_KEY` | Resend Dashboard | Si |
| `RESEND_FROM_EMAIL` | Resend verified domain | No |
| `ADMIN_EMAIL` | Email del admin nuevo | No |

## 1. Crear Proyecto Supabase

En Supabase Dashboard:

1. Crear un proyecto nuevo.
2. Elegir una region cercana a los usuarios.
3. Guardar el `Project Ref`.
4. Entrar en `Project Settings > API`.
5. Copiar `Project URL`.
6. Copiar la publishable key.
7. Copiar la service role key y guardarla como secreto.

Ejemplo de placeholders:

```text
NUEVO_PROJECT_REF=xxxxxxxxxxxxxxxxxxxx
NUEVO_SUPABASE_URL=https://xxxxxxxxxxxxxxxxxxxx.supabase.co
NUEVA_SUPABASE_PUBLISHABLE_KEY=sb_publishable_xxx
```

## 2. Vincular El Repo Al Nuevo Supabase

Desde el repo local:

```powershell
supabase login
supabase link --project-ref NUEVO_PROJECT_REF
```

Verifica que estas apuntando al proyecto nuevo antes de aplicar cambios:

```powershell
Get-Content supabase/.temp/project-ref
supabase migration list --linked
```

El valor de `supabase/.temp/project-ref` debe coincidir con `NUEVO_PROJECT_REF`.

## 3. Aplicar Migraciones

Aplica el esquema actual al nuevo Supabase:

```powershell
supabase db push
```

Verifica tablas principales:

```powershell
supabase db query --linked "select (select count(*) from admin_users) as admin_users, (select count(*) from participants) as participants, (select count(*) from submissions) as submissions;"
```

Resultado inicial esperado:

| Campo | Valor esperado |
|---|---:|
| `admin_users` | `0` |
| `participants` | `0` |
| `submissions` | `0` |

## 4. Configurar Secrets De Supabase Functions

En Supabase Dashboard o CLI, configurar los secrets del nuevo proyecto.

Variables necesarias:

```text
SUPABASE_URL=NUEVO_SUPABASE_URL
SUPABASE_SERVICE_ROLE_KEY=<service-role-key-del-nuevo-proyecto>
RESEND_API_KEY=<resend-api-key>
RESEND_FROM_EMAIL=PorraWeb <no-reply@porraweb.us>
ADMIN_RECEIPT_EMAIL=<correo-admin-que-recibe-copia>
```

Si usas CLI, no pegues estos valores en archivos del repo. Ejecuta el comando localmente:

```powershell
supabase secrets set SUPABASE_URL="NUEVO_SUPABASE_URL"
supabase secrets set SUPABASE_SERVICE_ROLE_KEY="<service-role-key-del-nuevo-proyecto>"
supabase secrets set RESEND_API_KEY="<resend-api-key>"
supabase secrets set RESEND_FROM_EMAIL="PorraWeb <no-reply@porraweb.us>"
supabase secrets set ADMIN_RECEIPT_EMAIL="<correo-admin>"
```

Si el proyecto usa `SUPABASE_SECRET_KEYS`, configurarlo con el formato esperado por las functions:

```powershell
supabase secrets set SUPABASE_SECRET_KEYS='{"default":"<service-role-key-del-nuevo-proyecto>"}'
```

## 5. Desplegar Edge Functions

Las funciones publicas de envio deben desplegarse sin verificacion JWT porque los participantes no tienen login.

```powershell
supabase functions deploy submit-groups --no-verify-jwt
supabase functions deploy submit-knockouts --no-verify-jwt
```

Las funciones admin reciben el JWT del admin desde el frontend y ademas validan permisos internamente:

```powershell
supabase functions deploy approve-participant
supabase functions deploy update-settings
supabase functions deploy sync-fifa-matches
```

Si alguna llamada admin devuelve `401` desde el gateway antes de entrar a la funcion, desplegar esa funcion tambien con `--no-verify-jwt` y mantener la validacion interna de admin.

## 6. Sincronizar Datos FIFA

Entrar luego al panel admin y usar `Sincronizar FIFA ahora`.

Tambien puedes validar los conteos con SQL:

```powershell
supabase db query --linked "select (select count(*) from teams) as teams, (select count(*) from tournament_groups) as groups, (select count(*) from matches) as matches;"
```

Resultado esperado despues de sincronizar:

| Tabla | Filas esperadas |
|---|---:|
| `teams` | `48` |
| `tournament_groups` | `12` |
| `matches` | `104` |

## 7. Crear Admin En Supabase Auth

En Supabase Dashboard del nuevo proyecto:

1. Ir a `Authentication > Users`.
2. Crear usuario con el email del admin.
3. Definir contrasena temporal o enviar invitacion.
4. Copiar el `User UID`.

Despues insertar el usuario en `admin_users`:

```powershell
supabase db query --linked "insert into admin_users (user_id, email) values ('USER_UID', 'ADMIN_EMAIL') on conflict (user_id) do nothing;"
```

Verificar:

```powershell
supabase db query --linked "select user_id, email, created_at from admin_users;"
```

## 8. Crear Proyecto Vercel

En Vercel Dashboard:

1. Crear un nuevo proyecto.
2. Importar el mismo repo GitHub.
3. Usar la misma rama que produccion, normalmente `main`.
4. Revisar que tome el `vercel.json` del repo.

Configurar variables de entorno en Production:

```text
SUPABASE_URL=NUEVO_SUPABASE_URL
SUPABASE_PUBLISHABLE_KEY=NUEVA_SUPABASE_PUBLISHABLE_KEY
```

No configures `SUPABASE_SERVICE_ROLE_KEY`, `RESEND_API_KEY` ni otros secrets backend en Vercel. Esos van en Supabase Edge Functions.

## 9. Configurar Subdominio

En Vercel, agregar dominio al nuevo proyecto:

```text
porrauno.porraweb.us
```

En Cloudflare DNS, crear registro:

| Tipo | Nombre | Valor | Proxy |
|---|---|---|---|
| `CNAME` | `porrauno` | `cname.vercel-dns.com` | DNS only recomendado |

Esperar a que Vercel emita el certificado HTTPS.

El dominio principal `porraweb.us` no se toca.

## 10. Configurar Resend

Para mantenerlo gratis y simple, reutilizar el remitente verificado:

```text
PorraWeb <no-reply@porraweb.us>
```

No hace falta verificar `porrauno.porraweb.us` en Resend si no vas a enviar desde `no-reply@porrauno.porraweb.us`.
La misma `RESEND_API_KEY` puede usarse en el nuevo Supabase si queres que ambos entornos envien desde el mismo dominio verificado.

Limites relevantes de Resend Free:

| Limite | Valor |
|---|---:|
| Emails por mes | `3000` |
| Emails por dia | `100` |
| Dominios | `1` |

## 11. Deploy Del Frontend

Desde Vercel Dashboard, lanzar deploy de produccion.

O desde CLI si estas vinculado al proyecto correcto:

```powershell
vercel link
vercel --prod --yes
```

Con dos proyectos Vercel usando el mismo repo, revisa bien que `vercel link` apunte al proyecto nuevo antes de desplegar.

Verificar aliases:

```powershell
vercel alias ls
```

Debe aparecer:

```text
porrauno.porraweb.us
```

## 12. Configuracion Inicial Desde Admin

Entrar en:

```text
https://porrauno.porraweb.us/#/admin/login
```

Configurar:

| Setting | Valor inicial sugerido |
|---|---|
| Fase de grupos | `open` cuando quieras recibir predicciones |
| Eliminatorias | `closed` al inicio |
| Precio | `5` o el importe del grupo |
| Bizum | telefono del admin de esa porra |
| Deadline | fecha limite informativa |

Despues ir a `Resultados` y ejecutar `Sincronizar FIFA ahora`.

## 13. Smoke Test

Antes de compartir el link:

1. Abrir `https://porrauno.porraweb.us`.
2. Verificar que dashboard carga sin datos mock.
3. Enviar una prediccion de prueba con email controlado.
4. Verificar que el participante queda pendiente.
5. Entrar como admin.
6. Aprobar o rechazar el participante de prueba.
7. Verificar que llega email de confirmacion o queda registro en `email_logs`.
8. Limpiar el participante de prueba si no queres que cuente.

Consultas utiles:

```powershell
supabase db query --linked "select count(*) from participants;"
supabase db query --linked "select count(*) from submissions;"
supabase db query --linked "select status, count(*) from email_logs group by status order by status;"
```

## 14. Mantener Supabase Free Activo

Supabase Free puede pausar proyectos tras una semana sin actividad.

Rutina manual recomendada:

1. Entrar dos veces por semana a `https://porrauno.porraweb.us/#/dashboard`.
2. Entrar al admin si queres forzar Auth y lecturas privadas.
3. Antes de compartir el link, probar dashboard y admin.

Esto no es una garantia contractual, pero reduce el riesgo de pausa en el plan Free.

## 15. Limites Del Enfoque Copia

Este enfoque es rapido porque evita desarrollar multi-porra, pero duplica operacion.

| Ventaja | Coste |
|---|---|
| No requiere cambios de arquitectura | Cada grupo tiene Supabase/Vercel propio |
| Aislamiento fuerte de datos | Hay que desplegar y configurar cada copia |
| Admins separados por entorno | Las mejoras futuras deben desplegarse en cada entorno |
| Puede mantenerse gratis para una copia extra | Supabase Free limita proyectos activos |

## 16. Checklist Final

- [ ] Nuevo Supabase creado.
- [ ] Repo vinculado al nuevo `Project Ref`.
- [ ] Migraciones aplicadas.
- [ ] Edge Functions desplegadas.
- [ ] Secrets configurados en Supabase.
- [ ] FIFA sincronizado.
- [ ] Admin creado en Supabase Auth.
- [ ] Admin insertado en `admin_users`.
- [ ] Nuevo Vercel project creado desde el mismo repo.
- [ ] `SUPABASE_URL` configurada en Vercel.
- [ ] `SUPABASE_PUBLISHABLE_KEY` configurada en Vercel.
- [ ] Subdominio agregado en Vercel.
- [ ] CNAME agregado en Cloudflare.
- [ ] HTTPS activo.
- [ ] Login admin probado.
- [ ] Envio publico probado.
- [ ] Email probado o `email_logs` revisado.
- [ ] Datos de prueba limpiados.
- [ ] Link listo para compartir.
