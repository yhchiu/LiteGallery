#!/usr/bin/env bash
#
# LiteGallery screenshot generator (emulator/device + adb).
#
# Pushes a set of demo photos/videos, puts the status bar into demo mode,
# then launches each screen of the app for every (language x theme)
# combination and saves a PNG via `adb exec-out screencap`.
#
# Requirements
#   - adb on PATH (Android SDK platform-tools)
#   - A running emulator (recommended: a "Google APIs" system image, NOT
#     "Google Play" — only the former allows `adb root`, which is needed to
#     open the non-exported screens directly: folder / settings / theme pack)
#   - A DEBUG build of LiteGallery installed (preferences are edited through
#     `run-as`, which only works on debuggable builds). Use --build to have
#     this script build and install it for you.
#   - Demo media in scripts/screenshot-media/<AlbumName>/*.jpg|*.mp4 ...
#     Each sub-folder becomes one album in the app.
#
# Usage
#   scripts/screenshots.sh [options]
#
# Options
#   --build               Run ./gradlew install<Flavor>Debug first
#   --flavor core|plus    Flavor to build (default: core)
#   --media DIR           Demo media root (default: scripts/screenshot-media)
#   --out DIR             Output directory (default: screenshots)
#   --locales "a b"       App languages: en zh_TW zh_CN (default: all three)
#   --themes "a b"        light / dark (default: "light dark")
#   --pack KEY            Theme pack: first_light, warm_paper, brutalist,
#                         editorial, monolith, prism (default: keep current)
#   --screens "a b"       home folder viewer settings themes trash
#                         (default: "home folder viewer settings themes")
#   --album NAME          Album used for the folder/viewer screens
#                         (default: first sub-folder that contains an image)
#   --photo FILE          File (inside --album) shown in the viewer
#                         (default: first image in the album, by name)
#   --wait SECONDS        Settle time after opening a screen (default: 3)
#   --skip-push           Don't (re)push demo media
#   --cleanup             Remove the pushed demo media from the device at the end
#   -h, --help            Show this help
#
# Device selection: set ANDROID_SERIAL=emulator-5554 when several devices
# are connected.
#
# On Windows run it from Git Bash or WSL.

set -euo pipefail

PKG="org.iurl.litegallery"
PREFS_FILE="shared_prefs/${PKG}_preferences.xml"
DEVICE_PICTURES="/sdcard/Pictures"
DEVICE_PATH_PREFIX="/storage/emulated/0/Pictures"
DEVICE_TMP="/data/local/tmp/litegallery_screens"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BUILD=0
FLAVOR="core"
MEDIA_DIR="$SCRIPT_DIR/screenshot-media"
OUT_DIR="$REPO_ROOT/screenshots"
LOCALES="en zh_TW zh_CN"
THEMES="light dark"
PACK=""
SCREENS="home folder viewer settings themes"
ALBUM=""
PHOTO=""
WAIT=3
SKIP_PUSH=0
CLEANUP=0

usage() { sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build) BUILD=1 ;;
    --flavor) FLAVOR="$2"; shift ;;
    --media) MEDIA_DIR="$2"; shift ;;
    --out) OUT_DIR="$2"; shift ;;
    --locales) LOCALES="$2"; shift ;;
    --themes) THEMES="$2"; shift ;;
    --pack) PACK="$2"; shift ;;
    --screens) SCREENS="$2"; shift ;;
    --album) ALBUM="$2"; shift ;;
    --photo) PHOTO="$2"; shift ;;
    --wait) WAIT="$2"; shift ;;
    --skip-push) SKIP_PUSH=1 ;;
    --cleanup) CLEANUP=1 ;;
    -h|--help) usage 0 ;;
    *) echo "Unknown option: $1" >&2; usage 1 ;;
  esac
  shift
done

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mWARN:\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# Quote a value for the device-side shell (adb shell joins argv into one string).
q() { printf "'%s'" "${1//\'/\'\\\'\'}"; }

