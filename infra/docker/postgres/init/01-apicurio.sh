#!/bin/sh
# Creates the database used by Apicurio Registry. PostgreSQL runs the scripts
# in /docker-entrypoint-initdb.d only when the data volume is empty.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE USER "$APICURIO_DB_USER" WITH PASSWORD '$APICURIO_DB_PASSWORD';
	CREATE DATABASE "$APICURIO_DB" OWNER "$APICURIO_DB_USER";
EOSQL
