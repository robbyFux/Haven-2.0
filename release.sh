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

if git status --porcelain | grep -q '^[^?]'; then
    warn "Es gibt uncommittete Änderungen. Bitte zuerst committen oder stashen."
    git status --short
    read -rp "Trotzdem fortfahren? [j/N] " CONFIRM
    [[ "$CONFIRM" =~ ^[jJyY]$ ]] || exit 1
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

# ─── 2. Android APK bauen ─────────────────────────────────────────────────────
step "2. Android APK bauen"

APK_DEST=""

if [[ "$SKIP_APK" == true ]]; then
    warn "APK-Build übersprungen (--skip-apk)"
elif [[ "$DRY_RUN" == true ]]; then
    info "[dry-run] Würde ausführen: ./gradlew :app:assembleRelease"
else
    if [[ -z "${HAVEN_KEYSTORE_PATH:-}" ]]; then
        warn "HAVEN_KEYSTORE_PATH nicht gesetzt — APK wird unsigniert gebaut"
        warn "Für signierte Releases: HAVEN_KEYSTORE_PATH, HAVEN_KEYSTORE_PASSWORD, HAVEN_KEY_ALIAS, HAVEN_KEY_PASSWORD setzen"
    fi

    ./gradlew :app:assembleRelease --no-daemon

    APK_SRC=$(find app/build/outputs/apk/release -name "*.apk" 2>/dev/null | head -1)
    [[ -z "$APK_SRC" ]] && error "Kein APK gefunden nach dem Build"

    APK_DEST="$DIST_DIR/haven-v${VERSION}.apk"
    cp "$APK_SRC" "$APK_DEST"
    ok "APK: dist/$VERSION/haven-v${VERSION}.apk"
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

# ─── 4. Git-Commit und Tag ────────────────────────────────────────────────────
step "4. Git-Commit und Tag erstellen"

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
step "5. GitHub Release erstellen"

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
