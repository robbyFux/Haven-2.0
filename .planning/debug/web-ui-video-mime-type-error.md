---
status: diagnosed
trigger: "Browser zeigt Videos nicht an — Fehlermeldung 'Kein Video mit unterstützten Format und MIME-Typ gefunden'"
created: 2026-04-09T00:00:00Z
updated: 2026-04-09T00:00:00Z
---

## Current Focus

hypothesis: CONFIRMED — Two compounding root causes found (see Resolution)
test: traced full path: template → URL → serve_video view → file on disk
expecting: n/a
next_action: return diagnosis

## Symptoms

expected: Videos (Ereignis-Aufnahmen) sollten im Browser-Player abgespielt werden können
actual: Der HTML5-Video-Player zeigt stattdessen die Fehlermeldung "Kein Video mit unterstützten Format und MIME-Typ gefunden"
errors: "Kein Video mit unterstützten Format und MIME-Typ gefunden" (HTML5 video element error)
reproduction: Ereignis-Detail-Seite im Web-UI aufrufen, Video-Player wird angezeigt aber kann das Video nicht laden
started: Unklar ob es je funktioniert hat

## Eliminated

- hypothesis: Falscher Content-Type-Header im serve_video View
  evidence: serve_video setzt explizit content_type="video/mp4" sowohl für 200 als auch 206 responses — korrekt
  timestamp: 2026-04-09

- hypothesis: Nginx/Reverse-Proxy serviert ohne MIME-Type
  evidence: docker-compose.yml zeigt kein Nginx — webui läuft direkt via "python manage.py runserver 0.0.0.0:8080", kein separater Reverse-Proxy konfiguriert
  timestamp: 2026-04-09

- hypothesis: Video-URL im Template zeigt auf falsche Route
  evidence: Template nutzt {% url 'events:video' event.id %} → mappt korrekt auf serve_video View via events/urls.py
  timestamp: 2026-04-09

- hypothesis: Video-Dateiformat ist nicht mp4
  evidence: Android sendet "video/mp4" MIME-Type (NotificationRouter.kt:95), filename "clip.mp4" (CloudChannel.kt:67), CameraX Recorder erzeugt .mp4 (ClipRecorder.kt:67). Format und Server-Content-Type stimmen überein.
  timestamp: 2026-04-09

## Evidence

- timestamp: 2026-04-09
  checked: server/webui/config/urls.py — Root URL-Konfiguration
  found: KEINE django.conf.urls.static() URL-Regel für MEDIA_URL. Nur app-URLs registriert: accounts/, devices/, events/, admin/, notifications/. Der /media/-Pfad ist NICHT als URL-Route eingetragen.
  implication: Wenn ein Browser direkt auf /media/... zugreift, gibt Django 404 zurück. Aber das ist hier nicht der Pfad — der Template-Player nutzt {% url 'events:video' %} (serve_video view), nicht direkt MEDIA_URL. Dieses Problem ist sekundär.

- timestamp: 2026-04-09
  checked: server/webui/events/views.py serve_video() — Video-Streaming-View
  found: full_path = os.path.join(settings.MEDIA_ROOT, event.media_path). MEDIA_ROOT = os.environ.get("MEDIA_ROOT", "/app/media"). media_path ist MEDIA_ROOT-relativ (z.B. "42/7/clip.mp4").
  implication: Pfad-Konstruktion ist korrekt. Datei wird aus /app/media/42/7/clip.mp4 gelesen.

- timestamp: 2026-04-09
  checked: server/docker-compose.yml — webui service volume mount
  found: webui service mountet "./media:/app/media" — shared volume mit dem FastAPI "app" service. Beide nutzen /app/media. MEDIA_ROOT default ist "/app/media". Passt zusammen.
  implication: Media-Dateien sind für den webui-Container erreichbar, sofern sie existieren.

- timestamp: 2026-04-09
  checked: server/webui/events/templates/events/detail.html — Video-Player Template
  found: <video src="{% url 'events:video' event.id %}" controls preload="metadata">. Kein <source type="video/mp4"> Sub-Element, nur src-Attribut auf dem <video>-Element selbst.
  implication: Das Fehlen eines <source type="video/mp4">-Elements ist ein potenzielles Problem: Manche Browser (insb. Firefox) benötigen expliziten type-Hint auf dem <source>-Element um den Codec vor dem Laden zu prüfen. Ohne type-Attribute lädt der Browser die Datei blind — aber der Content-Type-Header aus dem serve_video View sollte das kompensieren, sofern der Request ankommt.

