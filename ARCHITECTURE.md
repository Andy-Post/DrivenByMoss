# Architektur-Entwurf: OSC-Erweiterung fuer vollstaendigen Plugin-Parameterzugriff

## 1) Architekturentscheidung

**Entscheidung:** Umsetzung ueber eine **separate erweiterte Parameter-Bank** parallel zur bestehenden 8er-Remote-Control-Bank.

- Bestehende OSC-Logik (`/device/param/{1-8}/...`) bleibt unveraendert.
- Neue OSC-Endpunkte werden ausschliesslich unter `/device/allparams/...` eingefuehrt.
- Datenquelle fuer den Vollzugriff ist die bereits vorhandene `IParameterList` am `ISpecificDevice`.

### Begruendung

1. **Rueckwaertskompatibilitaet:** Die bestehende page-basierte 8er-Logik in `DeviceModule` bleibt unangetastet.
2. **Saubere Trennung:** Bestehendes Verhalten und erweiterter Vollzugriff sind im OSC-Adressraum klar separiert.
3. **Architekturfit:** Das Modell hat bereits die noetigen Schichten (OSC-Modul -> DAW-Abstraktion -> Bitwig-Implementierung), inklusive `ISpecificDevice#getParameterList()`.
4. **Skalierbarkeit:** Erweiterte Bankgroesse wird explizit auf **512** begrenzt (`EXTENDED_PARAMETER_BANK_SIZE`), ohne `Integer.MAX_VALUE`.

---

## 2) Zielbild Datenfluss

1. OSC-Client sendet Befehl an `/device/allparams/...`.
2. `DeviceModule` parst den Pfad und arbeitet auf `ICursorDevice`.
3. Zugriff auf Parameter erfolgt ueber `cursorDevice.getParameterList().getParameters()`.
4. Lesen/Schreiben erfolgt auf `IParameter`-Objekten (`getName`, `getDisplayedValue`, `getValue`, `setValue`).
5. Antworten/Fehler werden als dedizierte OSC-Messages gesendet.

**Wichtig:** Namensbasierter Zugriff nutzt den Parameternamen immer als OSC-Argument, nie in der Adresse.

---

## 3) Vollstaendiges OSC-Adressschema

## Bestehend (unveraendert)

- `/device/param/{1-8}/...`
- inklusive bestehender Page-/Bank-Navigation

## Neu (erweiterter Parameterzugriff)

- `/device/allparams/count`
  - Zweck: Anzahl verfuegbarer Parameter der erweiterten Bank melden.
  - Antwort: numerischer Wert (0..512).

- `/device/allparams/{index}/value`
  - Zweck: Parameterwert per Index lesen/setzen.
  - Lesen: bei Trigger/Abfrage Antwort mit aktuellem Wert.
  - Schreiben: setzt den Wert auf den uebergebenen numerischen OSC-Wert.

- `/device/allparams/{index}/name`
  - Zweck: Parameternamen per Index auslesen.

- `/device/allparams/list`
  - Zweck: Voll-Dump aller verfuegbaren Parameter in begin/item/end-Folge.

- `/device/allparams/list/{offset}`
  - Zweck: Optionaler Teil-Dump ab Offset.
  - Seitengroesse: **64** Items.

- `/device/allparams/byname`
  - Zweck: Setzen per Namen.
  - Argumente: `[String name, Float value]`.
  - Name wird ausschliesslich als Argument verarbeitet.

Ergaenzend fuer Fehlerfaelle:

- `/device/allparams/error`
  - Antwortkanal fuer alle Validierungs-/Parsing-Fehler.

---

## 4) Antwortformate

## 4.1 `/device/allparams/list` und `/device/allparams/list/{offset}`

Antwort als zusammenhaengende Sequenz:

- `/device/allparams/begin [String deviceName, Int parameterCount]`
- `/device/allparams/item [Int index, String name, String displayValue, Float normalizedValue]`
- `/device/allparams/end []`

Hinweise:

- `index` folgt dem in `DeviceModule` ueblichen numerischen Indexschema (bestehendes 1-basiertes OSC-Schema konsistent weiterverwenden).
- Bei Offset-Variante werden nur max. 64 Eintraege ab Offset geliefert, aber gleiche begin/item/end-Struktur.

## 4.2 Einzelantworten

- `/device/allparams/count [Int count]`
- `/device/allparams/{index}/name [String name]`
- `/device/allparams/{index}/value [Float normalizedValue]`

---

## 5) Werte-Konvertierung

Die Erweiterung verwendet **keine neue Werte-Skalierung**.

- Setter/Getter nutzen die vorhandene DrivenByMoss-Wertlogik (`IParameter#setValue(int)` / `IParameter#getValue()`), die mit der bestehenden OSC-Value-Resolution (Low/Medium/High) arbeitet.
- Die Resolution wird weiterhin zentral ueber `OSCConfiguration` und den in `OSCControllerSetup` konfigurierten `valueChanger` gesteuert.

Damit bleiben bestehende OSC-Clients konsistent zu allen anderen Parameter-Kommandos.

---

## 6) Fehlerbehandlung

Alle fehlerhaften Inputs liefern:

- `/device/allparams/error [String message]`

Fehlermeldungen:

1. Kein Device selektiert:
   - `"no device selected"`
2. Index ausserhalb Bereich:
   - `"index out of range: {index}, max: {max}"`
3. Name nicht gefunden:
   - `"parameter not found: {name}"`
4. Ungueltiges Integer-Parsing (Index/Offset):
   - Abfangen von `NumberFormatException`, Rueckgabe ueber `/device/allparams/error`.

Sonderfall:

- Device ohne verfuegbare Parameter -> `/device/allparams/count` liefert `0`.

---

## 7) Verifizierte Liste der zu aendernden / neuen Dateien

## A) Sicher zu aendern

1. `src/main/java/de/mossgrabers/controller/osc/module/DeviceModule.java`
   - Neue Parser-Zweige fuer `/device/allparams/...`
   - Antworten (`count`, `name`, `value`, `list`, `error`)
   - Name-basierter Setter
   - Index-/Offset-Validierung und Fehlerpfad

2. `src/main/java/de/mossgrabers/controller/osc/OSCControllerSetup.java`
   - Aktivierung/Dimensionierung der erweiterten Parameterliste im `ModelSetup` (z. B. `setNumListParams(EXTENDED_PARAMETER_BANK_SIZE)`).

## B) Wahrscheinlich zu aendern

3. `src/main/java/de/mossgrabers/framework/daw/ModelSetup.java`
   - Falls fuer die Erweiterung eine zentrale Konstante oder Guardrails fuer List-Parameter (<=512) notwendig sind.

## C) Eher nicht zu aendern (nach Code-Analyse)

4. `src/main/java/de/mossgrabers/bitwig/framework/daw/data/CursorDeviceImpl.java`
5. `src/main/java/de/mossgrabers/bitwig/framework/daw/data/SpecificDeviceImpl.java`
6. `src/main/java/de/mossgrabers/framework/daw/data/ISpecificDevice.java`

Begruendung:

- `ISpecificDevice#getParameterList()` ist bereits vorhanden.
- `SpecificDeviceImpl` erzeugt bereits `ParameterListImpl` aus `numListParams`.
- Der fehlende Schritt liegt primaer in der OSC-Nutzung und in der Aktivierung einer ausreichend grossen List-Konfiguration im Setup.

## D) Keine neue externe Dependency, kein Umbau der 8er-Page-Logik

- Keine Drittbibliotheken.
- Bestehende `/device/param/{1-8}/...`-Semantik bleibt unveraendert.
