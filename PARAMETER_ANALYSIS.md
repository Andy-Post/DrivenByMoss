# Task 2 - Analyse der bestehenden Parameter-Behandlung

## 1) Ergebnis der breiten Quellcode-Suche

Aus den geforderten Suchen ergeben sich folgende relevante Ketten:

- `ModelSetup` fuehrt `numParamPages` und `numParams` (Default jeweils `8`).
- `ModelImpl` liest diese Werte aus `ModelSetup` und gibt sie in alle Device-/Track-Wrapper weiter.
- `SpecificDeviceImpl` erzeugt die Device-Parameterbank ueber `device.createCursorRemoteControlsPage(checkedNumParams)`.
- `DeviceModule` exponiert per OSC nur `parameterBank.getPageSize()` Parameter und adressiert diese als `/.../param/{1..pageSize}/...`.

Damit liegt die effektive Begrenzung fuer OSC-Parameterzugriff in der Kette:

`OSCControllerSetup -> ModelSetup -> ModelImpl -> SpecificDeviceImpl/ParameterBankImpl -> DeviceModule`.

---

## 2) Exakte Stellen der Parameter-Limitierung

## A) Default-/Setup-Limit (8)

### `ModelSetup`
- Defaultwerte sind fest auf 8 gesetzt:

```java
private int numParamPages = 8;
private int numParams = 8;
```

Damit ist die Basis ohne explizite Konfiguration bereits 8/8.

### `OSCControllerSetup`
- Fuer OSC wird `bankPageSize` in `ModelSetup` gespiegelt:

```java
ms.setNumParamPages (bankPageSize);
ms.setNumParams (bankPageSize);
```

- `bankPageSize` kommt aus OSC-Settings; Default ist 8.

Wichtig: Das ist ein Konfigurationslimit, nicht die einzige technische Grenze.

## B) Framework-/Bitwig-Mapping-Limit (Remote-Controls-seitig)

### `SpecificDeviceImpl`

```java
final int checkedNumParamPages = numParamPages >= 0 ? numParamPages : 8;
final int checkedNumParams = numParams >= 0 ? numParams : 8;
...
final CursorRemoteControlsPage remoteControlsPage = this.device.createCursorRemoteControlsPage (checkedNumParams);
this.parameterBank = new ParameterBankImpl (..., remoteControlsPage, checkedNumParamPages, checkedNumParams);
```

Hier wird explizit **Cursor Remote Controls** verwendet (nicht eine All-Parameter-API).

### `ParameterBankImpl`

```java
super (host, numParams);
...
for (int i = 0; i < this.getPageSize (); i++)
    this.items.add (new ParameterImpl (..., this.remoteControls.getParameter (i), i, true));
```

`getPageSize()` entspricht `numParams`; es existieren nur so viele OSC-seitig adressierbare Parameter-Slots pro Page.

## C) OSC-Adressierungs-Limit (sichtbare Slots pro Page)

### `DeviceModule.flushDevice`

```java
for (int i = 0; i < parameterBank.getPageSize (); i++)
{
    final int oneplus = i + 1;
    this.flushParameterData (writer, deviceAddress + "param/" + oneplus + "/", parameterBank.getItem (i), dump);
}
```

=> Es werden nur `/param/1` bis `/param/N` (N = Page-Size) ausgegeben.

### `DeviceModule.parseDeviceValue`

```java
case TAG_PARAM:
    final String subCommand5 = getSubCommand (path);
    final int paramNo = Integer.parseInt (subCommand5) - 1;
    parseFXParamValue (parameterBank.getItem (paramNo), path, value);
```

=> Direkter Zugriff nur auf aktuelle Page-Slots, nicht auf globale Parameterindizes ueber alle Seiten.

---

## 3) Bestehende OSC-Adressen fuer Device-Parameter

## Cursor Device (`/device/...`)

- Parameter-Slot-Daten (pro Slot):
  - `/device/param/{i}/exists`
  - `/device/param/{i}/name`
  - `/device/param/{i}/valueStr`
  - `/device/param/{i}/value`
  - `/device/param/{i}/modulatedValue`

- Parameter schreiben/steuern:
  - `/device/param/{i}/value` (set)
  - `/device/param/{i}/indicate`
  - `/device/param/{i}/reset`
  - `/device/param/{i}/touched`

- Page-Navigation/-Auswahl:
  - `/device/page/select` bzw. `/device/page/selected` (numerisch)
  - `/device/page/{i}` (direkte Auswahl)
  - `/device/param/+` und `/device/param/-` (scroll remote controls)
  - `/device/param/bank/page/+` und `/device/param/bank/page/-`

