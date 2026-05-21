#!/usr/bin/env bash
# Haven 2.0 Release-Script
# Nutzung: ./release.sh [OPTIONEN] [VERSION]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ─── Farben ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GRN='\033[0;32m'
YLW='\033[1;33m'
BLU='\033[0;34m'
BLD='\033[1m'
NC='\033[0m'

info()    { echo -e "${BLU}[INFO]${NC}  $*"; }
ok()      { echo -e "${GRN}[OK]${NC}    $*"; }
warn()    { echo -e "${YLW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }
step()    { echo -e "\n${BLD}▶ $*${NC}"; }

# ─── Optionen ─────────────────────────────────────────────────────────────────
DRY_RUN=false
SKIP_APK=false
SKIP_DOCKER=false
PUSH_DOCKER=false
VERSION=""

usage() {
    cat <<EOF
Nutzung: $(basename "$0") [OPTIONEN] [VERSION]

Erstellt ein Haven 2.0 Release (APK + Cloud-Server Docker-Images + GitHub Release).

Argumente:
  VERSION              Semantische Version, z.B. 2.1.0 (interaktiv abgefragt wenn weggelassen)

Optionen:
  --dry-run            Alle Schritte anzeigen ohne sie auszuführen
  --skip-apk           Android-APK-Build überspringen
  --skip-docker        Docker-Build und -Export überspringen
  --push-docker        Images zu Docker Hub pushen statt als .tar.gz zu exportieren
  -h, --help           Diese Hilfe anzeigen

Umgebungsvariablen für APK-Signierung:
  HAVEN_KEYSTORE_PATH      Pfad zur .jks Keystore-Datei
  HAVEN_KEYSTORE_PASSWORD  Keystore-Passwort
  HAVEN_KEY_ALIAS          Key-Alias im Keystore
  HAVEN_KEY_PASSWORD       Key-Passwort

Beispiele:
  $(basename "$0") 2.1.0
  $(basename "$0") --dry-run 2.1.0
  $(basename "$0") --skip-docker 2.1.0
  HAVEN_KEYSTORE_PATH=~/.android/release.jks $(basename "$0") 2.1.0

Docker-Archiv laden:
  docker load < dist/2.1.0/haven-cloud-v2.1.0.tar.gz
EOF
    exit 0
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)     DRY_RUN=true; shift ;;
        --skip-apk)    SKIP_APK=true; shift ;;
        --skip-docker) SKIP_DOCKER=true; shift ;;
        --push-docker) PUSH_DOCKER=true; shift ;;
        -h|--help)     usage ;;
        -*)            error "Unbekannte Option: $1 (--help für Hilfe)" ;;
        *)             VERSION="$1"; shift ;;
    esac
done

# ─── Version bestimmen ────────────────────────────────────────────────────────
if [[ -z "$VERSION" ]]; then
    CURRENT=$(grep 'versionName' app/build.gradle.kts | grep -oP '"\K[^"]+')
    echo -e "${BLD}Aktuelle Version:${NC} $CURRENT"
    read -rp "Neue Version eingeben (z.B. 2.1.0): " VERSION
fi

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    error "Ungültiges Format: '$VERSION'. Erwartet: MAJOR.MINOR.PATCH (z.B. 2.1.0)"
fi

TAG="v$VERSION"

echo -e "\n${BLD}Haven 2.0 Release — $TAG${NC}"
[[ "$DRY_RUN" == true ]] && warn "DRY-RUN-Modus — es werden keine Änderungen vorgenommen"

# ─── Voraussetzungen prüfen ───────────────────────────────────────────────────
step "Voraussetzungen prüfen"

GH_OK=true
DOCKER_OK=true

command -v gh     &>/dev/null || { warn "gh CLI nicht gefunden — GitHub Release wird übersprungen"; GH_OK=false; }
command -v docker &>/dev/null || { warn "Docker nicht gefunden — Docker-Build wird übersprungen"; DOCKER_OK=false; }

if git tag --list | grep -q "^${TAG}$"; then
    error "Tag $TAG existiert bereits. Bitte andere Version wählen."
