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

JIY ist eine **echte Android-App**: die komplette Oberfläche (Ordner `web/`) steckt **in der APK**.
Die App lädt nichts mehr von einer Webseite nach und funktioniert auch offline
(nur Chat/Kalender-Abgleich, Quran-Audio, Gebetszeiten-Ort usw. brauchen Internet).

- **Etwas ändern:** Datei im Ordner `web/` oder `android/` auf GitHub ändern → *Commit changes*.
  GitHub baut automatisch eine neue APK (ca. 5 Minuten) und legt sie unter *Releases* ab.
- **Auf den Handys:** JIY zeigt unter *Mehr* „Update verfügbar“ → antippen → neue Version wird installiert.
  Anmeldung und alle Daten bleiben erhalten.
- **Neue E-Mail freischalten:** Fingerabdruck (SHA-256 der klein geschriebenen E-Mail) in `web/index.html` bei `ALLOWED_HASHES` ergänzen **und** die E-Mail in Firebase bei den Firestore-Regeln eintragen.
- GitHub Pages wird weiterhin veröffentlicht – nur noch für das Kalender-Abo und als Reserve.

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
- Inhalt: Lernwerkstatt (Deutsch A–Z, Arabisch ا–ي, Vorlesen, Schreiben üben), Puzzle, Memory (1–2 Spieler), Labyrinth, 3D-Villa, Auto fahren, Wudu und Salat Schritt für Schritt.
- **📖 Quran lernen** (unter *Mein Leben*): 12 kurze Suren (Al-Fatiha, An-Nas, Al-Falaq, Al-Ikhlas …) Vers für Vers –
  Rezitation (Alafasy), 🔁 3× wiederholen, 🇩🇪 Bedeutung vorlesen, ✓ „Kann ich“. **Lernmodus** spielt jeden Vers 3× und liest die Bedeutung vor.
  Für jede gelernte Sure gibt es 3 Sterne.
- **🔊 Vorlesen:** oben rechts im Kinderbereich – *DE+ع* (beides), *DE*, *ع* oder aus. Alles, was Yanis antippt, wird vorgelesen; beim Öffnen einer Seite wird die Überschrift vorgelesen.
  Für Arabisch muss auf dem Handy die arabische Stimme installiert sein: *Einstellungen → Sprache → Sprachausgabe → Google → Sprachdaten installieren → Arabisch*
  (fehlt sie, bietet JIY „Installieren“ an).
- Sterne aus den Spielen landen automatisch bei **Punkte & Belohnungen** des Kindes (höchstens 3× pro Spiel und Tag).
- Für die 3D-Villa muss die Datei `web/villa.gltf` mit hochgeladen sein.

## Rollen

- **👑 Papa · Administrator** – alles, inkl. Familie, Profile und Kinderschutz.
- **💜 Mama** – alle Eltern-Funktionen (Kalender, Haushalt, Fristen, Punkte, Profile mit PIN wechseln), nur Familie/Profile verwalten bleibt beim Administrator.
- **🧒 Kind** – mit Kinderschutz und Kleine Entdecker. **👋 Gast** – Kalender, Chat, Einkauf, Essen, Gebet, WLAN.

## 💶 Mein Geld (Haushaltsbuch)

Unter **Mehr → 💶 Mein Geld** – nur für **Mama und Papa** (Kinder und Gäste sehen es nicht).

- **Jeder hat einen privaten Bereich:** Papa legt seine Konten an, Mama ihre. Papas private Konten sieht
  Mama nicht – und Mamas private Konten sieht Papa nicht.
- **Gemeinschaftskonto:** Konto antippen → Häkchen „👨‍👩‍👦 Gemeinsames Konto“ → beide sehen es,
  inkl. Buchungen, Daueraufträge (👤 zeigt, wer gebucht hat). Sparziele lassen sich genauso teilen.
- Umbuchung vom privaten aufs Gemeinschaftskonto: der andere sieht nur „🔒 Privat → Gemeinschaftskonto“.
- Privat geht nur mit dem **eigenen Profil auf dem eigenen Konto**. Ist auf einem Handy das Profil des
  anderen aktiv, zeigt „Mein Geld“ nur die Gemeinschaftskonten.
- Daten in Firebase: `financeUser/{uid}/docs/` = privat (pro Person), `financeShared/` = gemeinsam.

**Firebase-Regeln:** sind eingetragen (`financeUser` privat pro Person, `financeShared` nur Eltern, beide aus der
allgemeinen Familien-Regel ausgenommen). Die Vorlage steht in `firestore.rules` – die echten E-Mails nur in der Konsole.

- Export landet in *Downloads/JIY*. Anmeldung über das JIY-Konto – kein extra Passwort.
- **KI** (Kontoauszug-Diagnose, Beleg scannen, Spartipps, Assistent): in *Mein Geld → Mehr → 🔑 KI-Schlüssel*
  einen eigenen Anthropic-API-Schlüssel eintragen (console.anthropic.com). Ohne Schlüssel geht alles außer der KI.
- Die App selbst ist `web/geld.html` – Änderungen dort sind wie gewohnt nach ca. 1 Minute in JIY.

## 🏠 Heimnetz (IT-Ecke, nur Administrator, nur in der App)

*Mehr → IT-Ecke → 🏠 Heimnetz.* Funktioniert nur, wenn das Handy im **Heim-WLAN** ist.

- **🔍 Geräte:** sucht alle Geräte im WLAN (Name, IP, Art: FRITZ!Box, Fernseher, Drucker, NAS, PC …).
  Mit ＋ ins IT-Inventar übernehmen. Mit eingerichteter FRITZ!Box kommen alle bekannten Geräte mit Namen dazu.
- **📶 FRITZ!Box:** Modell, FRITZ!OS, Internet-Status und Geschwindigkeit, **Gäste-WLAN ein/aus**,
  **Internet für ein Gerät sperren/freigeben** (⛔ in der Geräteliste, z. B. Tablet abends), **Neustart**.
  Einmalig in der FRITZ!Box: *System → FRITZ!Box-Benutzer* → eigenen Benutzer (z. B. „jiy“) mit Recht
  *FRITZ!Box Einstellungen* anlegen und unter *Heimnetz → Netzwerk → Netzwerkeinstellungen*
  „Zugriff für Anwendungen zulassen“ einschalten. Das Passwort liegt **verschlüsselt nur auf dem Handy**.
- **📺 Fernseher (Samsung, ab ca. 2018):** Fernbedienung mit Ein/Aus, Lautstärke, Programm, Pfeiltasten, Quelle,
  Netflix/YouTube/Prime/Disney+. Beim ersten Mal am Fernseher „Zulassen“ bestätigen.
  Fürs **Einschalten** am Fernseher *Allgemein → Netzwerk → Experteneinstellungen → „Mit Mobilgerät einschalten“* aktivieren.
