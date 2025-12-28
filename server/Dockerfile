# Build web UI
FROM node:20-alpine AS web-build
WORKDIR /src/web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# Build .NET app
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY server/WebServer.csproj server/
RUN dotnet restore server/WebServer.csproj
COPY server/ server/
RUN dotnet publish server/WebServer.csproj -c Release -o /app/publish /p:UseAppHost=false

# Runtime image
FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS final
WORKDIR /app
COPY --from=build /app/publish .
COPY --from=web-build /src/web/dist ./wwwroot
ENV ASPNETCORE_URLS=http://0.0.0.0:8080
ENV StaticFiles__RootPath=/app/wwwroot
ENTRYPOINT ["dotnet", "WebServer.dll"]
