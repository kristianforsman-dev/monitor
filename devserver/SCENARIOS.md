# Mock-scenarier för monitor/bevakning

## Översikt
Devservern kan läsa eventdata från:

devserver/mock-events.json

Genom att kopiera ett scenario till den filen kan du testa olika bevakningsutfall i GUI:t.

Starta sedan om devservern:

cd /workspaces/monitor
node devserver/server.js

---

## Aktivera ett scenario

Exempel:

cd /workspaces/monitor
cp devserver/mock-events.interval-error.json devserver/mock-events.json
node devserver/server.js

---

## ExactTimes-scenarier

### OK
Flow:
SAP-ECC||RSF||RSFBESTANM

Regler:
- 3 före 12:00
- 9 före 15:32

Förväntat:
status = INFO

---

### Negativt scenario A

Utfall:
- 2 före 12:00
- 8 före 15:32

Förväntat:
status = ERROR

---

### Negativt scenario B

Utfall:
- 3 före 12:00
- 7 före 15:32

Förväntat:
status = ERROR
message ungefär: "Tid 15:32: saknar 2"

---

## Interval-scenarier

Flow:
RSF||SAP-ECC||RSFBESTANMIN

Intervall:
09:00-10:15

expected = 50  
warnAtPct = 0.7  
tolerancePct = 0.05  

---

### OK
50 filer i intervallet

Förväntat:
status = INFO

---

### Warning
För låg takt under pågående intervall

Förväntat:
status = WARNING

---

### Error
Exempel:
42 filer efter deadline

Beräkning:
minOk = floor(50 * (1 - 0.05)) = 47

Förväntat:
status = ERROR
message = "Intervall 09:00-10:15: saknar 5"

---

## Verifiering via API

### ExactTimes

cd /workspaces/monitor

node - <<'NODE'
(async()=>{
  const base="http://127.0.0.1:8080";
  const mon = await (await fetch(base+"/api/monitoring")).json();
  const row = (mon.rows||[]).find(r => r.flowId === "SAP-ECC||RSF||RSFBESTANM");
  console.log(JSON.stringify(row, null, 2));
})();
NODE

---

### Intervals

cd /workspaces/monitor

node - <<'NODE'
(async()=>{
  const base="http://127.0.0.1:8080";
  const mon = await (await fetch(base+"/api/monitoring")).json();
  const row = (mon.rows||[]).find(r => r.flowId === "RSF||SAP-ECC||RSFBESTANMIN");
  console.log(JSON.stringify(row, null, 2));
})();
NODE
