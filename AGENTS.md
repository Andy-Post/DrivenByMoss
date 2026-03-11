# Codex Agent Instructions

## Projekt

Erweiterung der OSC-Schnittstelle von DrivenByMoss, damit saemtliche per
Bitwig-API exponierten VST-Parameter eines geladenen Plugins (primaerer
Anwendungsfall: Serum 2) per OSC gelesen und geschrieben werden koennen --
ohne Begrenzung auf 8 Parameter, ohne manuelle Page-Navigation.

Repository-Ursprung: https://github.com/git-moss/DrivenByMoss (LGPLv3, Release 26.6.0)

## Stack

- Java (JDK 21+)
- Maven 3.x (`mvn clean install -DskipTests`)
- Bitwig Controller API (Observer-basiert, reaktiv)
- OSC ueber UDP

## Build

```bash
mvn clean install -DskipTests
```

Der Build muss nach jeder Aenderung erfolgreich durchlaufen. Compiler-Fehler
und -Warnungen sind vollstaendig zu beheben, bevor ein Task als abgeschlossen
gilt.

## Relevante Verzeichnisse

```
src/main/java/de/mossgrabers/controller/osc/    # OSC-Controller (Hauptarbeitsbereich)
src/main/java/de/mossgrabers/framework/          # Framework-Kern
src/main/java/de/mossgrabers/framework/daw/      # DAW-Abstraktionsschicht
src/main/java/de/mossgrabers/bitwig/              # Bitwig-spezifische Implementierung
```

## Wahrscheinlich betroffene Dateien

- `OSCControllerSetup.java`
- `DeviceModule.java` (im OSC-Pfad)
- `CursorDeviceImpl.java`
- `ModelSetup.java`
- Eventuell: `SpecificDeviceImpl.java`, gemeinsames Device-Interface

Diese Liste ist ein Hinweis, kein Dogma. Task 2 verifiziert die tatsaechlich
betroffenen Dateien durch Quellcode-Analyse.

## Konventionen

- Bestehenden Code-Stil exakt uebernehmen (Einrueckung, Naming, Kommentarsprache,
  JavaDoc-Format) -- kein eigenes Format einfuehren
- Kommentare im Stil des Originals (Sprache und Detailgrad aus dem Bestandscode ableiten)
- Neue Konstanten zentral ablegen, nicht verstreut im Code
- Bevorzuge Komposition gegenueber Vererbung oder tiefgreifenden Aenderungen
  an bestehenden Framework-Klassen

## Kritische Einschraenkungen

- Die bestehende page-basierte Parameterlogik (8 Parameter, `/device/param/1-8/...`)
  darf NICHT veraendert werden -- volle Rueckwaertskompatibilitaet ist Pflicht
- Keine externen Abhaengigkeiten hinzufuegen -- DrivenByMoss hat ausser der
  Bitwig API keine externen Dependencies, das muss so bleiben
- NIEMALS `Integer.MAX_VALUE` als Bankgroesse verwenden -- Maximum ist 512
  (als benannte Konstante: `EXTENDED_PARAMETER_BANK_SIZE`)
- Parameternamen NIEMALS als Teil einer OSC-Adresse verwenden (Sonderzeichen-
  Problem) -- immer als OSC-Argument uebergeben
- Keine Spekulation: Wenn die Bitwig API etwas nicht hergibt, Limitierung
  dokumentieren statt erraten

## Neue OSC-Adressen (Zielzustand)

```
/device/allparams/count             # Gesamtanzahl aktiver Parameter
/device/allparams/{index}/value     # Lesen/Setzen per Index
/device/allparams/{index}/name      # Parametername abfragen
/device/allparams/list              # Kompletter Dump (begin/item/end-Folge)
/device/allparams/list/{offset}     # Dump ab Offset (optional)
/device/allparams/byname            # Setzen per Name (Name als Argument, nicht in Adresse)
```

Antwortformat fuer `/device/allparams/list`:
```
/device/allparams/begin    [String deviceName, Int parameterCount]
/device/allparams/item     [Int index, String name, String displayValue, Float normalizedValue]
/device/allparams/end      []
```

## Fehlerbehandlung

Alle OSC-Inputs sind als potenziell fehlerhaft zu behandeln:
- Kein Device: `/device/allparams/error` mit `"no device selected"`
- Index out of range: `/device/allparams/error` mit `"index out of range: {index}, max: {max}"`
- Name nicht gefunden: `/device/allparams/error` mit `"parameter not found: {name}"`
- Integer-Parsing immer absichern (NumberFormatException abfangen)

## Erwartete Ergebnisdateien pro Task

- Task 1: `ANALYSIS.md`
- Task 2: `PARAMETER_ANALYSIS.md`
- Task 3: `ARCHITECTURE.md`
- Task 4: `CHANGES_FRAMEWORK.md`
- Task 5: `CHANGES_OSC.md`
- Task 6: `README_EXTENSION.md`
- Task 7: `serum2_param_dump.py`, `serum2_mapping.json`
