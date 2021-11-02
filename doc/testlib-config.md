# Tiger test lib configuration

Im aktuellen Verzeichnis kann eine _tiger.yaml_ Konfigurationsdatei abgelegt werden um 
Das Verhalten der Tiger test lib anzupassen.

```yaml
 rbelPathDebugging: false
 # Flag um das Tracing des Rbel Path Executors zu aktivieren.
 # Bei aktiviertem Flag schreibt der Executor alle Suchschritte in
 # den einzelnen Ebenen des Dokuments in die Konsole.
 rbelAnsiColors: true
 # Standardmäßig schreibt der Rbel Path Executor die Ausgaben mit Hilfe
 # von ANSI Steuersequenzen in Farbe in die Konsole. Sollte die Konsole Ihres
 # Betriebssystem keine ANSI Sequenzen unterstützen, so können Sie diese mit diesem Flag deaktivieren
```
