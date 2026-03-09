# Devserver

Mockserver för lokal frontend-utveckling.

## Start
cd /workspaces/monitor
node devserver/server.js

## Syfte
- serva GUI lokalt
- mocka API-endpoints
- testa range/live/monitoring utan riktig Java-backend

## Viktiga endpoints
- /api/health
- /api/range?from=YYYY-MM-DD&to=YYYY-MM-DD
- /api/running
- /api/stopped
- /api/finished
- /api/monitoring
