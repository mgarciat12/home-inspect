# home-inspect

[![Release](https://img.shields.io/github/v/release/mgarciat12/home-inspect)](https://github.com/mgarciat12/home-inspect/releases)
![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Bootstrap 5.3.9](https://img.shields.io/badge/Bootstrap-5.3.9-7952B3?logo=bootstrap&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
[![License](https://img.shields.io/github/license/mgarciat12/home-inspect)](LICENSE)

Aplicación web para rellenar **informes de inspección de viviendas de Florida (EE. UU.)** desde el móvil,
con fotos y exportación a PDF. Tipos de informe: **inspección completa** (Standards of Practice de Florida,
61-30.801 a 61-30.811 F.A.C.), **4-Point** (Citizens Insp4pt 03 25) y **Wind Mitigation**
(OIR-B1-1802 Rev. 04/26). La estructura de cada tipo está definida como datos, no en el código.
La interfaz está en inglés y español; los informes se generan siempre en inglés.

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
- [Versiones](#versiones)
- [Licencia](#licencia)

## Estado del proyecto

El desarrollo va por fases:

| Fase | Contenido | Estado |
|------|-----------|--------|
| 1 | Esqueleto multi-módulo, Docker Compose, Flyway, layout Bootstrap | ✅ |
| 2 | `auth-server`: OAuth2/OIDC, usuarios en BD, login propio, claves persistentes, roles, API de usuarios | ✅ |
| 3 | `app` como cliente OAuth2: login, logout, roles y panel de administración de usuarios | ✅ |
| 4 | Modelo del informe, catálogo del SOP y editor con autoguardado | ✅ |
| 5 | Subida y gestión de fotos, firma del inspector | ✅ |
| 6 | Resumen automático de hallazgos y vista previa | ✅ |
| 7 | Exportación a PDF | ✅ |
| 8 | Ajustes de empresa, biblioteca de comentarios editable, 2FA opcional, datos demo, repaso de seguridad | ✅ |
| 9 | Tipos de informe (completo, 4-Point, Wind Mitigation), numeración por tipo, interfaz bilingüe, licencias del inspector, cabecera de EE. UU. | ✅ |
| 10 | Inspección completa según los Standards of Practice de Florida (61-30.801 a .811) y la sección 468.8323, F.S. | ✅ |
| 11 | 4-Point (Citizens Insp4pt 03 25): preguntas del formulario, fotos obligatorias y PDF con el formato oficial | ✅ |
| 12 | Wind Mitigation (OIR-B1-1802 Rev. 04/26): preguntas 1 a 9, certificación del inspector cualificado | ✅ |
| 13 | Visita combinada: varios tipos de informe para la misma visita con los datos generales compartidos | ✅ |

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
- **Verificación en dos pasos** (TOTP: Google Authenticator, Microsoft Authenticator, Aegis, FreeOTP…),
  desactivada por defecto: opcional por usuario (`AUTH_MFA_ENABLED`) u obligatoria para todos
  (`AUTH_MFA_REQUIRED`). Alta con código QR; el administrador puede quitarla si alguien pierde el móvil.

**Idioma y licencias**
- Interfaz en **inglés y español**: cada usuario elige en **Mi perfil** o se usa el idioma del navegador
  (inglés si no es ninguno de los dos). La página de login sigue el idioma del navegador.
- Los informes (PDF y vista previa) se generan **siempre en inglés**, con fechas y unidades de EE. UU.
- Cada inspector registra sus **licencias de Florida** (home inspector 468.8314, contratistas 489.111,
  ingeniero 471.015…) en **Mi perfil**; aparecen junto a su firma. La empresa tiene su propio nº de licencia.

**Informes**
- Tres tipos: **inspección completa**, **4-Point** y **Wind Mitigation**. Se elige al crear el informe y no
  se puede cambiar. Un tipo sin catálogo aparecería como «Próximamente».
- **Visita combinada:** al crear un informe se pueden marcar los otros tipos para crearlos a la vez. Comparten
  cliente, inmueble y póliza; al cambiar los datos generales de uno se aplican a los borradores de la misma
  visita, y el editor enlaza los informes de la visita.
- Numeración anual por tipo: `2026-0001` (completa), `4P-2026-0001`, `WM-2026-0001`.
- Cabecera con cliente, aseguradora y nº de póliza, inmueble (ciudad, condado, estado, ZIP, superficie en sq ft)
  y condiciones (temperatura en °F).
- **Inspección completa** (Standards of Practice de Florida, reglas 61-30.801 a 61-30.811 F.A.C.): 8 secciones
  (cubierta, exterior, terreno, estructura, electricidad, climatización, fontanería e interior) con los
  componentes que exige cada regla. Cada elemento tiene descripción/material, estado, valoración,
  limitaciones, observaciones y recomendación, más una biblioteca de comentarios en inglés que se insertan con un clic.
  Cubierta, exterior y terreno pueden marcarse «no aplica» (elementos comunes de un condominio).
- Valoración **«Near end of service life»** y lista de **«Systems and components not inspected»** con el motivo,
  como exige la sección 468.8323, F.S. Un informe no se puede finalizar si algún elemento no inspeccionado no
  indica el motivo.
- **4-Point** y **Wind Mitigation**: cada elemento es una pregunta del formulario oficial (sí/no, opciones,
  «marca todas las que apliquen», estado, número, año, fecha, texto o foto). Las respuestas se validan contra las
  opciones; las que lo exigen piden explicación en los comentarios; las fotos obligatorias son elementos propios
  que se marcan solos al subir la foto. No se puede finalizar sin las respuestas, explicaciones y fotos obligatorias.
- El PDF de los formularios reproduce el **formato oficial** (preguntas, casillas ☒/☐, textos legales y
  certificación con la licencia del inspector aceptada para ese formulario), con un anexo de fotos. La vista
  previa muestra el mismo documento.
- Editor *mobile-first* con progreso por sección y **autoguardado por elemento**: si se cae la conexión,
  los cambios se guardan en el navegador y se reenvían solos.
- Estados `Borrador` → `Finalizado`: un informe finalizado queda bloqueado y solo un administrador puede reabrirlo.
- Listado con buscador (dirección, cliente o nº), filtros por tipo, estado y fecha, y paginación.
- Duplicar un informe para usarlo como plantilla.

**Fotos**
- Varias fotos por elemento: cámara del móvil, galería o arrastrar y soltar en escritorio.
- Se valida el tipo real del archivo (JPEG, PNG o HEIC; el HEIC se convierte con libheif dentro del contenedor).
- Se corrige la orientación EXIF y se guarda una versión optimizada (1600 px) y una miniatura (400 px),
  re-codificadas **sin metadatos**: no se sirve la posición GPS. El original no se conserva.
- Pie de foto, orden, foto de portada y borrado. Las imágenes solo se sirven a usuarios con acceso al informe.
- Al borrar una foto o un informe se eliminan sus ficheros del disco.
- Cada inspector sube su firma en **Mi perfil**; aparecerá en el PDF.

**Resumen y vista previa**
- **Resumen de hallazgos** automático: todos los elementos valorados como *Peligro para la seguridad*,
  *Reparar*, *Requiere evaluación por especialista* o *Mantenimiento*, agrupados por gravedad (peligro primero),
  con su referencia (`1.2 Tejado › Canalones y bajantes`), un extracto de la observación, la recomendación
  y una miniatura. Las secciones marcadas como «no aplica» no se incluyen.
- **Vista previa** en HTML con la misma estructura que el PDF: portada, alcance y limitaciones, leyenda,
  resumen, datos generales, secciones detalladas con fotos y firma.

**PDF**
- Se genera con OpenHTMLtoPDF a partir de una plantilla Thymeleaf de impresión (`templates/pdf/report.html`).
- A4, cabecera con dirección y nº de informe en cada página, pie con los datos de la empresa y «Página X de Y».
- Fotos en cuadrícula de 2 columnas con su pie, usando la versión optimizada (el PDF ocupa poco).
- Fuentes DejaVu incrustadas: cualquier acento o símbolo se ve igual en todos los visores.
- Nombre del archivo: `home-inspection-<nº>-<calle>.pdf` (`4-point-…`, `wind-mitigation-…` según el tipo). Se descarga desde la vista del informe o la vista previa.

**Administración**
- **Empresa y textos**: nombre, logo, dirección, teléfono, email, web, NIF, texto de alcance y texto legal
  (aparecen en la portada y el pie del PDF).
- **Biblioteca de comentarios**: añadir, editar, ordenar y borrar las frases predefinidas de cada elemento.
- **Datos de demostración**: con `APP_PROFILES=demo` y la base de datos vacía se crea un informe de
  ejemplo con valoraciones, observaciones y fotos de prueba (visible para los administradores).
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
   PUBLIC_URL=http://localhost:8080
   COOKIE_SECURE=false
   ```

3. Arranca:

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
   docker compose -f docker-compose.yml -f docker-compose.dev.yml ps    # espera a "healthy"
   ```

4. Abre <http://localhost:8080> y entra con `ADMIN_USERNAME` / `ADMIN_PASSWORD`
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
git clone --branch v1.2.1 https://github.com/mgarciat12/home-inspect.git
cd home-inspect
cp .env.example .env
```

Genera los secretos con `openssl rand -base64 32` y rellena el `.env`
(ver [variables](#variables-de-entorno)). **Nunca subas el `.env` al repositorio.**

¿Dónde está tu PostgreSQL?

La conexión se define con una URL JDBC completa por servicio, igual que en featuretoggle
(`AUTH_DB_URL` / `AUTH_DB_USERNAME` / `AUTH_DB_PASSWORD` y `APP_DB_URL` / `APP_DB_USERNAME` / `APP_DB_PASSWORD`;
dentro de cada contenedor se llaman `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`).

- **En el propio host de Docker:** host `host.docker.internal`
  (`AUTH_DB_URL=jdbc:postgresql://host.docker.internal:5432/homeinspect_auth`). Los servicios ya incluyen
  `extra_hosts: host-gateway`. Comprueba que Postgres escucha en la IP del puente de Docker
  (`listen_addresses`) y que `pg_hba.conf` permite esa red.
- **En otro contenedor:** une los servicios a su red con `docker-compose.dbnet.yml`:

  ```dotenv
  AUTH_DB_URL=jdbc:postgresql://nombre-del-contenedor-postgres:5432/homeinspect_auth
  APP_DB_URL=jdbc:postgresql://nombre-del-contenedor-postgres:5432/homeinspect_app
  DB_NETWORK=nombre-de-su-red          # docker network ls
  COMPOSE_FILE=docker-compose.yml:docker-compose.dbnet.yml
  ```

### 3. El issuer y el proxy inverso

El `issuer` del token tiene que coincidir **exactamente** con la URL que ven el navegador y el
contenedor `app`. Usa dos subdominios, uno por servicio, detrás del proxy:

```dotenv
AUTH_ISSUER_URI=https://auth.midominio.casa
PUBLIC_URL=https://inspect.midominio.casa
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

**Actualizar** a una versión nueva (haz antes un [backup](#backup-y-restauración)):

```bash
git fetch --tags
git checkout v1.x.y
docker compose up -d --build   # Flyway aplica las migraciones
```

### Desplegar con Coolify

`docker-compose.coolify.yml` es el mismo stack preparado para Coolify: no publica puertos en el host
(el proxy de Coolify enruta los dominios, así no choca con otras apps como featuretoggle en el 8080)
y marca como obligatorias las variables sin valor por defecto.

1. Prepara PostgreSQL con `db/init.sql` ([paso 1](#1-preparar-postgresql-una-sola-vez)).
2. En Coolify: **New Resource → Public/Private Repository** con el repositorio y la rama/tag a desplegar,
   *Build Pack* **Docker Compose** y *Docker Compose Location* `/docker-compose.coolify.yml`.
3. En **Environment Variables** rellena (Coolify las detecta del compose):

   ```dotenv
   AUTH_DB_URL=jdbc:postgresql://<host-postgres>:5432/homeinspect_auth
   AUTH_DB_USERNAME=homeinspect_auth
   AUTH_DB_PASSWORD=<secreto>
   APP_DB_URL=jdbc:postgresql://<host-postgres>:5432/homeinspect_app
   APP_DB_USERNAME=homeinspect_app
   APP_DB_PASSWORD=<secreto>
   AUTH_ISSUER_URI=https://auth.midominio.casa
   PUBLIC_URL=https://inspect.midominio.casa
   ADMIN_USERNAME=admin
   ADMIN_PASSWORD=<secreto>
   AUTH_KEYSTORE_PASSWORD=<secreto>
   WEB_CLIENT_SECRET=<secreto>
   ADMIN_API_CLIENT_SECRET=<secreto>
   ```

   Son los mismos nombres que en featuretoggle (`*_DB_URL`, `*_DB_USERNAME`, `*_DB_PASSWORD`, `PUBLIC_URL`,
   `ADMIN_*`). Si Postgres es otro recurso de Coolify, activa **Connect to Predefined Network** y usa su
   nombre interno como `<host-postgres>`; si está en el host, `host.docker.internal`.
4. En cada servicio asigna su dominio con el puerto interno: `auth-server` → `https://auth.midominio.casa:9000`,
   `app` → `https://inspect.midominio.casa:8080`. Tienen que coincidir con `AUTH_ISSUER_URI` y `PUBLIC_URL`.
5. **Deploy**. El contenedor `app` descarga la configuración del `auth-server` en `AUTH_ISSUER_URI`, así que esa
   URL tiene que resolverse también desde el servidor (DNS local o túnel de Cloudflare).

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

Todas se definen en `.env` (ver `.env.example`) o, en Coolify, en la pestaña *Environment Variables*.
Los nombres siguen la convención de featuretoggle.

| Variable | Por defecto | Descripción |
|----------|-------------|-------------|
| `AUTH_DB_URL` / `AUTH_DB_USERNAME` / `AUTH_DB_PASSWORD` | `jdbc:postgresql://host.docker.internal:5432/homeinspect_auth` / `homeinspect_auth` / — | Base de datos del `auth-server` (en el contenedor: `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`) |
| `APP_DB_URL` / `APP_DB_USERNAME` / `APP_DB_PASSWORD` | `jdbc:postgresql://host.docker.internal:5432/homeinspect_app` / `homeinspect_app` / — | Base de datos de la `app` (en el contenedor: `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`) |
| `DB_NETWORK` | — | Red Docker de tu Postgres (con `docker-compose.dbnet.yml`) |
| `AUTH_ISSUER_URI` | — | URL pública del `auth-server`, la misma para el navegador y para la `app` |
| `PUBLIC_URL` | — | URL pública de la `app` (redirección OAuth2 y vuelta del logout) |
| `AUTH_PORT` / `APP_PORT` | `9000` / `8080` | Puertos publicados en el host |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `ADMIN_NAME` | `admin` / — / `Administrador` | Administrador inicial |
| `AUTH_MFA_ENABLED` | `false` | Cada usuario puede activar la verificación en dos pasos |
| `AUTH_MFA_REQUIRED` | `false` | Verificación en dos pasos obligatoria para todos |
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

1. En el primer arranque, el `auth-server` crea el usuario `ADMIN_USERNAME` con la contraseña
   `ADMIN_PASSWORD` y rol `ADMIN`. Si el usuario ya existe, no se modifica.
2. Abre la URL de la app: te redirige a la página de login del servidor de acceso.
3. Entra con esas credenciales. Se te pedirá **cambiar la contraseña** antes de continuar
   (mínimo 10 caracteres, con letras y números).
4. Crea a los inspectores en **Administración → Usuarios**. Cada uno recibe una contraseña temporal,
   que se muestra una sola vez, y deberá cambiarla en su primer acceso.

Una vez creado el administrador puedes quitar `ADMIN_PASSWORD` del `.env`.
Si se te olvida la contraseña del único administrador, crea otro cambiando `ADMIN_USERNAME`
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

## Seguridad

Resumen de las medidas aplicadas (repaso de la fase 8):

| Área | Medida |
|------|--------|
| Autenticación | OAuth 2.1 / OIDC con Authorization Code + PKCE obligatorio; contraseñas BCrypt; política de contraseñas; bloqueo 15 min tras 5 fallos (también con códigos 2FA); 2FA TOTP opcional |
| Pasos de login | Contraseña temporal y 2FA se resuelven **antes** de emitir el código de autorización (filtro previo a Spring Security) |
| Tokens | Clave RSA persistente y cifrada; roles leídos de la BD al emitir cada token; ningún token para usuarios desactivados; al desactivar, cambiar rol o resetear contraseña/2FA se revocan sus autorizaciones |
| Sesiones | Cookies `HttpOnly`, `SameSite=Lax`, `Secure` configurable, nombres distintos por servicio; un usuario desactivado o con rol cambiado pierde la sesión de la app en su siguiente petición |
| Autorización | Un inspector solo ve y edita sus informes (comprobado en la capa de servicio; un id ajeno responde 404); administración con `@PreAuthorize` además de la regla de URL; informes finalizados bloqueados |
| API de usuarios | Resource server con scope `users.admin`, solo `client_credentials` de la app |
| Web | CSRF en todos los formularios y en el autoguardado (cabecera); CSP sin scripts en línea (`script-src 'self'`); `X-Frame-Options`, `nosniff`, `Referrer-Policy`; sin `th:utext` |
| Ficheros | Tipo real por contenido; límite de tamaño; protección ante imágenes gigantes; re-codificación sin metadatos (sin GPS); rutas generadas (sin entrada del usuario); imágenes solo para quien puede ver el informe |
| Infraestructura | Contenedores sin root; solo `/actuator/health` expuesto y sin detalles; secretos solo por variables de entorno; Postgres sin publicar; cada servicio con su usuario y su base de datos |

Recomendaciones para el despliegue:

- Publica la app y el servidor de acceso **solo por HTTPS** (`COOKIE_SECURE=true`) y no expongas los puertos
  8080/9000 fuera del host (bind a `127.0.0.1` o cortafuegos).
- Activa `AUTH_MFA_ENABLED=true` (o `AUTH_MFA_REQUIRED=true`) si accedes desde fuera de tu red.
- Guarda `AUTH_KEYSTORE_PASSWORD` en un lugar seguro: sin ella no se puede descifrar la clave de firma
  ni los secretos 2FA de un backup.
- Ten en cuenta que la página de login informa de que una cuenta está bloqueada o desactivada
  (más claro para el usuario, a cambio de revelar que el usuario existe).

## Personalizar el catálogo del informe

Cada tipo de informe tiene su catálogo en `app/src/main/resources/catalog/`:
[`full.yml`](app/src/main/resources/catalog/full.yml) (inspección completa),
[`four-point.yml`](app/src/main/resources/catalog/four-point.yml) y
[`wind-mitigation.yml`](app/src/main/resources/catalog/wind-mitigation.yml). Un tipo solo se puede elegir al
crear un informe cuando su catálogo tiene secciones.

- Inspección completa: secciones, elementos, opciones de material, limitaciones y comentarios iniciales.
- Formularios: cada elemento es una pregunta con `field` (`TEXT`, `NUMBER`, `YEAR`, `DATE`, `YES_NO`,
  `YES_NO_NA`, `CHOICE`, `MULTI`, `CONDITION`, `PHOTO`), `options`, `required`, `explain_on` (respuestas que
  piden explicación), `photo_required` y `help`. La plantilla `templates/pdf/form.html` imprime las
  secciones en el orden del catálogo; las secciones `owner`, `certification` y `photos` alimentan los bloques
  fijos del formulario.

Al arrancar, la `app` sincroniza cada fichero con la base de datos por `code`:

- Los elementos nuevos aparecen en los informes nuevos y en los borradores existentes.
- Se actualizan los títulos, el orden y las opciones.
- Lo que quites del fichero se **desactiva**, no se borra: los informes antiguos lo conservan.
- Los comentarios del fichero se cargan una sola vez por elemento. Después se editan en
  **Administración → Biblioteca de comentarios**.

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
│       ├── catalog/full.yml              # estructura del informe completo
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

## Versiones

Las versiones siguen [Semantic Versioning](https://semver.org). Cada versión es un tag `vX.Y.Z` con sus
notas y los jars de los dos servicios en la página de [Releases](https://github.com/mgarciat12/home-inspect/releases).

| Versión | Novedades |
|---------|-----------|
| [v1.2.1](https://github.com/mgarciat12/home-inspect/releases/tag/v1.2.1) | Biblioteca de comentarios completa para los tres tipos de informe (inspección completa, 4-Point y Wind Mitigation) |
| [v1.2.0](https://github.com/mgarciat12/home-inspect/releases/tag/v1.2.0) | Informes de Florida: inspección completa (61-30.801 a .811 F.A.C.), 4-Point (Citizens Insp4pt 03 25) y Wind Mitigation (OIR-B1-1802 Rev. 04/26) con el formato oficial, visita combinada, interfaz en inglés y español, licencias del inspector |
| [v1.1.0](https://github.com/mgarciat12/home-inspect/releases/tag/v1.1.0) | Despliegue con Coolify (`docker-compose.coolify.yml`), variables de entorno con los mismos nombres que featuretoggle y corrección de la redirección a `apple-touch-icon.png` tras el login |
| [v1.0.0](https://github.com/mgarciat12/home-inspect/releases/tag/v1.0.0) | Primera versión: servidor de acceso OAuth2/OIDC con 2FA opcional, informes según el SOP con editor y autoguardado, fotos, resumen de hallazgos, PDF y despliegue con Docker |

## Licencia

[Apache License 2.0](LICENSE)
