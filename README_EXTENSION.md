# README_EXTENSION

## Zweck

Dieses Dokument fasst den aktuellen Stand fuer **Task 6 (Build, Validierung und Dokumentation)**
zusammen und dient als Startpunkt fuer die manuelle Verifikation der OSC-Erweiterung.

## Build- und Verify-Status

Folgende Kommandos wurden ausgefuehrt:

- `mvn clean install -DskipTests`
- `mvn verify`

Beide Maven-Laeufe brechen in dieser Umgebung bereits beim Aufloesen des
`maven-enforcer-plugin` (HTTP 403 auf Maven Central) ab. Dadurch ist keine
vollstaendige lokale Build-Validierung in dieser Session moeglich.

## Manuelle Code-Review-Checkliste

Status auf Basis der in dieser Session moeglichen statischen Sichtung:

- [x] Keine neuen hartkodierten Werte eingefuehrt (Task-6-seitig nur Dokumentation)
- [x] Keine Breaking Changes an bestehenden OSC-Adressen eingefuehrt (Task-6-seitig nur Dokumentation)
- [ ] Fehlerbehandlung fuer alle Randfaelle abschliessend in Bitwig verifiziert
- [ ] Alle neuen Parameter per `markInterested()` in Bitwig-Laufzeit validiert
- [ ] Integer-Parsing ueber alle neuen OSC-Einstiegspunkte in Runtime-Tests abgesichert
- [ ] OSC-Antworten als begin/item/end-Folge in Bitwig-Laufzeit validiert
- [ ] Bestehende Resolution-Logik fuer Werte-Konvertierung in Bitwig-Laufzeit validiert
- [ ] Selection-Following-Verhalten bei Device-Wechsel in Bitwig-Laufzeit validiert

Hinweis: Die offenen Punkte benoetigen eine lauffaehige Bitwig-Instanz mit
aktivierter OSC-Extension.

## Testplan fuer manuelle Verifikation in Bitwig

```bash
# Bitwig starten, OSC-Extension laden, Serum 2 auf eine Spur laden

# 1. Parameteranzahl abfragen:
oscsend localhost 8000 /device/allparams/count

# 2. Alle Parameter auflisten:
oscsend localhost 8000 /device/allparams/list
#    -> Erwartung: begin-Nachricht, deutlich mehr als 8 item-Nachrichten, end-Nachricht

# 3. Einen eindeutig erkennbaren Parameter per Index setzen (z.B. Filter Cutoff):
oscsend localhost 8000 /device/allparams/42/value f 0.75

# 4. Parameter per Name setzen:
oscsend localhost 8000 /device/allparams/byname si "Osc A Level" 0.8

# 5. Device in Bitwig wechseln (anderes Plugin selektieren):
oscsend localhost 8000 /device/allparams/list
#    -> Erwartung: Parameter des neuen Devices, nicht des alten

# 6. Bestehende Adressen pruefen (Rueckwaertskompatibilitaet):
oscsend localhost 8000 /device/param/1/value f 0.5
#    -> Muss weiterhin funktionieren wie bisher
```

## Konsolidierung bestehender Task-Dokumente

In diesem Branch sind derzeit keine separaten Dateien `ANALYSIS.md`,
`PARAMETER_ANALYSIS.md`, `ARCHITECTURE.md`, `CHANGES_FRAMEWORK.md` oder
`CHANGES_OSC.md` vorhanden. Dieses Dokument uebernimmt daher die
Task-6-Zusammenfassung zentral.

## Bekannte Limitierungen

- Ohne erfolgreichen Maven-Dependency-Download ist keine vollstaendige
  CI-aehnliche Validierung lokal reproduzierbar.
- Die funktionale Endabnahme der OSC-Erweiterung kann nur in Bitwig mit
  geladenem Ziel-Plugin (z. B. Serum 2) erfolgen.