dsh() { adb shell "$@" | tr -d '\r'; }

# ---------------------------------------------------------------- checks ----

command -v adb >/dev/null || die "adb not found. Install Android SDK platform-tools and add it to PATH."

if [[ "$BUILD" == 1 ]]; then
  case "$FLAVOR" in core|plus) ;; *) die "--flavor must be core or plus" ;; esac
  task="install$(tr '[:lower:]' '[:upper:]' <<< "${FLAVOR:0:1}")${FLAVOR:1}Debug"
  log "Building and installing ($task)"
  (cd "$REPO_ROOT" && ./gradlew "$task")
fi

adb get-state >/dev/null 2>&1 || die "No device/emulator connected (see 'adb devices'; set ANDROID_SERIAL if several)."

SDK="$(dsh getprop ro.build.version.sdk)"
log "Device API level: $SDK"

dsh pm path "$PKG" | grep -q package: || die "$PKG is not installed. Run with --build, or ./gradlew installCoreDebug."

if [[ "$(dsh "run-as $PKG id -u 2>&1")" =~ [^0-9] ]]; then
  die "run-as $PKG failed — install a DEBUG build (release builds are not debuggable)."
fi

# Try to get a root shell so non-exported activities can be started directly.
HAS_ROOT=0
if [[ "$(dsh id -u)" == "0" ]]; then
  HAS_ROOT=1
elif adb root >/dev/null 2>&1; then
  adb wait-for-device
  sleep 1
  [[ "$(dsh id -u)" == "0" ]] && HAS_ROOT=1
fi
if [[ "$HAS_ROOT" == 1 ]]; then
  log "Root shell available: all screens can be opened directly"
else
  warn "No root shell (use a 'Google APIs' emulator image for full coverage)."
  warn "Only 'home' and 'viewer' can be captured; other screens will be skipped."
fi

# ------------------------------------------------------------ demo media ----

[[ -d "$MEDIA_DIR" ]] || die "Demo media folder not found: $MEDIA_DIR (see scripts/screenshot-media/README.md)"

ALBUMS=()
while IFS= read -r d; do ALBUMS+=("$(basename "$d")"); done < <(
  find "$MEDIA_DIR" -mindepth 1 -maxdepth 1 -type d ! -name '.*' | sort)
