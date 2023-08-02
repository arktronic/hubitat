# HTTP Event Logger

## Purpose

This is a fork of the [InfluxDB Logger](https://github.com/HubitatCommunity/InfluxDB-Logger/), modified to send JSON data to an HTTP endpoint. The HTTP endpoint, in turn, can save the data in any format and location.

The [HubEventPersist](HubEventPersist/) project acts as a compatible HTTP endpoint, configurable to save data to either PostgreSQL or SQL Server.

Both Postgres and SQL Server could then be queried by a variety of analytics and visualization systems, including Grafana, to gather and report on metrics about your devices in your home.

## Using the HTTP Logger app in HE

Like with all apps, simply copy the Groovy code into a new app in HE inside "Apps Code", save it, and then add a new User App - in this case, named "HTTP Logger".

Configure it as you see fit, taking care to set up the HTTP endpoint destination correctly.

## Setting up the HubEventPersist HTTP endpoint

The easiest way to run the provided endpoint code is via Docker - a Dockerfile has been included. Two environment variables must be passed in to the container when executing it:

- `HUBEVENTPERSIST_DB` - must be set to either `Postgres` or `SqlServer`
- `HUBEVENTPERSIST_CONNECTIONSTRING` - must be a valid connection string for the selected database type, see [Npgsql docs](https://www.npgsql.org/doc/connection-string-parameters.html) and [SqlClient docs](https://learn.microsoft.com/en-us/dotnet/framework/data/adonet/connection-string-syntax#sqlclient-connection-strings) for details

## Database setup

A table named `HubEvents` must be created in order for the HubEventPersist app to successfully save data. The SQL code to create such a table in Postgres and SQL Server is provided below.

### PostgreSQL

```sql
CREATE TABLE "HubEvents" (
	"Id" bigserial NOT NULL,
	"DeviceId" int NOT NULL,
	"DeviceName" varchar(1024),
	"Event" varchar(1024),
	"Value" varchar(1024),
	"Unit" varchar(1024),
	"Recorded" timestamptz NOT NULL,
	"Hub" varchar(1024),
	"Polled" bool NOT NULL,
	"Extra" json
);
```

### SQL Server

```sql
CREATE TABLE dbo.HubEvents (
	Id bigint IDENTITY(1,1) NOT NULL,
	DeviceId int NOT NULL,
	DeviceName nvarchar(1024),
	Event nvarchar(1024),
	Value nvarchar(1024),
	Unit nvarchar(1024),
	Recorded datetimeoffset NOT NULL,
	Hub nvarchar(1024),
	Polled bit NOT NULL,
	Extra nvarchar(max)
);
```

## License

The Hubitat Elevation app, being a fork, is licensed under Apache 2.0. Everything else follows the repo-wide license.