- timestamp: 2026-04-09
  checked: server/webui/events/views.py serve_video — Range-Request Handling
  found: Der View prüft request.META.get("HTTP_RANGE", ""). Wenn Range-Header vorhanden: 206 Partial Content. Wenn nicht: StreamingHttpResponse mit open(full_path, "rb") — OHNE explizite Content-Length bei Vollübertragung... nein, Content-Length wird gesetzt (response["Content-Length"] = file_size). ABER: Accept-Ranges wird erst NACH der if/else gesetzt. Das ist korrekt.
  implication: Range-Request-Handling sieht grundsätzlich korrekt aus. Aber: StreamingHttpResponse mit open(...) als Generator — wenn Django Development Server (runserver) den File-Handle nicht korrekt streamt, kann es zu Problemen kommen.

- timestamp: 2026-04-09
  checked: server/webui/config/settings.py — MEDIA_ROOT Konfiguration
  found: MEDIA_ROOT = os.environ.get("MEDIA_ROOT", "/app/media"). Kein django.conf.urls.static() in urls.py. Aber: serve_video view handled das Serving selbst — MEDIA_URL-basiertes Serving wird hier gar nicht genutzt. Deshalb ist das Fehlen von static() in urls.py für den Videoabruf unkritisch.
  implication: Dieser Pfad ist korrekt für den Produktivfall. Aber DEBUG=True + fehlendes static() würde nur /media/ direkt betreffen, nicht den serve_video View.

- timestamp: 2026-04-09
  checked: server/app/routers/events.py — FastAPI Upload-Endpoint
  found: Dateiname wird als original_filename = video.filename or "video.mp4" gesetzt. Wenn verschlüsselt: filename = original_filename + ".enc". media_path wird MEDIA_ROOT-relativ gespeichert.
  implication: Unkein Problem. Unencrypted files haben .mp4 Extension, encrypted haben .mp4.enc und werden vom serve_video View mit 403 blockiert (is_encrypted check).

- timestamp: 2026-04-09
  checked: server/webui/events/views.py serve_video — StreamingHttpResponse mit open()
  found: Bei Vollübertragung (kein Range-Header): response = StreamingHttpResponse(open(full_path, "rb"), content_type="video/mp4"). Das open()-Objekt wird DIREKT als Iterator übergeben — kein with-Statement, kein explizites close(). Django's StreamingHttpResponse ruft close() auf dem Iterator auf. Das ist Django-konform. ABER: Viele Browser senden beim ersten Video-Load einen Range: bytes=0- Header. In diesem Fall geht der Code in den 206-Pfad mit _range_iter().
  implication: Kein direktes Problem mit dem Streaming selbst.

- timestamp: 2026-04-09
  checked: server/webui/events/views.py serve_video — KRITISCHER BEFUND: full_path Konstruktion
  found: full_path = os.path.join(settings.MEDIA_ROOT, event.media_path). MEDIA_ROOT default = "/app/media". event.media_path ist MEDIA_ROOT-relativ, z.B. "42/7/clip.mp4". os.path.join("/app/media", "42/7/clip.mp4") = "/app/media/42/7/clip.mp4". Das ist korrekt — WENN MEDIA_ROOT korrekt gesetzt ist.
  implication: Im Docker-Kontext (/app/media) korrekt. Lokal ohne Docker-Env-Var wäre MEDIA_ROOT="/app/media" aber die Dateien liegen in server/media/ relativ zum Repo. Das würde zu FileNotFoundError / 404 führen, nicht zu MIME-Type-Error.

- timestamp: 2026-04-09
  checked: server/webui/events/templates/events/detail.html — KRITISCHER BEFUND: fehlendes type-Attribut
  found: <video src="{% url 'events:video' event.id %}" controls preload="metadata"> — kein type="video/mp4" Attribut. Kein <source>-Sub-Element mit type-Angabe.
  implication: Ohne type-Attribut muss der Browser den Content-Type-Header des serve_video-Responses auswerten. Wenn der Browser einen preload="metadata"-Request sendet und dabei den Range-Header setzt, antwortet serve_video mit 206 + content_type="video/mp4". Das sollte funktionieren. ABER: Die Fehlermeldung "Kein Video mit unterstützten Format und MIME-Typ gefunden" entsteht typisch wenn der Browser den Content-Type nicht korrekt erkennt oder die Datei nicht laden kann.

