- # Haven 2.0 – Projekt-Dokumentation

  **Haven 2.0** ist eine moderne, privacy-zentrierte und hochsichere Überwachungslösung für Android. Sie verwandelt jedes Android-Smartphone in ein intelligentes Sicherheitssystem, das physische Räume überwacht und Ereignisse lokal sowie in einer optionalen, selbstgehosteten Cloud verarbeitet.

  ## 🛡️ Unsere Leitprinzipien
  *   **Local-first:** Alle Analysen finden primär direkt auf dem Gerät statt.
  *   **On-device AI:** Künstliche Intelligenz zur Objekterkennung läuft lokal, um maximale Privatsphäre zu gewährleisten
  *   **Self-hosted Backend:** Du behältst die volle Kontrolle über deine Daten durch eine eigene Server-Infrastruktur
  *   **Sensorfusion:** Durch die intelligente Kombination verschiedener Sensoren werden Fehlalarme drastisch reduziert

  ---

  ## 📱 Die Haven 2.0 Android-App

  Die App nutzt die gesamte sensorische Tiefe deines Smartphones, um Bewegungen, Geräusche und Manipulationen zu erkennen.

  ### Intelligente Erkennung (3-Stufen-Pipeline)
  Um den Akku zu schonen und Fehlalarme (wie Lichtflackern) zu vermeiden, nutzt Haven eine hocheffiziente Kamera-Pipeline:
  1.  **Luminanz-Differenz:** Erkennt blitzschnell Pixelveränderungen im Bild.
  2.  **Strukturanalyse (pHash):** Prüft, ob sich die Struktur im Bild wirklich geändert hat oder nur das Licht flackert.
  3.  **KI-Klassifizierung:** Nutzt lokale KI (TensorFlow Lite / MediaPipe), um gezielt nach **Personen, Tieren oder Fahrzeugen** zu suchen.

  ### Umfassende Sensor-Überwachung
  *   **Bewegung & Erschütterung:** Kombiniert Beschleunigungssensor und Gyroskop (Sensorfusion), um zwischen Vibrationen und echtem Bewegen des Geräts zu unterscheiden.
  *   **Akustik:** Überwachung der Umgebungslautstärke basierend auf absoluten Dezibel-Werten.
  *   **Licht:** Erkennt plötzliche Helligkeitsänderungen (z.B. Einschalten einer Taschenlampe) durch eine adaptive Baseline.
  *   **Erkennungszonen (ROI):** Du kannst spezifische Bereiche im Kamerabild definieren, die überwacht werden sollen.

  ### Sicherheit & Privatsphäre
  *   **Verschlüsseltes Archiv:** Alle Aufnahmen werden mit AES-GCM (Android Keystore) verschlüsselt gespeichert.
  *   **Stop-Cooldown:** Beim Beenden der Überwachung werden die letzten 30 Sekunden automatisch gelöscht, damit du dich nicht selbst beim Abschalten aufnimmst.
  *   **App-Sperre:** Schutz der App durch einen PIN (SHA-256 + Salt).
  *   **Kein Tracking:** Wir nutzen keine Google-Cloud-Dienste, Firebase oder Analytics.

  ---

  ## ☁️ Haven Cloud-Server & Web-UI (Optional)

  Für Nutzer, die mehrere Geräte verwalten oder von überall auf ihre Ereignisse zugreifen möchten, bietet Haven 2.0 ein leistungsstarkes, selbstgehostetes Backend.

  ### Funktionen des Cloud-Servers
  *   **Zentrale Verwaltung:** Verwalte mehrere Haven-Instanzen über ein einziges Konto mit sicherer User-Key-Authentifizierung.
  *   **Sichere Speicherung:** Verschlüsselter Upload von Ereignissen und Video-Clips von deinen Geräten.
  *   **Cloud-KI:** Optionale Zweitmeinung durch KI-Analyse im Backend (lokales Modell oder via API).
  *   **Multi-User Support:** Unterstützung für mehrere Benutzer mit individuellen Quotas und Berechtigungen.

  ### Web-Interface
  *   **Event-Browser:** Filtere und betrachte Ereignisse und Videos direkt im Browser.
  *   **Admin-Dashboard:** Behalte den Überblick über Speicherverbrauch, Statistiken und Benutzerkonten.
  *   **Sicherheit:** Unterstützung für Zwei-Faktor-Authentifizierung (2FA/TOTP).

  ---

  ## 🔔 Benachrichtigungen (NotificationEngine)
  Haven 2.0 informiert dich sofort über Ereignisse über verschiedene Kanäle:
  *   **Signal:** Direkte Alarmierung über die signal-cli REST API (inklusive Bildanhängen).
  *   **Mattermost:** Integration via Webhooks.
  *   **Pushover:** Priorisierte Push-Nachrichten für kritische Vorfälle.
  *   **Heartbeat:** Regelmäßige Status-Updates (Akkustand, Uptime) pro Kanal.

  ---

  ## 🚀 Status & Roadmap
  *   **Aktueller Stand:** Phase 5 (Cloud-Server), Web-UI (Phase 6) und Optimierung für Android 16 (Phase 7) sind abgeschlossen.
  *   **Zukunft:** Erweiterte Zeitpläne, Live-Streaming via WebRTC und Erkennung von Telefon-Manipulationen.
