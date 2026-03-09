# FlowMonitor API-kontrakt

## /api/range
Historiska intervall.

Query:
- from=YYYY-MM-DD
- to=YYYY-MM-DD

Returnerar:
{
  "rows": [...]
}

## /api/running
Live-endpoint för idag.

Tillåtet:
- inga datumparametrar
- eller from=today och to=today

Övriga intervall ska ge 400.

## /api/stopped
Live-endpoint för idag.

Tillåtet:
- inga datumparametrar
- eller from=today och to=today

Övriga intervall ska ge 400.

## /api/finished
Live-endpoint för idag.

Tillåtet:
- inga datumparametrar
- eller from=today och to=today

Övriga intervall ska ge 400.

## /api/monitoring
Bevakningsstatus.

Kan ta emot from/to för kontraktets skull, men:
- INFO/WARNING/ERROR får inte bero på GUI:ts datumintervall
- status räknas från bevakningsregler + aktuell tid + backenddata