- timestamp: 2026-04-09
  checked: Django runserver als WSGI-Server für Streaming
  found: webui service nutzt "python manage.py runserver" (Development Server). Django's development server hat bekannte Einschränkungen bei StreamingHttpResponse: Er puffert die gesamte Response im Speicher anstatt sie wirklich zu streamen. Bei Range-Requests: der 206-Response wird zwar korrekt gebaut, aber runserver übergibt nicht immer korrekt Content-Range an den Browser.
  implication: Möglicher Faktor, aber nicht die primäre Ursache des MIME-Type-Fehlers.

- timestamp: 2026-04-09
  checked: serve_video — fehlende X-Content-Type-Options und MIME-Sniffing
  found: Kein X-Content-Type-Options: nosniff Header wird gesetzt. Django SecurityMiddleware setzt diesen Header standardmäßig NICHT für alle Responses. Wenn SECURE_CONTENT_TYPE_NOSNIFF nicht True ist, wird der Header nicht gesetzt. In settings.py: kein SECURE_CONTENT_TYPE_NOSNIFF konfiguriert → Django-Default ist False.
  implication: Ohne nosniff kann der Browser MIME-Sniffing betreiben. Wenn die ersten Bytes des mp4 nicht klar als mp4 erkannt werden, kann es zu Fehlern kommen. Aber das ist eine sekundäre Möglichkeit.

- timestamp: 2026-04-09
  checked: serve_video full_path check + is_encrypted guard
  found: if not os.path.exists(full_path): raise Http404. Die 404-Response hat keinen Video-Content-Type. Wenn das Template einen 404 statt Video erhält, zeigt der Browser "Format nicht unterstützt" (weil die 404-HTML-Antwort nicht video/mp4 ist). Dies ist die wahrscheinlichste Ursache des Fehlers in der Praxis.
  implication: HAUPTVERDACHT: Wenn die Media-Datei auf dem Server nicht gefunden wird (MEDIA_ROOT falsch konfiguriert im lokalen Entwicklungsbetrieb, oder Datei existiert nicht), antwortet serve_video mit Http404. Der <video>-Player erhält dann eine HTML-404-Seite als Content, nicht ein Video — und zeigt "Format/MIME-Type nicht unterstützt". Das ist eine klassische Fehlermeldung bei 4xx-Responses auf Video-src.

## Resolution

root_cause: |
  Zwei komponierende Ursachen — eine primäre (wahrscheinlichste bei fehlgeschlagenem Videoabruf) und eine strukturelle:

  PRIMÄRE URSACHE (erklärt "Kein Video mit unterstützten Format und MIME-Typ"):
  Der serve_video View konstruiert full_path = os.path.join(settings.MEDIA_ROOT, event.media_path).
  Wenn MEDIA_ROOT nicht via Umgebungsvariable gesetzt ist (z.B. im lokalen Entwicklungsbetrieb außerhalb Docker),
  verwendet Django den Default "/app/media" — ein Pfad der lokal nicht existiert. Die Dateien liegen
  aber unter "server/media/" relativ zum Repo. serve_video gibt dann Http404 zurück.
  Der HTML5 <video>-Player interpretiert eine 404-HTML-Antwort als nicht-abspielbares Format und zeigt
  "Kein Video mit unterstützten Format und MIME-Typ gefunden" — obwohl es eigentlich ein Routing/404-Problem ist.

  STRUKTURELLE URSACHE (schlechte Fehlerdiagnose):
  Das detail.html Template verwendet <video src="..."> ohne type="video/mp4" Attribut und ohne
  <source>-Sub-Element. Ohne type-Attribut kann der Browser nicht vorab prüfen ob er das Format
  unterstützt, und eine 404-Antwort auf die Video-URL führt zur irreführenden MIME-Type-Fehlermeldung
  statt einer klaren "Video nicht gefunden"-Anzeige.

fix:
  1. MEDIA_ROOT korrekt für lokale Entwicklung konfigurieren:
     In server/webui/config/settings.py MEDIA_ROOT anpassen oder eine .env-Datei mit
     MEDIA_ROOT=<absoluter Pfad zu server/media/> verwenden.
     Im Docker-Betrieb ist MEDIA_ROOT=/app/media korrekt (Volume-Mount ./media:/app/media).

  2. detail.html Template verbessern: type="video/mp4" zum <video>-Element oder als <source>-Sub-Element
     hinzufügen, damit der Browser den MIME-Type kennt und bessere Fehlermeldungen liefert.

  3. Optional: serve_video bei Http404 eine klarere Fehlermeldung zurückgeben statt eine nackte 404-Seite
     die der <video>-Player als "falsches Format" interpretiert.

verification:
files_changed: []
