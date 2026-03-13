# CHANGES_OSC

## Neue/geaenderte Dateien

- `src/main/java/de/mossgrabers/controller/osc/module/DeviceModule.java`
  - Neue OSC-Handler fuer `/device/allparams/...` (count, list, list/{offset}, byname, {index}/value, {index}/name)
  - Fehlerausgabe ueber `/device/allparams/error`
  - List-Dump-Response als begin/item/end
  - Zyklische Ausgabe von `/device/allparams/count` im normalen Flush
- `src/main/java/de/mossgrabers/controller/osc/OSCControllerSetup.java`
  - `EXTENDED_PARAMETER_BANK_SIZE = 512` eingefuehrt
  - Erweiterte Parameterliste nur fuer OSC ueber `ModelSetup#setNumListParams(...)` aktiviert
- `src/main/java/de/mossgrabers/framework/osc/IOpenSoundControlWriter.java`
  - Overload fuer mehrwertige OSC-Nachrichten (`List<?>`) hinzugefuegt
- `src/main/java/de/mossgrabers/framework/osc/AbstractOpenSoundControlWriter.java`
  - Implementierung des neuen `sendOSC(..., List<?>, ...)`-Overloads

## Neues Adressschema

### Eingehend

- `/device/allparams/{index}/value`  
  Setzt Parameter per Index mit bestehender Zahlen-/Resolution-Logik.
- `/device/allparams/{index}/name`  
  Antwortet mit dem Parameternamen unter derselben Adresse.
- `/device/allparams/byname`  
  Erwartet OSC-Argumente `[String name, Float/Number value]`, setzt Parameter per Name.
- `/device/allparams/list`  
  Startet kompletten Parameter-Dump.
- `/device/allparams/list/{offset}`  
  Startet Parameter-Dump ab Index `offset`.
- `/device/allparams/count`  
  Triggert eine sofortige Antwort mit der aktiven Parameteranzahl.

### Ausgehend

- `/device/allparams/count` -> `[Int parameterCount]`
- `/device/allparams/{index}/name` -> `[String name]`
- `/device/allparams/begin` -> `[String deviceName, Int parameterCount]`
- `/device/allparams/item` -> `[Int index, String name, String displayValue, Float normalizedValue]`
- `/device/allparams/end` -> `[]`
- `/device/allparams/error` -> `[String message]`

## Testszenarien (manuell/mental)

- Kein selektiertes Device:
  - Input: `/device/allparams/list`
  - Erwartung: `/device/allparams/error ["no device selected"]`
- Ungueltiger Index (zu gross):
  - Input: `/device/allparams/999/value <v>` bei z. B. 300 Parametern
  - Erwartung: `/device/allparams/error ["index out of range: 999, max: 299"]`
- Negativer Index:
  - Input: `/device/allparams/-1/value <v>`
  - Erwartung: `/device/allparams/error ["index out of range: -1, max: ..."]`
- Nicht numerischer Index:
  - Input: `/device/allparams/abc/value <v>`
  - Erwartung: `/device/allparams/error ["index out of range: abc, max: ..."]`
- Name nicht gefunden:
  - Input: `/device/allparams/byname ["does_not_exist", <v>]`
  - Erwartung: `/device/allparams/error ["parameter not found: does_not_exist"]`
- List-Dump:
  - Input: `/device/allparams/list/32`
  - Erwartung: begin/item/end-Sequenz ab Index 32.
