-- One-off setup of the two databases in YOUR existing PostgreSQL (run it once, as a superuser).
-- Each service gets its own database and login role, and owns only its database.
-- Flyway creates the tables afterwards.
--
--   psql -h <host> -U postgres \
--        -v auth_password='<AUTH_DB_PASSWORD>' -v app_password='<APP_DB_PASSWORD>' \
--        -f db/init.sql
--
-- Optional variables (defaults shown): auth_db=homeinspect_auth auth_user=homeinspect_auth
--                                      app_db=homeinspect_app   app_user=homeinspect_app
-- (database names must match AUTH_DB_URL / APP_DB_URL, users AUTH_DB_USERNAME / APP_DB_USERNAME)

\set ON_ERROR_STOP on

\if :{?auth_password}
\else
  \echo 'Missing -v auth_password=...'
  \quit
\endif
\if :{?app_password}
\else
  \echo 'Missing -v app_password=...'
  \quit
\endif
\if :{?auth_db}
\else
  \set auth_db homeinspect_auth
\endif
\if :{?auth_user}
\else
  \set auth_user homeinspect_auth
\endif
\if :{?app_db}
\else
  \set app_db homeinspect_app
\endif
\if :{?app_user}
\else
  \set app_user homeinspect_app
\endif

-- Login roles without extra privileges
CREATE ROLE :"auth_user" LOGIN PASSWORD :'auth_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE ROLE :"app_user"  LOGIN PASSWORD :'app_password'  NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

-- One database per service, owned by its role
CREATE DATABASE :"auth_db" OWNER :"auth_user" ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE :"app_db"  OWNER :"app_user"  ENCODING 'UTF8' TEMPLATE template0;

-- Nobody else may connect
REVOKE ALL ON DATABASE :"auth_db" FROM PUBLIC;
REVOKE ALL ON DATABASE :"app_db"  FROM PUBLIC;

-- The public schema belongs to each service role (no CREATE for anybody else)
\connect :"auth_db"
ALTER SCHEMA public OWNER TO :"auth_user";
REVOKE ALL ON SCHEMA public FROM PUBLIC;

\connect :"app_db"
ALTER SCHEMA public OWNER TO :"app_user";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