fi

# Uncommittete Änderungen vor dem Release sichern
if git status --porcelain | grep -q '^[^?]'; then
    DIRTY_FILES=$(git status --short | grep '^[^?]')
    info "Uncommittete Änderungen werden vor dem Release gesichert:"
    echo "$DIRTY_FILES"
    if [[ "$DRY_RUN" == true ]]; then
        info "[dry-run] Würde committen und pushen: alle geänderten Dateien"
    else
        git add -u
        git commit -m "chore: sync local changes before $TAG release"
        git push origin "$(git rev-parse --abbrev-ref HEAD)"
        ok "Lokale Änderungen committed und gepusht"
    fi
fi

# ─── Ausgabeverzeichnis ───────────────────────────────────────────────────────
DIST_DIR="$SCRIPT_DIR/dist/$VERSION"
[[ "$DRY_RUN" == false ]] && mkdir -p "$DIST_DIR"
info "Artefakte werden nach dist/$VERSION/ geschrieben"

# ─── 1. Versionsnummern aktualisieren ─────────────────────────────────────────
step "1. Versionsnummern aktualisieren"

OLD_CODE=$(grep 'versionCode' app/build.gradle.kts | grep -oP '\d+')
OLD_NAME=$(grep 'versionName' app/build.gradle.kts | grep -oP '"\K[^"]+')
NEW_CODE=$((OLD_CODE + 1))

OLD_SERVER=$(grep '^version = ' server/pyproject.toml | grep -oP '"\K[^"]+')

info "Android: versionName $OLD_NAME → $VERSION  |  versionCode $OLD_CODE → $NEW_CODE"
info "Server:  version $OLD_SERVER → $VERSION (pyproject.toml)"

if [[ "$DRY_RUN" == false ]]; then
    sed -i "s/versionCode = $OLD_CODE/versionCode = $NEW_CODE/" app/build.gradle.kts
    sed -i "s/versionName = \"$OLD_NAME\"/versionName = \"$VERSION\"/" app/build.gradle.kts
    sed -i "s/^version = \"$OLD_SERVER\"/version = \"$VERSION\"/" server/pyproject.toml
    ok "Versionsdateien aktualisiert"
fi

# ─── Hilfsfunktionen ──────────────────────────────────────────────────────────
find_apksigner() {
    if command -v apksigner &>/dev/null; then
        echo "apksigner"; return
    fi
    if [[ -n "${ANDROID_HOME:-}" ]]; then
        local bt
        bt=$(find "$ANDROID_HOME/build-tools" -name "apksigner" -type f 2>/dev/null | sort -V | tail -1)
        [[ -n "$bt" ]] && echo "$bt" && return
    fi
    echo ""
}

# ─── 2. Android APK bauen ─────────────────────────────────────────────────────
step "2. Android APK bauen"

APK_DEST=""
APK_SIGNED_TYPE="unsigned"

if [[ "$SKIP_APK" == true ]]; then
    warn "APK-Build übersprungen (--skip-apk)"
elif [[ "$DRY_RUN" == true ]]; then
    info "[dry-run] Würde ausführen: ./gradlew :app:assembleRelease"
    info "[dry-run] APK würde signiert: release-key (HAVEN_KEYSTORE_PATH gesetzt) oder dev-key"