- Last parameter:
  - `/device/lastparam/...` (gleiches Parameter-Subschema)

## Weitere Device-Wurzeln
Dasselbe Schema wird ebenfalls fuer folgende Wurzeln gespiegelt:
- `/primary/...`
- `/eq/...` (plus EQ-spezifische Kommandos)

## Parametername
- Ja, Name wird mitgesendet (`.../name`), aber als **OSC-Message-Value**, nicht in der Adresse.

---

## 4) Wertnormalisierung und Resolution-Konzept

## Interne Darstellung
- `ParameterImpl.getValue()` konvertiert Bitwig-normalisierte Werte (`0.0..1.0`) via `valueChanger.fromNormalizedValue(...)` in Integerbereich.
- `setValue(...)` geht umgekehrt ueber den `valueChanger` zur Bitwig-Normalisierung.

## OSC-Resolution (OSC-Konfiguration)
`OSCConfiguration.ValueResolution`:
- LOW: 128
- MEDIUM: 1024
- HIGH: 16384

`OSCControllerSetup` setzt dazu:
- LOW: `upperBound=128`, `stepSize=1`
- MEDIUM: `upperBound=1024`, `stepSize=8`
- HIGH: `upperBound=16384`, `stepSize=128`

=> OSC transportiert fuer Parameter aktuell ganzzahlige Werte im gewaehlten Bereich; Bitwig-seitig bleibt die Normalisierung auf `0..1`.

---

## 5) Framework-Schicht: sichtbare Pages vs. "alle" Parameter

- `ISpecificDevice` bietet beides an:
  - `getParameterBank()` (paged, Remote Controls)
  - `getParameterList()` (Listenabstraktion)

- Die aktuelle OSC-Implementierung (`DeviceModule`) nutzt **nur** `getParameterBank()`.
- `ParameterListImpl` existiert, basiert aber ebenfalls auf mehrfachen `CursorRemoteControlsPage`-Instanzen (8er-Slots pro Seite) und wird im OSC-Modul derzeit nicht verwendet.

Bewertung:
- Es gibt eine Abstraktion fuer "Liste", aber keine bestehende OSC-Strecke fuer globale Index-/Namensadressierung aller Plugin-Parameter.
- Die aktuelle Kette ist klar auf page-basiertes Remote-Control-Paradigma ausgelegt.

---

## 6) Bitwig-spezifische Implementierung und Cursor-Modus

- `ModelImpl` erzeugt den Haupt-Device-Zugriff mit:

```java
createCursorDevice (..., CursorDeviceFollowMode.FOLLOW_SELECTION)
```

- Dadurch folgt das Cursor-Device der aktuellen Device-Selektion in Bitwig.
- Die Parameterbank ist an dieses Cursor-Device bzw. jeweilige `SpecificDeviceImpl` gebunden.

Folge fuer die Anforderung:
- Auch wenn man die Bankgroesse erhoeht, bleibt man im Modell der Remote-Control-Pages des jeweils selektierten Devices.

---

## 7) Datenflussdiagramm (Ist-Zustand)

```text
OSC Input (/device/param/..)
  -> OSCParser.handle()
    -> DeviceModule.execute()/parseDeviceValue()
      -> ISpecificDevice.getParameterBank()
        -> ParameterBankImpl (CursorRemoteControlsPage)
          -> Bitwig API Parameter

Bitwig Parameter-Observer
  -> ParameterImpl / Bank-Wrapper
    -> DeviceModule.flushDevice()/flushParameterData()
      -> OSCWriter.flush()/sendOSC()
        -> OSC Output (/device/param/{1..N}/...)
```

---

## 8) Gesamtbewertung: Reicht Bankgroesse erhoehen?

Kurzfassung: **Nein, nicht ausreichend.**

Warum:
1. OSC-Adressierung ist aktuell explizit auf `/param/{slot}` innerhalb der aktuellen Page gebaut.
2. DeviceModule arbeitet ueber `IParameterBank`-Page-Slots, nicht ueber globale Parameterindizes.
3. Bitwig-Anbindung laeuft ueber `CursorRemoteControlsPage` (Remote-Control-Konzept), nicht ueber eine bereits vorhandene "all params by index/name"-Strecke.

Damit sind fuer den Zielzustand (`/device/allparams/...`) strukturelle Erweiterungen notwendig (zusaetzlicher Datenpfad + neue OSC-Adressen + Fehlerbehandlung), waehrend die bestehende 8er-/Page-Logik unveraendert fuer Rueckwaertskompatibilitaet erhalten bleiben muss.
