# CHANGES_FRAMEWORK

## Was wurde geändert?

- Neue zentrale Konstante `DeviceConstants.EXTENDED_PARAMETER_BANK_SIZE = 512` eingeführt.
- `ModelSetup` um das Flag `wantsExtendedParameterBank` ergänzt (inkl. Getter/Setter), damit die erweiterte Parameter-Bank nur bei Bedarf instanziiert wird.
- Neues Framework-Datenobjekt `FullParameterInfo` ergänzt (`index`, `name`, `normalizedName`, `normalizedValue`, `displayValue`, `exists`).
- `ISpecificDevice` um Methoden für den erweiterten Parameterzugriff ergänzt:
  - `getAllParameters()`
  - `getParameterByFullIndex(int)`
  - `getParameterByName(String)`
  - `getExistingParameterCount()`
- Neue Kompositionsklasse `ExtendedParameterBankImpl` (Bitwig-Schicht) hinzugefügt:
  - initialisiert eine 512er-Bank in 64 Seiten à 8 Parameter,
  - ruft für jeden Parameter `markInterested()` auf `exists`, `name`, `value`, `displayedValue` auf,
  - hält Lookup-Strukturen für `index -> Parameter` und `normalizedName -> index` bereit,
  - filtert unbelegte Slots (nur `exists && !name.isBlank()`).
- `SpecificDeviceImpl` minimal erweitert, um optional eine `ExtendedParameterBankImpl` zu halten und die neuen `ISpecificDevice`-Methoden zu bedienen.
- `CursorLayerImpl` auf die erweiterte `SpecificDeviceImpl`-Signatur angepasst.

## Warum wurde das geändert?

- Die Framework-Schicht benötigt einen einheitlichen, seitenunabhängigen Zugriff auf alle per Bitwig-API verfügbaren Plugin-Parameter.
- Die 512er-Grenze ist als zentrale Konstante festgelegt, um harte Werte in der OSC-Implementierung zu vermeiden.
- Durch die optionale Aktivierung über `ModelSetup` wird vermieden, dass andere Controller automatisch zusätzliche Observer/Remote-Control-Seiten erzeugen.

## Mögliche Seiteneffekte

- Sobald `wantsExtendedParameterBank` aktiv ist, werden zusätzliche Observer und `CursorRemoteControlsPage`-Instanzen erzeugt; das kann je nach Projekt-/Plugin-Größe mehr API- und CPU-Last verursachen.
- Bei mehrfach vorkommenden Parameternamen liefert der Name-Lookup bewusst den ersten Treffer (stabile, deterministische Zuordnung, aber keine Mehrfachauflösung).
- Für nicht aktivierte Extended-Bank liefern die neuen Methoden leere/`null`-Ergebnisse; aufrufender Code muss diese Fälle behandeln.