else
    if [[ -n "${HAVEN_KEYSTORE_PATH:-}" ]]; then
        info "APK wird durch Gradle mit Release-Key signiert"
    else
        warn "HAVEN_KEYSTORE_PATH nicht gesetzt — APK wird nach dem Build mit Dev-Key signiert"
        warn "Für Release-Signierung: HAVEN_KEYSTORE_PATH, HAVEN_KEYSTORE_PASSWORD, HAVEN_KEY_ALIAS, HAVEN_KEY_PASSWORD setzen"
    fi

    ./gradlew :app:assembleRelease --no-daemon

    APK_SRC=$(find app/build/outputs/apk/release -name "*.apk" 2>/dev/null | head -1)
    [[ -z "$APK_SRC" ]] && error "Kein APK gefunden nach dem Build"

    APK_DEST="$DIST_DIR/haven-v${VERSION}.apk"

    if [[ -n "${HAVEN_KEYSTORE_PATH:-}" ]]; then
        cp "$APK_SRC" "$APK_DEST"
        APK_SIGNED_TYPE="release"
        ok "APK (release-signiert): dist/$VERSION/haven-v${VERSION}.apk"
    else
        APKSIGNER=$(find_apksigner)
        if [[ -z "$APKSIGNER" ]]; then
            warn "apksigner nicht gefunden — APK bleibt unsigniert (nur ADB-Install möglich)"
            warn "Tipp: ANDROID_HOME setzen oder apksigner in PATH legen"
            cp "$APK_SRC" "$APK_DEST"
        else
            DEV_KEYSTORE="$HOME/.android/haven-dev.jks"
            if [[ ! -f "$DEV_KEYSTORE" ]]; then
                info "Erstelle Dev-Keystore: $DEV_KEYSTORE"
                keytool -genkey -v \
                    -keystore "$DEV_KEYSTORE" \
                    -alias haven-dev \
                    -keyalg RSA -keysize 2048 -validity 10000 \
                    -dname "CN=Haven Dev, O=Haven, C=DE" \
                    -storepass android -keypass android \
                    -noprompt 2>/dev/null
                ok "Dev-Keystore erstellt: $DEV_KEYSTORE"
            fi
            "$APKSIGNER" sign \
                --ks "$DEV_KEYSTORE" \
                --ks-alias haven-dev \
                --ks-pass pass:android \
                --key-pass pass:android \
                --out "$APK_DEST" \
                "$APK_SRC"
            APK_SIGNED_TYPE="dev"
            ok "APK (dev-signiert): dist/$VERSION/haven-v${VERSION}.apk"
        fi
    fi
fi

# ─── 3. Docker-Images bauen und exportieren ───────────────────────────────────
step "3. Cloud-Server Docker-Images"

DOCKER_ARCHIVE="$DIST_DIR/haven-cloud-v${VERSION}.tar.gz"

if [[ "$SKIP_DOCKER" == true ]]; then
    warn "Docker übersprungen (--skip-docker)"
elif [[ "$DOCKER_OK" == false ]]; then
    warn "Docker nicht verfügbar — übersprungen"
elif [[ "$DRY_RUN" == true ]]; then
    info "[dry-run] Würde bauen: haven-api:$VERSION, haven-webui:$VERSION"
    info "[dry-run] Würde exportieren: dist/$VERSION/haven-cloud-v${VERSION}.tar.gz"
else
    info "Baue haven-api:$VERSION (API + Worker)..."
    docker build \
        -t "haven-api:$VERSION" \
        -t "haven-api:latest" \
        server/

    info "Baue haven-webui:$VERSION (Web-UI)..."
    docker build \
        -t "haven-webui:$VERSION" \
        -t "haven-webui:latest" \
        -f server/Dockerfile.webui \
        server/

    if [[ "$PUSH_DOCKER" == true ]]; then
        info "Pushe Images zu Docker Hub..."
        docker push "haven-api:$VERSION"
        docker push "haven-api:latest"
        docker push "haven-webui:$VERSION"
        docker push "haven-webui:latest"
        ok "Docker-Images gepusht"
    else
        info "Exportiere Images als Archiv..."
        docker save "haven-api:$VERSION" "haven-webui:$VERSION" | gzip > "$DOCKER_ARCHIVE"
        ok "Docker-Archiv: dist/$VERSION/haven-cloud-v${VERSION}.tar.gz"
        info "Laden mit: docker load < dist/$VERSION/haven-cloud-v${VERSION}.tar.gz"
    fi
fi

# ─── 4. Installationsanleitung generieren ────────────────────────────────────
step "4. Installationsanleitung generieren"

INSTALL_GUIDE="$DIST_DIR/INSTALL.md"

if [[ "$DRY_RUN" == true ]]; then
    info "[dry-run] Würde generieren: dist/$VERSION/INSTALL.md"
