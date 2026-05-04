# Brawler Arena — Current Implementation

This repository contains a small Java + Swing 2D side-view brawler (MVC architecture). The game runs in a resizable window but uses a logical reference resolution of 1280×720 for internal coordinates. Rendering is scaled at runtime so the game adapts to different window sizes.

This README follows the professor's request: it includes the spec, a description of what was built, which classes handle which responsibilities, and a short reflection on what was learned.

---

## Quick Features (current code)

- 60 FPS main loop driven by a Swing `Timer` (`GameController.GameLoop`).
- Resolution-independent rendering: `GameView.GamePanel` computes `widthScale` and `heightScale` and applies them to drawing coordinates and UI placement.
- Implemented enemies: `Goblin`, `Wolf`, `Rogue` with per-type HP/damage and simple melee AI (`GameModel` + `Spawner`).
- Power-ups (various immediate effects) and currency (`Money`) dropped by enemies; upgrades purchasable with in-game money.
- HUD with level, timer, lives, health bar, kills, active effects panel, and an ability/upgrade panel.
- Floating damage text, per-enemy health bars, and clickable Title-screen How-to-play button.
- Audio: `SoundManager` preloads per-event WAVs and prints diagnostics; MP3 fallback implemented via PowerShell when a WAV is missing.

---

## Controls (exact mapping implemented in `GameController.KeyboardController`)

- Move left/right: `A` / `D`
- Run: hold `Shift`
- Jump: `Space` or `W`
- Crouch / Down: `S`
- Punch: `J`
- Kick: `K`
- Pause / Resume: `P` or `Esc`
- Restart: `R`
- Buy upgrades: `1` (Speed), `2` (Health), `3` (Damage), `4` (Jump)

Title screen: press `Space` to start. The title screen also has a clickable "How to play" button handled in `GameController`.

---

## What I built (concise)

- Full MVC wiring: `GameController` (input, loop, controllers), `GameModel` (game state and rules), `GameView` (rendering), `SoundManager` (audio).
- Screen-independent rendering and per-entity draw offsets so characters stand correctly on ground at any window size.
- Gameplay systems: enemies, spawner, level timer (60s), health/lives/respawn, power-ups, money, upgrades, floating text, HUD, and simple AI.
- Audio improvements: per-event WAV loading with diagnostic logging and an MP3 fallback loop using PowerShell for environments without a WAV file.

---

## Which class is responsible for what

- `GameController.java` — application wiring and runtime responsibilities:
  - `GameController` constructor: sets up MVC, listeners, and starts music and the game loop.
  - `KeyboardController`: collects held and pressed keys and exposes intent queries.
  - `GameLoop`: main tick at ~60 FPS: reads input, updates model, runs collision checks, triggers rendering.
  - `CollisionController`: bounding-box overlap tests and applies damage between players and enemies.
  - `StateController`: simple state transitions (start, restart, level advance), restart cooldown logic.

- `GameModel.java` — authoritative game state and rules:
  - Entities: `Player`, `Enemy` (and concrete `Goblin`, `Wolf`, `Rogue`), `PowerUp`, `Money`, `FloatingText`.
  - Spawner and per-level logic, level timer, applying damage, money and upgrades, high-score persistence (`highscore.txt`).
  - Encapsulates update(dt) where physics, AI updates, power-up spawning and collection, and level transitions occur.

- `GameView.java` — rendering and UI composition:
  - `GameWindow`/`GamePanel`: main Swing UI and overridden `paintComponent`.
  - `BackgroundView`, `HUDView`, `SpriteSheet`, and `Animation` helpers.
  - Animation clips are created in `GamePanel.loadAnimations()` and drawing uses `widthScale`/`heightScale` to adapt positions.

- `SoundManager.java` — audio subsystem:
  - Preloads per-event WAV clips, prints load diagnostics, provides per-event play helpers with throttling.
  - MP3 fallback uses a PowerShell MediaPlayer loop when `battleThemeA.wav` is missing.

---

## Technical notes & important implementation details

- Reference coordinates are preserved (`GameModel.WORLD_WIDTH = 1280`, `WORLD_HEIGHT = 720`) so game logic runs with consistent physics; `GameView` is responsible for mapping model coordinates to screen pixels.
- Enemy/player draw Y offsets are type-specific in `GameView.getEnemyDrawYOffset(...)` and then multiplied by `heightScale` so sprites align to the ground.
- `SoundManager` prints informative messages on startup so you can see which sound files are present; missing audio files do not crash the game.

---

## Build & Run

From the project root (where the .java files live):

```powershell
javac -d bin -encoding UTF-8 *.java
java -cp bin GameController
```

If you use an IDE, ensure the `assets/` directory is copied to the working directory so the image and audio loaders find their files.

---

## What I learned (short reflection)

- How to decouple game logic (model) from rendering (view) so the same world state can be displayed at multiple resolutions.
- Practical Swing rendering techniques: `paintComponent`, off-by-one sprite offsets, and runtime scaling (width/height scales) to support resizable windows.
- Audio pitfalls: WAV format compatibility and graceful fallbacks; using a PowerShell fallback is a pragmatic cross-platform workaround on Windows.
- Small UX details matter: per-enemy health bars, floating text, and responsive HUD greatly improve feedback.
- Tooling: fixed a local Git identity issue and learned to diagnose VS Code git askpass permission errors.
- Using gameArt : How to use real art and .png in the games and add assets correctly, render the frames and animations. 

---

