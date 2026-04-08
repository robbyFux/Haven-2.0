---
date: "2026-04-03 00:00"
promoted: false
---

Phase 4 NotificationEngine: verschlüsselte Medien müssen vor dem Versenden entschlüsselt werden (Keystore-Key ist gerätegebunden/nicht exportierbar). Pattern: decryptToFile() → temp Datei anhängen → nach Send löschen (try/finally). Nie .enc Dateien direkt versenden. Transportverschlüsselung (Signal E2E / HTTPS) übernimmt für den Versand.
