# Haven Modern - Neuimplementierung der Haven App

## 1. Projektziel und strategische Einordnung

Ziel des Projekts **Haven Modern** ist die Entwicklung einer hochsicheren, privacy-zentrierten Android-Applikation zur physischen Raum- und Geräteüberwachung.  
Die App kombiniert:

- die sensorische Tiefe und Bedrohungslogik der ursprünglichen Haven-App,
    - referenzen/haven-0.2.1/
    - https://github.com/guardianproject/haven
- nutzererprobte Konzepte aus Alfred Camera (Live-Streaming, Multi-Device, Zonen, Schedules),
    - referenzen/AlfredCamera/
    - https://alfred.camera/de
- sowie neue, strategisch differenzierende Funktionen (lokale KI, Change Detection, Gerätemanipulations-Erkennung).

**Leitprinzipien:**
- Local-first statt Cloud-first  
- On-device Intelligence statt externer Inferenz  
- Self-hosted Infrastruktur statt Vendor Lock-in  
- Sensorfusion statt isolierter Trigger  

---

## 2. Zielplattform und Rahmenbedingungen

### 2.1 Plattform
- **Android**
  - `targetSdkVersion`: 35 (Android 15)
  - `minSdkVersion`: 26 (Android 8.0)

### 2.2 Technologische Leitlinien
- Programmiersprache: Kotlin
- Kamera: CameraX (Jetpack)
- ML: TensorFlow Lite (quantisierte Modelle)
- Streaming: WebRTC
- Verschlüsselung: Android Keystore + AES-GCM
- Backend: optional, self-hosted

---

## 3. Systemarchitektur (High Level)

### 3.1 Mobile App (Android)
Modulare Architektur:

- **Sensor Core**
  - Accelerometer, Gyroskop, Magnetometer, Light, Audio
- **Media Core**
  - Kamera (Foto/Video), Audioaufnahme
- **Detection Engine**
  - Heuristische Trigger
  - ML-basierte Klassifikation
- **Event Engine**
  - Regelwerk, Priorisierung, Eskalation
- **Secure Storage**
  - Verschlüsselte Medien & Metadaten
- **RTC / Networking**
  - WebRTC Client, Signaling, TURN
- **UI / Control Layer**
  - Monitoring, Timeline, Konfiguration

### 3.2 Privates Backend
- HTTPS Signaling API (JWT)
- WebRTC SFU (z. B. Janus / mediasoup)
- TURN Server (coturn)
- Multi-Device / Multi-Instance fähig
- Keine unverschlüsselte Medienspeicherung

---

## 4. Funktionale Anforderungen

### 4.1 Grundfunktionen (Haven-Baseline)

- Überwachung folgender Sensoren:
  - Kamera (Bewegung / Bildaufnahme)
  - Mikrofon (Geräuschpegel / Peaks)
  - Beschleunigungssensor
  - Gyroskop
  - Lichtsensor
  - Magnetometer (optional)
- Ereignisbasierte Aktionen:
  - Start/Stop Aufnahme
  - Lokale Alarme
  - Upload an privates Backend
  - Push-Benachrichtigungen

---

### 4.2 Erweiterte Sensorfusion & Kalibrierung

**Ziel:** Minimierung von False Positives und Geräteabhängigkeiten.

**Anforderungen:**
- Nutzung von unkalibrierten Sensoren (`*_UNCALIBRATED`) mit Fallback
- Sensorfusion mittels:
  - Complementary Filter
  - Optional Kalman Filter
- Kalibrierungs-Wizard:
  - Geführte Bewegungssequenz
  - Persistentes Geräte-Profil
- Adaptive, lernfähige Schwellenwerte

**Abnahmekriterium:**  
Nach Kalibrierung messbar stabilere Detektion (Jitter-Reduktion dokumentiert).

---

### 4.3 Intelligente Aktivitäts- & Personenerkennung (Alfred-inspiriert)

**Ziel:** Kontextualisierung von Ereignissen.

**Detektionsklassen:**
- Person
- Tier (optional)
- Fahrzeug (optional)
- Allgemeine Bewegung
- Geräuschereignis

