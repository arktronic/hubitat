using Microsoft.EntityFrameworkCore;

/*
Postgres:
CREATE TABLE "HubEvents" (
	"Id" bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
	"DeviceId" int NOT NULL,
	"DeviceName" varchar(1024) NULL,
	"Event" varchar(1024) NULL,
	"Value" varchar(1024) NULL,
	"Unit" varchar(1024) NULL,
	"Recorded" timestamptz NOT NULL,
	"Hub" varchar(1024) NULL,
	"Polled" bool NOT NULL,
	"Extra" jsonb NULL
);

SqlServer:
CREATE TABLE HubEvents (
	Id bigint IDENTITY(1,1) NOT NULL,
	DeviceId int NOT NULL,
	DeviceName nvarchar(1024) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	Event nvarchar(1024) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	Value nvarchar(1024) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	Unit nvarchar(1024) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	Recorded datetimeoffset NOT NULL,
	Hub nvarchar(1024) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
	Polled bit NOT NULL,
	Extra nvarchar(MAX) COLLATE SQL_Latin1_General_CP1_CI_AS NULL
);
*/

#if DEBUG
if (System.Diagnostics.Debugger.IsAttached)
{
    //Environment.SetEnvironmentVariable("HUBEVENTPERSIST_DB", "SqlServer");
    //Environment.SetEnvironmentVariable("HUBEVENTPERSIST_CONNECTIONSTRING", "Data Source=127.0.0.1,1433;Initial Catalog=hub;User ID=sa;Password=MyTemporary!Password1;TrustServerCertificate=True");
    Environment.SetEnvironmentVariable("HUBEVENTPERSIST_DB", "Postgres");
    Environment.SetEnvironmentVariable("HUBEVENTPERSIST_CONNECTIONSTRING", "Host=127.0.0.1;Database=hub;Username=postgres;Password=MyTemporary!Password1");
}
#endif

var builder = WebApplication.CreateBuilder(args);
var dbType = Environment.GetEnvironmentVariable("HUBEVENTPERSIST_DB");
if (dbType == "SqlServer")
    builder.Services.AddDbContext<EventDb>(opt => opt.UseSqlServer(Environment.GetEnvironmentVariable("HUBEVENTPERSIST_CONNECTIONSTRING")));
else if (dbType == "Postgres")
    builder.Services.AddDbContext<EventDb>(opt => opt.UseNpgsql(Environment.GetEnvironmentVariable("HUBEVENTPERSIST_CONNECTIONSTRING")));
else
    throw new Exception("HUBEVENTPERSIST_DB and HUBEVENTPERSIST_CONNECTIONSTRING environment variables must be defined!");
var app = builder.Build();

app.MapGet("/", async (EventDb db) =>
{
    if (await db.Database.CanConnectAsync())
    {
        return Results.Json(new
        {
            alive = true,
            database = true,
            dbType = db.Database.ProviderName,
        });
    }
    else
    {
        try
        {
            await db.Database.OpenConnectionAsync();
        }
        catch (Exception ex)
        {
            return Results.Json(new
            {
                alive = true,
                database = false,
                dbType = db.Database.ProviderName,
                connectionErrorMessage = ex.Message,
                connectionErrorDetails = ex.ToString(),
            });
        }
        return Results.Json(new
        {
            alive = true,
            database = false,
            dbType = db.Database.ProviderName,
        });
    }
});

app.MapGet("/test", () =>
{
    Console.WriteLine("Test!");
    return Results.NoContent();
});

app.MapPost("/events", async (List<HubHttpEvent> events, EventDb db) =>
{
    try
    {
        db.HubEvents.AddRange(events.Select(e => e.ToHubEvent()));
        await db.SaveChangesAsync();

        return Results.Accepted();
    }
    catch (Exception ex)
    {
        return Results.Json(new
        {
            errorMessage = ex.Message,
            errorDetails = ex.ToString(),
        }, statusCode: 500);
    }
});

app.Run();