else
    # APK-Abschnitt abhängig vom Signing-Status
    if [[ "$APK_SIGNED_TYPE" == "release" ]]; then
        cat > "$INSTALL_GUIDE" <<EOF
# Haven 2.0 ${TAG} – Schnellstart

## Android-App installieren (haven-v${VERSION}.apk)

Das APK ist mit dem **Release-Key signiert**.

### Option A: ADB
\`\`\`bash
adb install haven-v${VERSION}.apk
\`\`\`

### Option B: Dateimanager
1. APK auf das Gerät übertragen (USB / Download)
2. **Einstellungen → Apps → Spezieller App-Zugriff → Unbekannte Apps installieren**
   → Dateimanager → *Aus dieser Quelle zulassen*
3. APK im Dateimanager antippen → *Installieren*

---
EOF
    elif [[ "$APK_SIGNED_TYPE" == "dev" ]]; then
        cat > "$INSTALL_GUIDE" <<EOF
# Haven 2.0 ${TAG} – Schnellstart

## Android-App installieren (haven-v${VERSION}.apk)

Das APK ist mit einem **Dev-Key signiert** — Android zeigt beim ersten Install eine einmalige Sicherheitswarnung.

### Option A: ADB
\`\`\`bash
adb install haven-v${VERSION}.apk
\`\`\`

### Option B: Dateimanager
1. APK auf das Gerät übertragen (USB / Download)
2. **Einstellungen → Apps → Spezieller App-Zugriff → Unbekannte Apps installieren**
   → Dateimanager → *Aus dieser Quelle zulassen*
3. APK im Dateimanager antippen → *Installieren*

---
EOF
    else
        cat > "$INSTALL_GUIDE" <<EOF
# Haven 2.0 ${TAG} – Schnellstart

## Android-App installieren (haven-v${VERSION}.apk)

Das APK ist **unsigniert** — Installation nur via \`adb install\` möglich (nicht per Dateimanager).

### ADB
\`\`\`bash
adb install haven-v${VERSION}.apk
\`\`\`

---
EOF
    fi

    # Cloud-Server-Abschnitt (immer gleich)
    cat >> "$INSTALL_GUIDE" <<EOF

## Cloud-Server installieren (haven-cloud-v${VERSION}.tar.gz)

### Voraussetzungen
- Docker Engine 24+ und Docker Compose v2
- Freie Ports 8000 (API) und 8080 (Web UI)

### 1. Images laden
\`\`\`bash
docker load < haven-cloud-v${VERSION}.tar.gz
\`\`\`

### 2. Konfiguration anlegen
\`\`\`bash
mkdir haven-server && cd haven-server
\`\`\`

\`docker-compose.yml\` erstellen:
\`\`\`yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: haven
      POSTGRES_PASSWORD: haven
      POSTGRES_DB: haven
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U haven -d haven"]
      interval: 10s
      timeout: 5s
      retries: 5
  redis:
    image: redis:7-alpine
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
  app:
    image: haven-api:${VERSION}
    command: uvicorn app.main:app --host 0.0.0.0 --port 8000
    ports: ["8000:8000"]
    volumes: ["./media:/app/media"]
    env_file: .env
    depends_on:
      db: {condition: service_healthy}
      redis: {condition: service_healthy}
  worker:
    image: haven-api:${VERSION}
    command: celery -A app.celery_app worker --loglevel=info --concurrency=2
    volumes: ["./media:/app/media"]
    env_file: .env
    depends_on:
      db: {condition: service_healthy}
      redis: {condition: service_healthy}
  webui:
    image: haven-webui:${VERSION}
    command: >
      sh -c "python manage.py migrate --noinput &&
             python manage.py runserver 0.0.0.0:8080"
    ports: ["8080:8080"]
    volumes: ["./media:/app/media"]
    env_file: .env
    depends_on:
      db: {condition: service_healthy}
volumes:
  postgres_data:
\`\`\`

\`.env\` erstellen:
\`\`\`ini
DATABASE_URL=postgresql+asyncpg://haven:haven@db:5432/haven
REDIS_URL=redis://redis:6379/0
# mind. 64 zufällige Zeichen: python3 -c "import secrets; print(secrets.token_hex(32))"
SECRET_KEY=hier-zufaelligen-schluessel-eintragen
DJANGO_SECRET_KEY=\${SECRET_KEY}
ALLOWED_HOSTS=localhost,127.0.0.1
\`\`\`

### 3. Stack starten
\`\`\`bash
docker compose up -d
docker compose exec app alembic upgrade head
\`\`\`

### 4. Admin-Nutzer anlegen
\`\`\`bash
docker compose exec app python -c "
import asyncio, secrets
from passlib.context import CryptContext
from app.database import AsyncSessionLocal
from app.models.user import User
pwd = CryptContext(schemes=['bcrypt'], deprecated='auto')
async def run():
    async with AsyncSessionLocal() as db:
        u = User(username='admin', password_hash=pwd.hash('changeme'),
                 user_key='haven_u_' + secrets.token_hex(16), is_admin=True)
        db.add(u); await db.commit()
        print('User-Key:', u.user_key)
asyncio.run(run())
"
\`\`\`

Passwort danach unter **Web UI → Profil → Passwort ändern** setzen.

### 5. Dienste prüfen
| URL | Beschreibung |
|-----|-------------|
| http://localhost:8000/health | API Health-Check |
| http://localhost:8080/ | Web UI |

### Android-App verbinden
Einstellungen → Cloud-Server → API-URL: \`http://<server-ip>:8000\`
EOF
    ok "Anleitung: dist/$VERSION/INSTALL.md"
fi

# ─── 5. Git-Commit und Tag ────────────────────────────────────────────────────
step "5. Git-Commit und Tag erstellen"


if [[ "$DRY_RUN" == true ]]; then
    info "[dry-run] Würde committen: app/build.gradle.kts, server/pyproject.toml"
    info "[dry-run] Würde Tag setzen: $TAG"
else
    git add app/build.gradle.kts server/pyproject.toml
    git commit -m "chore: bump version to $VERSION"
    git tag "$TAG"
    ok "Commit und Tag $TAG erstellt"
fi

# ─── 5. Push und GitHub Release ───────────────────────────────────────────────
step "6. GitHub Release erstellen"

if [[ "$GH_OK" == false ]]; then
    warn "gh CLI nicht verfügbar — GitHub Release übersprungen"
    info "Manuell: git push origin main && git push origin $TAG"
    info "Dann: gh release create $TAG --title 'Haven 2.0 $TAG' --generate-notes"
elif [[ "$DRY_RUN" == true ]]; then
    info "[dry-run] Würde pushen: main + $TAG"
    info "[dry-run] Würde GitHub Release '$TAG' anlegen mit auto-generierten Changelog-Notes"
else
    git push origin main
    git push origin "$TAG"

    RELEASE_ASSETS=()
    [[ -n "$APK_DEST" && -f "$APK_DEST" ]] && RELEASE_ASSETS+=("$APK_DEST")
    [[ -f "$DOCKER_ARCHIVE" ]]              && RELEASE_ASSETS+=("$DOCKER_ARCHIVE")
    [[ -f "$INSTALL_GUIDE" ]]               && RELEASE_ASSETS+=("$INSTALL_GUIDE")

    gh release create "$TAG" \
        --title "Haven 2.0 $TAG" \
        --generate-notes \
        "${RELEASE_ASSETS[@]}"

    REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
    ok "GitHub Release: https://github.com/$REPO/releases/tag/$TAG"
fi

# ─── Zusammenfassung ──────────────────────────────────────────────────────────
echo
echo -e "${GRN}${BLD}✓ Release $TAG abgeschlossen!${NC}"
if [[ "$DRY_RUN" == false ]]; then
    echo    "  Artefakte: dist/$VERSION/"
    [[ -f "$APK_DEST" ]]       && echo "  APK:       dist/$VERSION/haven-v${VERSION}.apk"
    [[ -f "$DOCKER_ARCHIVE" ]] && echo "  Docker:    dist/$VERSION/haven-cloud-v${VERSION}.tar.gz"
fi