**Anforderungen:**
- On-device ML (TensorFlow Lite)
- Ausgabe:
  - Bounding Boxes
  - Confidence Scores
  - Objektklassen
- Kombination aus:
  - ML-Erkennung
  - Heuristischer Bewegungserkennung
- Privacy-Modus:
  - Keine Cloud-Inference
  - Optional nur Metadaten-Upload

---

### 4.4 Zonen- und Zeitsteuerung (aus Alfred übernommen)

**Ziel:** Präzise, kontextabhängige Detektion.

**Anforderungen:**
- Definition von Erkennungszonen (ROI) im Kamerabild
- Zeitpläne (Schedules):
  - Aktiv / Inaktiv
  - Nachtmodus
- Modus-Konzepte:
  - Armed
  - Disarmed
  - Night
  - Presence-Aware

---

### 4.5 Live-Streaming & Multi-Viewer

**Ziel:** Echtzeit-Lagebild.

**Anforderungen:**
- WebRTC-basierter Live-Stream
- Adaptive Bitrate
- Digitaler Zoom im Live-Feed
- Multi-Viewer-Unterstützung
- Ende-zu-Ende-Verschlüsselung

---

### 4.6 Zwei-Wege-Audio (Interaktion)

**Ziel:** Aktive Interaktion und Abschreckung.

**Anforderungen:**
- Push-to-Talk über WebRTC
- Optional automatische Aktivierung bei Detektion
- Latenzoptimierung

---

### 4.7 Bewegungserkennung des Telefons („Telefon eingeschlossen“-Modus)

**Ziel:** Erkennung physischer Manipulation des Geräts selbst.

**Anforderungen:**
- High-Pass-Filter auf Accelerometer + Gyroskop
- RMS-Analyse mit Zeitfenster
- Hysterese & Debounce
- Optional ML-Klassifikator:
  - Umweltvibration vs. Entnahme / Bewegung

**Auslösung:**
- Sofortige Aufzeichnung
- Alarmereignis

---

### 4.8 Foto-Vorher-Nachher-Vergleich (Change Detection)

**Ziel:** Feststellung räumlicher Veränderungen.

**Anforderungen:**
- Baseline-Referenzaufnahme
- Vergleichsaufnahme bei Ereignis
- Algorithmen:
  - Perceptual Hash (pHash)
  - SSIM
  - Differenzbild mit Beleuchtungsnormalisierung
- Ergebnis:
  - Change-Score
  - Heatmap
- Verschlüsselte Speicherung

---

### 4.9 Ereignis-Timeline & Archiv (Alfred-Konzept erweitert)

**Ziel:** Forensische Nachvollziehbarkeit.

**Anforderungen:**
- Zeitachse aller Ereignisse
- Segmentierte Event-Clips
- Metadaten:
  - Detektionstyp
  - Sensorwerte
  - Zeit / Modus
- Lokale oder private Backend-Speicherung

---

### 4.10 Multi-Device & Pairing

**Ziel:** Skalierbare Überwachung.

**Anforderungen:**
- QR-Code-basiertes Secure Pairing
- Geräte-Rollen:
  - Admin
  - Viewer
  - Read-Only
- Zentrale Verwaltung über Backend (optional)

---

## 5. Nicht-funktionale Anforderungen

### 5.1 Sicherheit
- Lokale Verschlüsselung aller Medien
- Android Keystore für Schlüsselmaterial
- TLS / DTLS / SRTP
- Optional: Play Integrity / Attestation
- Audit-Logs

### 5.2 Datenschutz
- Local-first Default
- Keine Drittanbieter-Cloud
- Minimale Metadaten
- Lösch- & Exportfunktionen

### 5.3 Performance & Energie
- Ereigniserkennung < 500 ms
- Adaptive Sensor-Sampling-Raten
- Dokumentierter Batterieverbrauch

---

## 6. Berechtigungen (Android)

- CAMERA
- RECORD_AUDIO
- FOREGROUND_SERVICE (+ Service Types)
- POST_NOTIFICATIONS (Android 13+)
- BODY_SENSORS (falls erforderlich)
- ACCESS_FINE_LOCATION (optional, begründet)
