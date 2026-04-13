---
created: 2026-04-13T11:50:21.608Z
title: Settings — visuelle Trennung zwischen Unterpunkten eines Abschnitts
area: ui
files:
  - app/src/main/java/org/havenapp/main/ui/settings/SettingsScreen.kt
---

## Problem

Innerhalb eines Einstellungs-Abschnitts (z.B. „Erkennung") sind die einzelnen Einstellungszeilen (Empfindlichkeit, Kamera, Erkennungsmodus) optisch nicht voneinander getrennt. Bei mehreren Elementen pro Abschnitt fehlt die visuelle Struktur, was die Lesbarkeit erschwert.

## Solution

Zwischen den einzelnen Unterpunkten eines CategoryCard-Abschnitts eine `HorizontalDivider` einfügen (Material 3: `HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))`). Alternativ: subtile Hintergrundfarbe im Wechsel oder erhöhter vertikaler Abstand. Primär-Vorschlag: `HorizontalDivider` nach jedem Eintrag außer dem letzten — sauber und konventionskonform mit Material 3.

Betrifft alle CategoryCards in `SettingsScreen.kt` mit mehr als einem Unterpunkt.
