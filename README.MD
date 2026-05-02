# Brawler Arena — Game Spec

**Concept.** Single-screen 2D side-view brawler. Java + Swing + MVC. 1280×720 window. Hero defends a fixed arena against waves of enemies. Survive 60 seconds per level across 4 levels = victory.

**Controls.** `A / D` walk · `Shift + A/D` run · `Space` jump · `J` punch (10 dmg) · `K` kick (15 dmg) · `Esc / P` pause.

---

## MODEL — state and rules (no rendering, no input)

**World** — 1280×720 bounds, fixed ground baseline Y, hero walled at left/right screen edges.

**Player** — position, velocity, facing direction, HP (100/100), lives (3), state ∈ {IDLE, WALK, RUN, JUMP, PUNCH, KICK, HURT, DEAD, RESPAWNING}, attack hitbox active only during specific animation frames, 3-second respawn delay after death.

**Enemy (abstract base)** — position, velocity, facing, HP, maxHP, damage, speed, attack range, attack cooldown, state ∈ {WALK, ATTACK, HURT, DEAD}.
- **Goblin** — 30 HP · slow · 5 damage per hit
- **Wolf** — 40 HP · fast · 10 damage
- **Rogue** — 60 HP · medium · 15 damage
- AI: walk toward player; when in melee range, attack on cooldown. All on-screen enemies rush (no queueing).

**Spawner** — per-level config: allowed enemy types, spawn interval ~2–4s, cap of 3–4 on screen. Spawns from right edge. *Stretch: Level 4 also spawns from left edge.*

**Level** — index 1–4, 60s countdown, enemy pool:
- L1 Goblin · L2 Goblin + Wolf · L3 Goblin + Wolf + Rogue · L4 All three (+ rear spawning if time permits)

**GameState** enum — TITLE, PLAYING, PAUSED, LEVEL_CLEARED, GAME_OVER, VICTORY.

---

## VIEW — rendering only

- **GameWindow** — `JFrame` 1280×720 hosting **GamePanel** (`JPanel` with overridden `paintComponent`).
- **BackgroundView** — draws `country-platform-preview.png` scaled (~×3.3) to fill canvas.
- **SpriteSheet** helper — loads a `BufferedImage`, exposes `getFrame(col, row, w, h)`.
- **Animation** — ordered list of frames + ms-per-frame + loop flag; advances per tick.
- **CharacterView** (one per entity type) — maps each state to an Animation, draws current frame at the entity's model position. Per-sheet frame sizes will differ; each view encodes its own sheet layout.
  - `PlayerView` — Ars Notoria hero sheet
  - `GoblinView` — LPC sheet (use only side-walk + side-slash rows)
  - `WolfView` — wolfsheet1
  - `RogueView` — calciumtrice sheet
  - Sprites may be uniformly scaled per type so the cast looks size-consistent on screen.
- **HUDView** — lives icons (top-left), player health bar, level number, countdown timer (top-center), small floating health bar above each enemy.
- **Screen overlays** — TitleScreen, PauseOverlay, LevelClearedSplash, GameOverScreen, VictoryScreen.

---

## CONTROLLER — input + game loop + state transitions

- **KeyboardController** (`KeyListener`) — tracks held keys; exposes intents `isLeftHeld()`, `isRightHeld()`, `isRunHeld()`, `wasJumpPressed()`, `wasPunchPressed()`, `wasKickPressed()`, `wasPausePressed()`. Edge-triggered for actions, level-triggered for movement.
- **GameLoop** — `javax.swing.Timer` at ~60 FPS. Each tick, in order:
  1. Read input intents.
  2. Update player (movement, jump physics, attack-state transitions, hitbox windows).
  3. Update each enemy (AI: walk toward player, attack if in range and off cooldown).
  4. **CollisionController** — bounding-box overlap; apply damage during active attack frames; trigger HURT / DEAD.
  5. Update spawner (spawn if under cap and interval elapsed).
  6. Tick level timer; cull dead entities.
  7. Check transitions (timer = 0 → LEVEL_CLEARED; lives = 0 → GAME_OVER; L4 cleared → VICTORY).
  8. `repaint()`.
- **StateController** — routes input to the active screen's handler; manages screen transitions.

---

## Build order — 3 days, tester after every step

**Day 1 — View first (visual checkpoint after every step)**
1. JFrame + GamePanel + colored fill. Run it.
2. Load and draw the scaled background.
3. SpriteSheet + Animation classes; show one looping hero idle on screen.
4. Add looping goblin / wolf / rogue idle animations side by side — visual sanity check.
5. Sketch the HUD with placeholder values.

**Day 2 — Model + Controller**
1. Player position model + WASD movement + screen-edge walls.
2. Jump physics (gravity, ground baseline).
3. Punch / kick states, animation-driven hitbox windows.
4. Enemy model + simple AI; spawn one manually for testing.
5. Bounding-box collision + damage + HURT animation + death.
6. Lives, 3-second respawn.

**Day 3 — Levels, screens, polish**
1. Spawner with per-level enemy pool and pacing.
2. 60-second level timer + Level Cleared splash + advance to next level.
3. Title, Pause (`Esc`), Game Over (`R` restarts), Victory screens.
4. Tune damage / spawn rates so it's actually fun.
5. **Stretch:** Level 4 rear spawning.

---

## Asset notes

- All five PNGs are usable. The LPC goblin was made for top-down RPGs, so use only its side-walk and side-slash rows — it'll read slightly tilted next to the side-view cast but is fine for class scope.
- Animations are done by drawing one sub-rectangle of a sheet per frame; a `Timer` advances the frame index. No single-image assets needed.
- Each sheet has different frame dimensions, so the per-type View classes will each declare their own frame width/height and row layout.