[[ ${#ALBUMS[@]} -gt 0 ]] || die "No album sub-folders in $MEDIA_DIR (expected $MEDIA_DIR/<AlbumName>/photo.jpg)"

first_image() {
  find "$1" -maxdepth 1 -type f \
    \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' -o -iname '*.heic' \) \
    -exec basename {} \; | sort | head -n 1
}
if [[ -z "$ALBUM" ]]; then
  # Default: the first album (by name) that contains at least one image.
  for a in "${ALBUMS[@]}"; do
    if [[ -n "$(first_image "$MEDIA_DIR/$a")" ]]; then ALBUM="$a"; break; fi
  done
  [[ -n "$ALBUM" ]] || ALBUM="${ALBUMS[0]}"
fi
[[ -d "$MEDIA_DIR/$ALBUM" ]] || die "Album not found: $MEDIA_DIR/$ALBUM"
[[ -n "$PHOTO" ]] || PHOTO="$(first_image "$MEDIA_DIR/$ALBUM")"
[[ -n "$PHOTO" ]] || warn "No image in album '$ALBUM'; the viewer screen will be skipped."

url_encode_path() { # minimal: spaces and a few common characters
  local s="$1"
  s="${s//%/%25}"; s="${s// /%20}"; s="${s//#/%23}"; s="${s//\?/%3F}"
  printf '%s' "$s"
}

if [[ "$SKIP_PUSH" == 0 ]]; then
  log "Pushing ${#ALBUMS[@]} album(s) to $DEVICE_PICTURES"
  for a in "${ALBUMS[@]}"; do
    dsh rm -rf "$(q "$DEVICE_PICTURES/$a")" >/dev/null
    # adb push keeps the files' modification times, so date sorting stays meaningful.
    adb push "$MEDIA_DIR/$a" "$DEVICE_PICTURES/" >/dev/null
  done
  log "Asking MediaStore to index the pushed files"
  for a in "${ALBUMS[@]}"; do
    while IFS= read -r f; do
      rel="${f#"$MEDIA_DIR"/}"
      dsh am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
        -d "$(q "file://$(url_encode_path "$DEVICE_PATH_PREFIX/$rel")")" >/dev/null
    done < <(find "$MEDIA_DIR/$a" -type f ! -name '.*')
  done
  sleep 2
fi

# ----------------------------------------------------------- permissions ----

log "Granting storage permissions"
if (( SDK >= 33 )); then
  dsh pm grant "$PKG" android.permission.READ_MEDIA_IMAGES || true
  dsh pm grant "$PKG" android.permission.READ_MEDIA_VIDEO || true
else
  dsh pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE || true
  (( SDK <= 28 )) && { dsh pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE || true; }
fi

# ----------------------------------------------------------- preferences ----

WORK_DIR="$(mktemp -d)"
PREFS_BACKUP="$WORK_DIR/prefs_backup.xml"
HAD_PREFS=0
ORIG_UIMODE="$(dsh cmd uimode night 2>/dev/null | awk '{print $NF}')"

read_prefs() { adb exec-out "run-as $PKG cat $PREFS_FILE 2>/dev/null" || true; }

write_prefs() { # $1 = local file
  dsh mkdir -p "$DEVICE_TMP" >/dev/null
  adb push "$1" "$DEVICE_TMP/prefs.xml" >/dev/null
  dsh chmod 644 "$DEVICE_TMP/prefs.xml"
  dsh run-as "$PKG" mkdir -p shared_prefs
  dsh run-as "$PKG" cp "$DEVICE_TMP/prefs.xml" "$PREFS_FILE"
  dsh rm -f "$DEVICE_TMP/prefs.xml"
}

# set_prefs key=value [key=value ...]  — string preferences only
set_prefs() {
  local cur="$WORK_DIR/cur.xml" new="$WORK_DIR/new.xml"
  read_prefs > "$cur"
  grep -q '<map' "$cur" || printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n</map>\n" > "$cur"
  # Normalise an empty "<map />" into an open/close pair.
  awk '/<map *\/>/ { print "<map>"; print "</map>"; next } { print }' "$cur" > "$new" && mv "$new" "$cur"
  local kv key val
  for kv in "$@"; do
    key="${kv%%=*}"; val="${kv#*=}"
    awk -v k="$key" -v v="$val" '
      index($0, "<string name=\"" k "\">") { next }
      /<\/map>/ { print "    <string name=\"" k "\">" v "</string>" }
      { print }' "$cur" > "$new"
    mv "$new" "$cur"
  done
  write_prefs "$cur"
}

restore_everything() {
  set +e
  log "Restoring device state"
  dsh am force-stop "$PKG" >/dev/null
  if [[ "$HAD_PREFS" == 1 ]]; then
    write_prefs "$PREFS_BACKUP"
  else
    dsh run-as "$PKG" rm -f "$PREFS_FILE" >/dev/null
  fi
  dsh am broadcast -a com.android.systemui.demo -e command exit >/dev/null
  [[ -n "$ORIG_UIMODE" ]] && dsh cmd uimode night "$ORIG_UIMODE" >/dev/null 2>&1
  if [[ "$CLEANUP" == 1 ]]; then
    for a in "${ALBUMS[@]}"; do dsh rm -rf "$(q "$DEVICE_PICTURES/$a")" >/dev/null; done
    log "Removed demo media from the device (MediaStore may need a moment to notice)"
  fi
  dsh rm -rf "$DEVICE_TMP" >/dev/null
  rm -rf "$WORK_DIR"
}

# Make sure the prefs file exists (first launch creates it), then back it up.
dsh am force-stop "$PKG" >/dev/null
if [[ -z "$(read_prefs)" ]]; then
  log "First launch to initialise app preferences"
  dsh am start -W -n "$PKG/.MainActivity" >/dev/null
  sleep "$WAIT"
  dsh am force-stop "$PKG" >/dev/null
fi
if [[ -n "$(read_prefs)" ]]; then
  read_prefs > "$PREFS_BACKUP"
  HAD_PREFS=1
fi
trap restore_everything EXIT

# ------------------------------------------------------------- demo mode ----

log "Enabling clean status bar (demo mode)"
dsh settings put global sysui_demo_allowed 1
demo() { dsh am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 1200
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4
demo network -e mobile show -e datatype none -e level 4
demo notifications -e visible false

# --------------------------------------------------------------- capture ----

FOLDER_PATH="$DEVICE_PATH_PREFIX/$ALBUM"

launch_screen() { # $1 = screen name; returns 1 if skipped
  local comp
  case "$1" in
    home)
      dsh am start -W -S -n "$PKG/.MainActivity" >/dev/null ;;
    viewer)
      [[ -n "$PHOTO" ]] || return 1
      dsh am start -W -S -n "$PKG/.MediaViewerActivity" \
        --es extra_media_path "$(q "$FOLDER_PATH/$PHOTO")" \
        --es extra_folder_path "$(q "$FOLDER_PATH")" \
        --ei extra_current_position 0 >/dev/null ;;
    folder|settings|themes|trash)
      [[ "$HAS_ROOT" == 1 ]] || return 1
      case "$1" in
        folder)   comp=".FolderViewActivity" ;;
        settings) comp=".SettingsActivity" ;;
        themes)   comp=".ThemePackActivity" ;;
        trash)    comp=".TrashBinActivity" ;;
      esac
      if [[ "$1" == folder ]]; then
        dsh am start -W -S -n "$PKG/$comp" \
          --es extra_folder_path "$(q "$FOLDER_PATH")" \
          --es extra_folder_name "$(q "$ALBUM")" >/dev/null
      else
        dsh am start -W -S -n "$PKG/$comp" >/dev/null
      fi ;;
    *) warn "Unknown screen '$1'"; return 1 ;;
  esac
}

