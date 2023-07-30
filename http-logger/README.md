# HTTP Event Logger

## TBD

## Database setup

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
