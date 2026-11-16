# home-inspect

![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Bootstrap 5.3.9](https://img.shields.io/badge/Bootstrap-5.3.9-7952B3?logo=bootstrap&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
[![License](https://img.shields.io/github/license/mgarciat12/home-inspect)](LICENSE)

Aplicación web para rellenar **informes de inspección de viviendas** desde el móvil, con fotos y
exportación a PDF. La estructura del informe sigue las áreas del *Home Inspection Standard of Practice*
(tejado, exterior, estructura, calefacción, climatización, fontanería, electricidad, chimeneas,
ático, interior y garaje) y está definida como datos, no en el código.

**Stack:** Java 25 · Spring Boot 4.1 · Spring Security 7 (Authorization Server + OAuth2 Client) ·
Thymeleaf + Bootstrap 5.3.9 (WebJars, sin CDN) · Spring Data JPA · Flyway · PostgreSQL ·
Docker Compose · Testcontainers

## Contenido

- [Estado del proyecto](#estado-del-proyecto)
- [Funcionalidades](#funcionalidades)
- [Arquitectura](#arquitectura)
- [Requisitos](#requisitos)
- [Arrancar en local](#arrancar-en-local)
- [Desplegar en el servidor](#desplegar-en-el-servidor)
- [Variables de entorno](#variables-de-entorno)
- [Primer acceso del administrador](#primer-acceso-del-administrador)
- [Backup y restauración](#backup-y-restauración)
- [Personalizar el catálogo del informe](#personalizar-el-catálogo-del-informe)
- [Desarrollo](#desarrollo)
- [Licencia](#licencia)

## Estado del proyecto

El desarrollo va por fases:

| Fase | Contenido | Estado |
|------|-----------|--------|
| 1 | Esqueleto multi-módulo, Docker Compose, Flyway, layout Bootstrap | ✅ |
| 2 | `auth-server`: OAuth2/OIDC, usuarios en BD, login propio, claves persistentes, roles, API de usuarios | ✅ |
| 3 | `app` como cliente OAuth2: login, logout, roles y panel de administración de usuarios | ✅ |
| 4 | Modelo del informe, catálogo del SOP y editor con autoguardado | ✅ |
| 5 | Subida y gestión de fotos | ⏳ en curso |
| 6 | Resumen automático de hallazgos y vista previa | ⏳ |
| 7 | Exportación a PDF | ⏳ |
| 8 | Ajustes de empresa, biblioteca de comentarios editable, MFA opcional, repaso de seguridad | ⏳ |

## Funcionalidades

**Acceso y usuarios**
- Servidor de autorización propio (OAuth 2.1 + OpenID Connect) con página de login en español.
- La aplicación entra con *Authorization Code + PKCE*; el logout cierra la sesión en los dos servidores.
- Roles `ADMIN` e `INSPECTOR` en el token. Un inspector solo ve y edita sus propios informes
  (comprobado en la capa de servicio).
- Bloqueo temporal tras 5 intentos fallidos. Contraseñas temporales con cambio obligatorio.
- Panel de administración: crear, editar, activar/desactivar usuarios, cambiar rol y resetear contraseña.
  Desactivar a un usuario o cambiar su rol cierra su sesión abierta.
- Clientes, autorizaciones y clave de firma persistentes: las sesiones sobreviven a reinicios.

**Informes**
- Numeración anual automática (`2026-0001`), cabecera con cliente, inmueble y condiciones.
- 11 secciones y 59 elementos. Cada elemento tiene descripción/material, estado, valoración,
  limitaciones, observaciones y recomendación, más una biblioteca de comentarios que se insertan con un clic.
- Editor *mobile-first* con progreso por sección y **autoguardado por elemento**: si se cae la conexión,
  los cambios se guardan en el navegador y se reenvían solos.
- Estados `Borrador` → `Finalizado`: un informe finalizado queda bloqueado y solo un administrador puede reabrirlo.
- Listado con buscador (dirección, cliente o nº), filtros por estado y fecha, y paginación.
- Duplicar un informe para usarlo como plantilla.
- Modo claro, oscuro o automático.

## Arquitectura

```
                 navegador (móvil / escritorio)
                    │                     │
        https://inspect.midominio         https://auth.midominio
                    │                     │
            ┌───────▼───────┐  OIDC  ┌────▼─────────┐
            │      app      │───────▶│  auth-server │
            │   (puerto     │  API   │  (puerto     │
            │    8080)      │───────▶│   9000)      │
            └───────┬───────┘ users  └────┬─────────┘
                    │                     │
             BD homeinspect_app    BD homeinspect_auth
                    └──────┬──────────────┘
                     tu PostgreSQL
```

Proyecto Maven multi-módulo:

| Módulo | Paquete | Qué hace |
|--------|---------|----------|
| `auth-server` | `com.homeinspect.auth` | Spring Authorization Server: login, usuarios (esquema `auth`), clientes OAuth2, claves JWT, API `/api/users` |
| `app` | `com.homeinspect.app` | Aplicación de informes: cliente OIDC, perfiles locales, catálogo, informes, editor, panel de admin |

Dentro de `app` el código se organiza por funcionalidad (`report`, `catalog`, `user`, `admin`,
`security`, `config`) y por capas: `controller` → `service` → `repository`. Las vistas reciben DTOs,
nunca entidades JPA.

**Clientes OAuth2 que registra el `auth-server` al arrancar**

| Cliente | Grant | Uso |
|---------|-------|-----|
| `home-inspect-web` | `authorization_code` + `refresh_token`, PKCE obligatorio, sin consentimiento | Login de los usuarios en la app |
| `home-inspect-admin-api` | `client_credentials`, scope `users.admin` | La app gestiona usuarios a través de `/api/users` |

## Requisitos

- **Docker** con el plugin **Compose**. No hace falta tener Java ni Maven instalados:
  todo se compila dentro de contenedores con JDK 25.
- **PostgreSQL** ya existente (13 o superior) para producción. Para desarrollo se usa uno temporal.
- Un **proxy inverso con HTTPS** (Caddy, Nginx, Traefik…) para producción.

## Arrancar en local

`docker-compose.dev.yml` añade un PostgreSQL temporal con las dos bases de datos. Usa otro nombre
de proyecto (`home-inspect-dev`), así que no toca nada de producción.

1. El navegador y el contenedor `app` deben ver el `auth-server` en la **misma URL**. En local se usa
   `host.docker.internal`. Añádelo una vez a `/etc/hosts` de tu equipo:

   ```bash
   echo "127.0.0.1 host.docker.internal" | sudo tee -a /etc/hosts
   ```

2. Crea el `.env`:

   ```bash
   cp .env.example .env
   ```

   y cambia estos valores para local:

   ```dotenv
   AUTH_ISSUER_URI=http://host.docker.internal:9000
   APP_BASE_URL=http://localhost:8080
   COOKIE_SECURE=false
   ```

3. Arranca:

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
   docker compose -f docker-compose.yml -f docker-compose.dev.yml ps    # espera a "healthy"
   ```

4. Abre <http://localhost:8080> y entra con `AUTH_ADMIN_USERNAME` / `AUTH_ADMIN_PASSWORD`
   (ver [primer acceso](#primer-acceso-del-administrador)).

Para pararlo: `docker compose -f docker-compose.yml -f docker-compose.dev.yml down`
(añade `-v` para borrar también la base de datos de pruebas).

## Desplegar en el servidor

### 1. Preparar PostgreSQL (una sola vez)

`db/init.sql` crea las dos bases de datos y sus usuarios con permisos mínimos: cada servicio es
dueño solo de su base. Ejecútalo como superusuario:

```bash
psql -h <host-postgres> -U postgres \
     -v auth_password='<AUTH_DB_PASSWORD>' -v app_password='<APP_DB_PASSWORD>' \
     -f db/init.sql
```

Opcionalmente puedes cambiar los nombres con `-v auth_db=… -v auth_user=… -v app_db=… -v app_user=…`.
Flyway crea las tablas al arrancar cada servicio.

### 2. Configurar `.env`

```bash
git clone https://github.com/mgarciat12/home-inspect.git
cd home-inspect
cp .env.example .env
```

Genera los secretos con `openssl rand -base64 32` y rellena el `.env`
(ver [variables](#variables-de-entorno)). **Nunca subas el `.env` al repositorio.**

¿Dónde está tu PostgreSQL?

- **En el propio host de Docker:** `DB_HOST=host.docker.internal`. Los servicios ya incluyen
  `extra_hosts: host-gateway`. Comprueba que Postgres escucha en la IP del puente de Docker
  (`listen_addresses`) y que `pg_hba.conf` permite esa red.
- **En otro contenedor:** une los servicios a su red con `docker-compose.dbnet.yml`:

  ```dotenv
  DB_HOST=nombre-del-contenedor-postgres
  DB_NETWORK=nombre-de-su-red          # docker network ls
  COMPOSE_FILE=docker-compose.yml:docker-compose.dbnet.yml
  ```

### 3. El issuer y el proxy inverso

El `issuer` del token tiene que coincidir **exactamente** con la URL que ven el navegador y el
contenedor `app`. Usa dos subdominios, uno por servicio, detrás del proxy:

```dotenv
AUTH_ISSUER_URI=https://auth.midominio.casa
APP_BASE_URL=https://inspect.midominio.casa
COOKIE_SECURE=true
```

El contenedor `app` tiene que resolver `auth.midominio.casa`, normalmente con tu DNS local.
Si no puede, añade en `docker-compose.yml`, servicio `app`, `extra_hosts: ["auth.midominio.casa:<IP del proxy>"]`.

Caddy:

```caddy
auth.midominio.casa {
    reverse_proxy localhost:9000
}
inspect.midominio.casa {
    reverse_proxy localhost:8080
    request_body {
        max_size 200MB
    }
}
```

Nginx (los dos servicios atienden las cabeceras `X-Forwarded-*`):

```nginx
server {
    server_name auth.midominio.casa;
    location / {
        proxy_pass         http://127.0.0.1:9000;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_set_header   X-Forwarded-Host  $host;
    }
}
server {
    server_name inspect.midominio.casa;
    client_max_body_size 200m;
    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_set_header   X-Forwarded-Host  $host;
    }
}
```

### 4. Arrancar

```bash
docker compose up -d --build
docker compose ps              # auth-server y app pasan a "healthy"
docker compose logs -f app
```

`app` espera a que `auth-server` esté *healthy*. Los dos tienen `restart: unless-stopped`.

**Actualizar** a una versión nueva:

```bash
git pull
docker compose up -d --build   # Flyway aplica las migraciones
```

### Volúmenes

| Volumen | Montado en | Contenido |
|---------|-----------|-----------|
| `home-inspect_uploads` | `app:/data/uploads` | Fotos, firmas y logo |
| `home-inspect_auth-keys` | `auth-server:/data/keys` | Clave RSA de firma de los JWT (cifrada con `AUTH_KEYSTORE_PASSWORD`) |

Si prefieres usar tu propio keystore, copia un PKCS12 como `jwt-signing.p12` en el volumen de claves
(contraseña = `AUTH_KEYSTORE_PASSWORD`):

```bash
keytool -genkeypair -alias jwt-signing -keyalg RSA -keysize 2048 -validity 3650 \
        -storetype PKCS12 -keystore jwt-signing.p12 -dname "CN=home-inspect"
```

## Variables de entorno

Todas se definen en `.env` (ver `.env.example`).

| Variable | Por defecto | Descripción |
|----------|-------------|-------------|
| `DB_HOST` / `DB_PORT` | `host.docker.internal` / `5432` | Servidor PostgreSQL |
| `AUTH_DB_NAME` / `AUTH_DB_USER` / `AUTH_DB_PASSWORD` | `homeinspect_auth` / `homeinspect_auth` / — | Base de datos del `auth-server` |
| `APP_DB_NAME` / `APP_DB_USER` / `APP_DB_PASSWORD` | `homeinspect_app` / `homeinspect_app` / — | Base de datos de la `app` |
| `DB_NETWORK` | — | Red Docker de tu Postgres (con `docker-compose.dbnet.yml`) |
| `AUTH_ISSUER_URI` | — | URL pública del `auth-server`, la misma para el navegador y para la `app` |
| `APP_BASE_URL` | — | URL pública de la `app` (redirección OAuth2 y vuelta del logout) |
| `AUTH_PORT` / `APP_PORT` | `9000` / `8080` | Puertos publicados en el host |
| `AUTH_ADMIN_USERNAME` / `AUTH_ADMIN_PASSWORD` | `admin` / — | Administrador inicial |
| `AUTH_KEYSTORE_PASSWORD` | — | Contraseña de la clave de firma JWT (mín. 8 caracteres) |
| `WEB_CLIENT_SECRET` | — | Secreto del cliente `home-inspect-web` |
| `ADMIN_API_CLIENT_SECRET` | — | Secreto del cliente `home-inspect-admin-api` |
| `UPLOAD_MAX_FILE_SIZE` | `15MB` | Tamaño máximo por foto |
| `APP_PROFILES` | — | Perfiles de Spring, p. ej. `demo` |
| `COOKIE_SECURE` | `true` | Cookies solo por HTTPS. `false` para pruebas en HTTP local |
| `TZ` | `Europe/Madrid` | Zona horaria de los contenedores |

Ajustes avanzados, con valores por defecto razonables: `SESSION_TIMEOUT` (sesión de la app 8 h; del
`auth-server` 30 min), `AUTH_LOCKOUT_ATTEMPTS` (5) y `AUTH_LOCKOUT_DURATION` (`15m`).

## Primer acceso del administrador

1. En el primer arranque, el `auth-server` crea el usuario `AUTH_ADMIN_USERNAME` con la contraseña
   `AUTH_ADMIN_PASSWORD` y rol `ADMIN`. Si el usuario ya existe, no se modifica.
2. Abre la URL de la app: te redirige a la página de login del servidor de acceso.
3. Entra con esas credenciales. Se te pedirá **cambiar la contraseña** antes de continuar
   (mínimo 10 caracteres, con letras y números).
4. Crea a los inspectores en **Administración → Usuarios**. Cada uno recibe una contraseña temporal,
   que se muestra una sola vez, y deberá cambiarla en su primer acceso.

Una vez creado el administrador puedes quitar `AUTH_ADMIN_PASSWORD` del `.env`.
Si se te olvida la contraseña del único administrador, crea otro cambiando `AUTH_ADMIN_USERNAME`
y reiniciando el `auth-server`.

## Backup y restauración

### Backup

```bash
scripts/backup.sh                 # → backups/AAAAMMDD-HHMMSS/
scripts/backup.sh /ruta/destino
```

Genera:

- `homeinspect_auth.dump` y `homeinspect_app.dump`: `pg_dump` en formato *custom*.
- `uploads.tar.gz`: fotos, firmas y logo.
- `auth-keys.tar.gz`: clave de firma. Sin ella, todas las sesiones caducan al restaurar.

Lee la conexión del `.env` y ejecuta `pg_dump` en un contenedor, así que no necesitas el cliente de
PostgreSQL en el host. Si tu servidor es más nuevo que PostgreSQL 17, usa `PG_CLIENT_IMAGE=postgres:18-alpine`.
Prográmalo con `cron`, por ejemplo cada noche:

```cron
30 3 * * * cd /opt/home-inspect && scripts/backup.sh >> backups/backup.log 2>&1
```

### Restauración

```bash
docker compose stop app auth-server
B=backups/20260101-033000

# Bases de datos (deben existir: db/init.sql en un servidor nuevo)
pg_restore -h <host> -U homeinspect_auth -d homeinspect_auth --clean --if-exists $B/homeinspect_auth.dump
pg_restore -h <host> -U homeinspect_app  -d homeinspect_app  --clean --if-exists $B/homeinspect_app.dump

# Volúmenes
docker run --rm -v home-inspect_uploads:/data -v "$PWD/$B":/backup alpine:3 \
  sh -c "rm -rf /data/* && tar xzf /backup/uploads.tar.gz -C /data"
docker run --rm -v home-inspect_auth-keys:/data -v "$PWD/$B":/backup alpine:3 \
  sh -c "rm -rf /data/* && tar xzf /backup/auth-keys.tar.gz -C /data"

docker compose start auth-server app
```

## Personalizar el catálogo del informe

Las secciones, los elementos, las opciones de material, las limitaciones y los comentarios iniciales
están en [`app/src/main/resources/catalog/sop-catalog.yml`](app/src/main/resources/catalog/sop-catalog.yml).
Al arrancar, la `app` sincroniza ese fichero con la base de datos por `code`:

- Los elementos nuevos aparecen en los informes nuevos y en los borradores existentes.
- Se actualizan los títulos, el orden y las opciones.
- Lo que quites del fichero se **desactiva**, no se borra: los informes antiguos lo conservan.
- Los comentarios del fichero solo se cargan al crear el elemento. Después se editan desde el panel
  de administración (fase 8).

No cambies el `code` de un elemento ya usado. Si un texto contiene `: `, ponlo entre comillas.

## Desarrollo

Compilar y ejecutar todos los tests (unitarios e integración con Testcontainers + PostgreSQL)
**sin JDK local**:

```bash
scripts/mvn-docker.sh verify
scripts/mvn-docker.sh -pl app test -Dtest=ReportServiceTests
```

El script ejecuta Maven en `maven:3.9-eclipse-temurin-25`, guarda la caché de dependencias en el
volumen `home-inspect-m2` y monta el socket de Docker para Testcontainers.

Estructura:

```
home-inspect/
├── pom.xml                      # padre (Spring Boot 4.1, Java 25)
├── auth-server/                 # servidor de autorización
│   ├── Dockerfile
│   └── src/main/resources/db/migration   # esquema auth
├── app/                         # aplicación de informes
│   ├── Dockerfile
│   └── src/main/resources/
│       ├── catalog/sop-catalog.yml       # estructura del informe
│       ├── db/migration/                 # Flyway
│       ├── messages.properties           # textos (español)
│       └── templates/                    # Thymeleaf
├── db/init.sql                  # creación de las BD (manual, una vez)
├── docker-compose.yml           # producción (sin Postgres)
├── docker-compose.dev.yml       # desarrollo (con Postgres temporal)
├── docker-compose.dbnet.yml     # unirse a la red de tu Postgres
└── scripts/
    ├── mvn-docker.sh            # Maven con JDK 25 en Docker
    └── backup.sh                # backup de BD y volúmenes
```

Todos los textos de la interfaz están en `messages.properties`. Para traducirla, añade
`messages_en.properties` y cambia `spring.web.locale-resolver` a `accept-header` en `application.yml`.

## Licencia

[Apache License 2.0](LICENSE)