index=0
declare -a ORDERED=()
for s in $SCREENS; do index=$((index + 1)); ORDERED+=("$(printf '%02d' "$index")_$s"); done

count=0
for locale in $LOCALES; do
  case "$locale" in en|zh_TW|zh_CN) ;; *) die "Unsupported locale '$locale' (use en, zh_TW, zh_CN)" ;; esac
  for theme in $THEMES; do
    case "$theme" in light|dark) ;; *) die "Unsupported theme '$theme' (use light or dark)" ;; esac
    log "Capturing: language=$locale theme=$theme"

    dsh am force-stop "$PKG" >/dev/null
    prefs=("app_language=$locale" "theme_preference=$theme")
    [[ -n "$PACK" ]] && prefs+=("theme_pack_preference=$PACK")
    set_prefs "${prefs[@]}"
    # Match the system night mode too, so system bars agree with the app.
    [[ "$theme" == dark ]] && dsh cmd uimode night yes >/dev/null 2>&1 || true
    [[ "$theme" == light ]] && dsh cmd uimode night no >/dev/null 2>&1 || true

    dest="$OUT_DIR/$locale/$theme"
    mkdir -p "$dest"
    for entry in "${ORDERED[@]}"; do
      screen="${entry#*_}"
      if ! launch_screen "$screen"; then
        warn "  skipped $screen"
        continue
      fi
      sleep "$WAIT"
      adb exec-out screencap -p > "$dest/$entry.png"
      echo "  saved $dest/$entry.png"
      count=$((count + 1))
    done
  done
done

log "Done: $count screenshot(s) in $OUT_DIR"
