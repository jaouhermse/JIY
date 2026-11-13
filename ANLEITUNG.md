# JIY Family – Einrichtung (einmalig, ca. 20 Minuten)

Am Ende hast du:
- eine **APK** zum Installieren auf euren Android-Handys,
- deine **eigenen Daten** in Google Firebase (kostenlos),
- **Updates selbst**: `web/index.html` auf GitHub ändern → nach ca. 1 Minute ist es in der App.

---

## Schritt 1 – Firebase einrichten

1. Öffne <https://console.firebase.google.com> und melde dich mit deinem Google-Konto an.
2. **Projekt hinzufügen** → Name z. B. `jiy-family` → Google Analytics kannst du ausschalten → **Projekt erstellen**.
3. **Anmeldung einschalten:** links *Build → Authentication* → **Jetzt starten** → *E-Mail/Passwort* → **Aktivieren** → Speichern.
4. **Datenbank anlegen:** links *Build → Firestore Database* → **Datenbank erstellen** → Standort **europe-west3 (Frankfurt)** → *Im Produktionsmodus starten*.
5. **Regeln eintragen:** in Firestore oben auf **Regeln** → alles löschen → Inhalt aus `firestore.rules` einfügen.
   Ersetze dort `email_deiner_frau@outlook.de` durch die echte E-Mail deiner Frau (**klein geschrieben**) → **Veröffentlichen**.
6. **Web-App registrieren:** Zahnrad oben links → *Projekteinstellungen* → unten **</> (Web)** → Name `JIY` → **App registrieren**.
   Du siehst jetzt einen Block `const firebaseConfig = { apiKey: ..., ... }` – den brauchst du gleich.

## Schritt 2 – Werte in `web/index.html` eintragen

Öffne `web/index.html` und ganz oben im Abschnitt **EINSTELLUNGEN**:

- `FIREBASE_CONFIG` → die Werte aus Schritt 1.6 einsetzen (apiKey, authDomain, projectId, storageBucket, messagingSenderId, appId).
- `ALLOWED_HASHES` / `OWNER_HASH` → SHA-256-Fingerabdrücke der E-Mails (klein geschrieben). So stehen eure Adressen nicht öffentlich im Code.

> Der `apiKey` von Firebase ist nicht geheim – geschützt werden eure Daten durch die Regeln aus Schritt 1.5.

## Schritt 3 – Auf GitHub hochladen

1. Auf <https://github.com> → **New repository** → Name **`jiy-family`** → **Public** → *Create repository*.
   (Öffentlich ist nötig, damit GitHub Pages kostenlos ist. Eure Familiendaten liegen trotzdem geschützt in Firebase, nicht auf GitHub.)
2. Auf der Repo-Seite **uploading an existing file** → den **Inhalt** des Ordners `jiy-family` hineinziehen
   (die Ordner `web`, `android`, `.github` und die Dateien) → **Commit changes**.
   Falls der Ordner `.github` beim Hochziehen fehlt: *Add file → Create new file* → Name `.github/workflows/build.yml` → Inhalt aus der Datei einfügen.
3. **Settings → Pages** → bei *Source* **GitHub Actions** auswählen.
4. Oben auf **Actions** → *JIY bauen* → **Run workflow** (oder einfach warten, es startet beim Hochladen von selbst).
   Nach ca. 5 Minuten sind beide Häkchen grün ✅.

## Schritt 4 – App aufs Handy

1. Auf dem Handy dein Repo öffnen → rechts **Releases** → **JIY Family App** → `JIY-Family.apk` herunterladen.
2. Antippen → Android fragt nach „Unbekannte Apps installieren“ → für Chrome **erlauben** → **Installieren**.
3. App öffnen → **Neues Konto erstellen** → E-Mail + Passwort → Bestätigungs-Mail öffnen und Link antippen → **Ich habe bestätigt**.
4. Du als Besitzer: Tab **Familie** → dich, deine Frau und Yanis anlegen (Name, Kürzel, Geburtsdatum, Rolle) → bei dir **Das bin ich**.
5. Deine Frau: APK genauso installieren, Konto mit **ihrer** E-Mail erstellen, oben rechts ihr Profil wählen.

---

## Updates machen

- **Aussehen/Funktionen ändern:** auf GitHub `web/index.html` öffnen → ✏️ bearbeiten → *Commit changes*.
  Nach ca. 1 Minute ist die Änderung in der App (App einmal schließen und neu öffnen).
- **Neue E-Mail freischalten:** Fingerabdruck (SHA-256 der klein geschriebenen E-Mail) in `web/index.html` bei `ALLOWED_HASHES` ergänzen **und** die E-Mail in Firebase bei den Firestore-Regeln eintragen.
- **Neue APK** brauchst du nur, wenn sich etwas im Ordner `android` ändert. Sie landet automatisch unter *Releases*
  und lässt sich über die alte App drüber installieren.

## Videos im Chat einschalten (optional)

Ohne Kreditkarte gehen Fotos (werden verkleinert). Für **Videos**:
1. Firebase → unten links **Upgrade** → Tarif **Blaze** (Kreditkarte; für eine Familie praktisch 0 €, du kannst ein Budget-Limit setzen).
2. *Build → Storage* → **Jetzt starten** → Standort Europa.
3. Unter Storage → **Regeln** → Inhalt aus `storage.rules` einfügen (E-Mails anpassen) → Veröffentlichen.
4. In `web/index.html` `USE_STORAGE = true` setzen → speichern.

## Hinweise

- **Anrufe** öffnen Jitsi Meet außerhalb der App (Browser oder Jitsi-App).
- **Google/Outlook** bei Terminen öffnet den jeweiligen Kalender zum Übernehmen.
- Die „schlauen Vorschläge“ von claude.ai gibt es in dieser Version nicht – Katalog und „Oft gekauft“ sind da.
- Die Datei `android/app/jiy.keystore` ist der Signier-Schlüssel der App. Nicht löschen – sonst lässt sich eine neue APK nicht mehr über die alte installieren.

## Kleine Entdecker (Yanis' Lern- und Spielbereich)

Die frühere Yanis-App „Kleine Entdecker“ ist jetzt **in JIY eingebaut** – keine zweite App mehr nötig.

- Sichtbar **nur für Profile mit der Rolle „🧒 Kind“** (unten Tab **🎒 Entdecker**, außerdem unter *Mehr*).
- Inhalt: Lernwerkstatt (Deutsch A–Z, Arabisch ا–ي, Vorlesen, Schreiben üben), Puzzle, Memory (1–2 Spieler), Labyrinth, 3D-Villa, Auto fahren, Wudu und Salat Schritt für Schritt. Gebetszeiten und Quran laufen über den Tab *Gebet*.
- Sterne aus den Spielen landen automatisch bei **Punkte & Belohnungen** des Kindes (höchstens 3× pro Spiel und Tag).
- Für die 3D-Villa muss die Datei `web/villa.gltf` mit hochgeladen sein.

## Rollen

- **👑 Papa · Administrator** – alles, inkl. Familie, Profile und Kinderschutz.
- **💜 Mama** – alle Eltern-Funktionen (Kalender, Haushalt, Fristen, Punkte, Profile mit PIN wechseln), nur Familie/Profile verwalten bleibt beim Administrator.
- **🧒 Kind** – mit Kinderschutz und Kleine Entdecker. **👋 Gast** – Kalender, Chat, Einkauf, Essen, Gebet, WLAN.
