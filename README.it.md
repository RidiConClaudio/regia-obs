# RegiaOBS

[English](README.md) · **Italiano**

Controller Android nativo e Macro Deck per **OBS Studio** (`obs-websocket` v5, compatibile con OBS 28–33+), scritto in Kotlin e Jetpack Compose.

Controlla l'intera regia di OBS da telefono o tablet Android via Wi-Fi locale o VPN, senza software extra da installare sul PC.

---

## Funzionalità principali

- **Regia Broadcast (`Console`)**: monitor live Program + Anteprima in Studio Mode (1–8 FPS), selettore transizione e durata, **T-Bar** interattiva, controllo **Diretta (LIVE)**, **Registrazione** (con pausa e capitoli), **Replay Buffer** (`Save Clip` istantaneo), **Virtual Camera** e sorgenti rapide.
- **Macro Deck (`Deck`)**: griglia di tasti personalizzabili su più pagine con spie di stato live, 19 tipi di azioni concatenabili con ritardi, sincronizzazione automatica con i nomi reali del tuo OBS (anche in italiano), generazione automatica dalle scene ed export/import JSON.
- **Mixer Audio Pro (`Mixer`)**: modalità Console verticale o Lista compatta, VU meter su `Canvas` a 20 Hz, curva cubica OBS, **cambio Microfono / Altoparlante / Finestra** direttamente dal telefono, monitoraggio, Sync Offset (`ms`), bilanciamento L/R e filtri audio.
- **Sorgenti & Trasformazioni (`Sources`)**: visibilità, blocco, ordine livelli, gruppi espandibili, Media Player integrato con barra di avanzamento, modifica testi e URL Browser al volo, ricarica cache e controlli Transform (spostamento, scala, rotazione, adatta allo schermo).
- **Connessione & Telemetria (`Connect` & `System`)**: scansione QR offline di OBS, scansione Wi-Fi locale, profili multi-PC, telemetria CPU/FPS/frame persi/spazio disco, cambio Profilo/Collezione Scene, Hotkey ricercabili e **Modalità Demo** integrata.

---

## Configurazione su OBS Studio

1. Apri **OBS Studio** (v28+) sul PC.
2. Vai su **Strumenti → Impostazioni Server WebSocket**.
3. Spunta **Abilita server WebSocket** (porta predefinita `4455`).
4. Clicca **Mostra informazioni connessione** per aprire il QR Code.
5. Apri **RegiaOBS** sul telefono e tocca **Scan OBS QR** oppure **Scan Wi-Fi**.

---

## Compilazione da sorgente

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Oppure scarica l'APK già compilato dalla sezione [Releases](https://github.com/RidiConClaudio/regia-obs/releases).

## Licenza

[MIT](LICENSE)
