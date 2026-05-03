/**
 * GameModel.java
 * Contains all game state and rules. No Swing imports.
 */

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameModel {
    private static final long LEVEL_DURATION_MS = 60000;
    
    // Game state enum
    public enum GameState {
        TITLE, PLAYING, PAUSED, LEVEL_CLEARED, GAME_OVER, VICTORY
    }
    
    // Player state enum
    public enum PlayerState {
        IDLE, CROUCH, WALK, RUN, JUMP, FALL, PUNCH, KICK, HURT, DEAD, RESPAWNING
    }
    
    // Enemy state enum
    public enum EnemyState {
        WALK, ATTACK, HURT, DEAD
    }

    // Player-only power-up types
    public enum PowerUpType {
        DOUBLE_DAMAGE, SPEED_BOOST, HIGH_JUMP, DAMAGE_REDUCTION, SLOW, NO_JUMP, LOSE_HP_50, MYSTERY_BOX
    }

    // Permanent upgrade types purchasable with money
    public enum AbilityType {
        SPEED, HEALTH, DAMAGE, JUMP
    }
    
    // World bounds
    public static final int WORLD_WIDTH = 1280;
    public static final int WORLD_HEIGHT = 720;
    public static final int GROUND_Y = 550; // Fixed ground baseline
    public static final int CHARACTER_GROUND_Y = 580; // sprite feet baseline used by physics and drawing
    
    private GameState gameState;
    private int currentLevel;
    private long levelTimeRemaining; // milliseconds
    private Player player;
    private List<Enemy> enemies;
    private List<PowerUp> powerUps;
    private List<FloatingText> floatingTexts;
    private Spawner spawner;
    private long powerUpSpawnTimer;
    private long nextPowerUpSpawnDelayMs;
    private final Random powerUpRandom = new Random();
    // Money and upgrades
    private List<Money> moneyList;
    private int playerMoney;
    
    /**
     * Initialize game model with defaults.
     */
    public GameModel() {
        this.gameState = GameState.TITLE;
        this.currentLevel = 1;
        this.levelTimeRemaining = LEVEL_DURATION_MS;
        this.player = new Player();
        this.enemies = new ArrayList<>();
        this.powerUps = new ArrayList<>();
        this.floatingTexts = new ArrayList<>();
        this.moneyList = new ArrayList<>();
        this.playerMoney = 0;
        this.spawner = new Spawner(currentLevel);
        this.powerUpSpawnTimer = 0;
        this.nextPowerUpSpawnDelayMs = 8000;
    }
    
    /**
     * Update game state for one tick (~16ms at 60 FPS).
     */
    public void update(double deltaTime) {
        if (gameState != GameState.PLAYING) {
            return;
        }
        
        // Update player
        if (player != null) {
            player.update(deltaTime);
        }
        
        // Update enemies
        for (Enemy enemy : enemies) {
            enemy.update(deltaTime, player);
        }
        
        // Update spawner
        spawner.update(deltaTime, enemies);

        // Update power-ups and floating combat text
        updatePowerUps(deltaTime);
        updateFloatingTexts(deltaTime);
        updateMoney(deltaTime);
        
        // Update level timer
        levelTimeRemaining -= (long)(deltaTime * 1000);
        if (levelTimeRemaining < 0) {
            levelTimeRemaining = 0;
        }
        
        // Remove dead entities
        enemies.removeIf(e -> e.isDead());
        
        // Check level transitions
        checkLevelTransitions();
    }
    
    /**
     * Check win/lose/level-clear conditions.
     */
    private void checkLevelTransitions() {
        if (player.getLives() <= 0) {
            gameState = GameState.GAME_OVER;
            return;
        }

        if (levelTimeRemaining <= 0) {
            if (currentLevel >= 4) {
                gameState = GameState.VICTORY;
            } else {
                gameState = GameState.LEVEL_CLEARED;
            }
        }
    }
    
    /**
     * Apply damage from an attack to a target.
     */
    public void applyDamage(GameEntity target, int damage) {
        if (target instanceof Player) {
            int actualDamage = ((Player) target).takeDamage(damage);
            if (actualDamage > 0) {
                addFloatingText(target.getX() + target.getWidth() * 0.5, target.getY() - 10, "-" + actualDamage, Color.RED);
            }
        } else if (target instanceof Enemy) {
            int actualDamage = ((Enemy) target).takeDamage(damage);
            if (actualDamage > 0) {
                addFloatingText(target.getX() + target.getWidth() * 0.5, target.getY() - 10, "-" + actualDamage, Color.RED);
                Enemy enemy = (Enemy) target;
                if (enemy.isDead()) {
                    // spawn money upon death
                    int value = 1 + powerUpRandom.nextInt(2); // 1-2 dollars
                    moneyList.add(new Money(enemy.getX(), enemy.getY(), value));
                    if (enemy instanceof Goblin) {
                        player.heal(10);
                    } else if (enemy instanceof Wolf) {
                        player.heal(15);
                    } else if (enemy instanceof Rogue) {
                        player.heal(20);
                    }
                }
            }
        }
    }
    
    /**
     * Start a new level.
     */
    public void startLevel(int levelNumber) {
        this.currentLevel = levelNumber;
        this.levelTimeRemaining = LEVEL_DURATION_MS;
        this.enemies.clear();
        this.powerUps.clear();
        this.floatingTexts.clear();
        this.spawner = new Spawner(levelNumber);
        this.powerUpSpawnTimer = 0;
        this.nextPowerUpSpawnDelayMs = 8000 + powerUpRandom.nextInt(5000);
        this.gameState = GameState.PLAYING;

        // Give each level an immediate visual enemy check-in.
        int starterEnemies = Math.min(levelNumber, 3);
        for (int i = 0; i < starterEnemies; i++) {
            Enemy spawned = spawner.spawnRandomEnemy();
            if (spawned != null) {
                spawned.setX(WORLD_WIDTH - 120 - (i * 80));
                enemies.add(spawned);
            }
        }
    }

    public void advanceToNextLevel() {
        if (currentLevel >= 4) {
            gameState = GameState.VICTORY;
            return;
        }
        startLevel(currentLevel + 1);
    }
    
    /**
     * Pause the game.
     */
    public void pause() {
        if (gameState == GameState.PLAYING) {
            gameState = GameState.PAUSED;
        }
    }
    
    /**
     * Resume from pause.
     */
    public void resume() {
        if (gameState == GameState.PAUSED) {
            gameState = GameState.PLAYING;
        }
    }
    
    // ===== Getters =====
    public GameState getGameState() { return gameState; }
    public void setGameState(GameState state) { this.gameState = state; }
    public int getCurrentLevel() { return currentLevel; }
    public long getLevelTimeRemaining() { return levelTimeRemaining; }
    public Player getPlayer() { return player; }
    public List<Enemy> getEnemies() { return enemies; }
    public List<PowerUp> getPowerUps() { return powerUps; }
    public List<FloatingText> getFloatingTexts() { return floatingTexts; }
    public List<Money> getMoneyList() { return moneyList; }
    public int getPlayerMoney() { return playerMoney; }
    public Spawner getSpawner() { return spawner; }
    public boolean isCrouching() { return player != null && player.isCrouching(); }
    public void resetPlayer() { this.player = new Player(); }
    public List<String> getActiveEffectSummaries() { return player == null ? new ArrayList<>() : player.getActiveEffectSummaries(); }

    public void addFloatingText(double x, double y, String text, Color color) { floatingTexts.add(new FloatingText(x, y, text, color)); }

    public String applyPowerUp(PowerUpType type) {
        if (player == null) {
            return "";
        }
        PowerUpType resolvedType = type;
        if (type == PowerUpType.MYSTERY_BOX) {
            PowerUpType[] mysteryChoices = {
                PowerUpType.DOUBLE_DAMAGE,
                PowerUpType.SPEED_BOOST,
                PowerUpType.HIGH_JUMP,
                PowerUpType.DAMAGE_REDUCTION,
                PowerUpType.SLOW,
                PowerUpType.NO_JUMP,
                PowerUpType.LOSE_HP_50
            };
            resolvedType = mysteryChoices[powerUpRandom.nextInt(mysteryChoices.length)];
        }
        return player.applyPowerUp(resolvedType);
    }

    // Purchase a permanent upgrade using player money. Returns a short message.
    public String purchaseUpgrade(AbilityType ability) {
        if (player == null) return "No player";
        int level = player.getPermLevel(ability);
        int cost = level + 1;
        if (playerMoney < cost) {
            return "Not enough $";
        }
        playerMoney -= cost;
        String label = player.applyPermanentUpgrade(ability);
        addFloatingText(player.getX() + player.getWidth() / 2, player.getY() - 20, "Bought " + label, Color.CYAN);
        return "Bought " + label;
    }

    public int getUpgradeCost(AbilityType ability) {
        if (player == null) return 9999;
        return player.getPermLevel(ability) + 1;
    }

    public int getUpgradeLevel(AbilityType ability) {
        if (player == null) return 0;
        return player.getPermLevel(ability);
    }

    private void updatePowerUps(double deltaTime) {
        long deltaMs = (long) (deltaTime * 1000);

        powerUpSpawnTimer += deltaMs;
        if (powerUpSpawnTimer >= nextPowerUpSpawnDelayMs) {
            powerUpSpawnTimer = 0;
            nextPowerUpSpawnDelayMs = 8000 + powerUpRandom.nextInt(5000);
            if (powerUps.size() < 2) {
                powerUps.add(PowerUp.spawnRandom(powerUpRandom));
            }
        }

        if (player == null) {
            return;
        }

        for (int i = powerUps.size() - 1; i >= 0; i--) {
            PowerUp powerUp = powerUps.get(i);
            powerUp.update(deltaTime);
            if (powerUp.isExpired()) {
                powerUps.remove(i);
                continue;
            }
            if (checkOverlap(player, powerUp)) {
                String label = applyPowerUp(powerUp.getType());
                addFloatingText(powerUp.getX(), powerUp.getY() - 6, label, powerUp.getColor());
                powerUps.remove(i);
            }
        }
    }

    private void updateMoney(double deltaTime) {
        if (player == null) return;
        for (int i = moneyList.size() - 1; i >= 0; i--) {
            Money m = moneyList.get(i);
            m.update(deltaTime);
            if (m.isExpired()) {
                moneyList.remove(i);
                continue;
            }
            if (checkOverlap(player, m)) {
                playerMoney += m.getValue();
                addFloatingText(player.getX() + player.getWidth() / 2, player.getY() - 20, "+$" + m.getValue(), Color.YELLOW);
                moneyList.remove(i);
            }
        }
    }

    private void updateFloatingTexts(double deltaTime) {
        for (int i = floatingTexts.size() - 1; i >= 0; i--) {
            FloatingText text = floatingTexts.get(i);
            text.update(deltaTime);
            if (text.isExpired()) {
                floatingTexts.remove(i);
            }
        }
    }

    private boolean checkOverlap(GameEntity a, GameEntity b) {
        return !(a.getX() + a.getWidth() < b.getX() ||
                 b.getX() + b.getWidth() < a.getX() ||
                 a.getY() + a.getHeight() < b.getY() ||
                 b.getY() + b.getHeight() < a.getY());
    }
    
    // ========== PLAYER CLASS ==========
    public static class Player extends GameEntity {
        private static final double WALK_SPEED = 260.0;
        private static final double RUN_SPEED = 380.0;
        private static final double GRAVITY = 1600.0;
        private static final double JUMP_VELOCITY = -700.0;

        private PlayerState state;
        private int hp;
        private int maxHp = 100;
        private int lives = 3;
        private int facing = 1; // 1 = right, -1 = left
        private double velocityY;
        private boolean onGround;
        private long attackFrameWindow; // Time active attack hitbox is active (ms)
        private long punchCooldownRemaining;
        private long kickCooldownRemaining;
        private boolean attackDelivered;
        private long attackWindowDefault;
        private long punchAttackWindowDefault;
        private long kickAttackWindowDefault;
        private long respawnTimer;
        private boolean moveInputThisFrame;
        private boolean runHeld;
        private boolean downHeld;
        private long actionStateTimer;
        private int currentAttackDamage;
        private boolean crouching;
        private long damageBoostRemainingMs;
        private long speedBoostRemainingMs;
        private long jumpBoostRemainingMs;
        private long damageReductionRemainingMs;
        private long slowRemainingMs;
        private long noJumpRemainingMs;
        // Permanent upgrade levels applied by purchases
        private int permSpeedLevel;
        private int permDamageLevel;
        private int permJumpLevel;
        private int permHealthLevel;
        private int basePunchDamage = 10;
        private int baseKickDamage = 15;
        
        public Player() {
            super(100, CHARACTER_GROUND_Y - 100, 92, 100);
            this.state = PlayerState.IDLE;
            this.hp = maxHp;
            this.velocityY = 0;
            this.onGround = true;
            this.attackFrameWindow = 0;
            this.punchCooldownRemaining = 0;
            this.kickCooldownRemaining = 0;
            this.attackDelivered = false;
            this.attackWindowDefault = 220;
            this.punchAttackWindowDefault = 180;
            this.kickAttackWindowDefault = 220;
            this.respawnTimer = 0;
            this.moveInputThisFrame = false;
            this.runHeld = false;
            this.downHeld = false;
            this.actionStateTimer = 0;
            this.currentAttackDamage = 0;
            this.crouching = false;
            this.damageBoostRemainingMs = 0;
            this.speedBoostRemainingMs = 0;
            this.jumpBoostRemainingMs = 0;
            this.damageReductionRemainingMs = 0;
            this.slowRemainingMs = 0;
            this.noJumpRemainingMs = 0;
            this.permSpeedLevel = 0;
            this.permDamageLevel = 0;
            this.permJumpLevel = 0;
            this.permHealthLevel = 0;
        }
        
        @Override
        public void update(double deltaTime) {
            long deltaMs = (long) (deltaTime * 1000);

            if (state == PlayerState.DEAD && lives > 0) {
                respawnTimer -= deltaMs;
                if (respawnTimer <= 0) {
                    respawn();
                }
                return;
            }

            if (punchCooldownRemaining > 0) {
                punchCooldownRemaining -= deltaMs;
                if (punchCooldownRemaining < 0) {
                    punchCooldownRemaining = 0;
                }
            }

            if (kickCooldownRemaining > 0) {
                kickCooldownRemaining -= deltaMs;
                if (kickCooldownRemaining < 0) {
                    kickCooldownRemaining = 0;
                }
            }

            if (attackFrameWindow > 0) {
                attackFrameWindow -= deltaMs;
                if (attackFrameWindow < 0) {
                    attackFrameWindow = 0;
                    attackDelivered = false;
                }
            }

            if (actionStateTimer > 0) {
                actionStateTimer -= deltaMs;
                if (actionStateTimer <= 0 && onGround && (state == PlayerState.PUNCH || state == PlayerState.KICK || state == PlayerState.HURT)) {
                    state = PlayerState.IDLE;
                }
            }

            tickEffectTimers(deltaMs);

            if (!onGround) {
                velocityY += GRAVITY * deltaTime;
            }

            x += velocityX * deltaTime;
            y += velocityY * deltaTime;

            if (x < 0) {
                x = 0;
            }
            if (x + width > WORLD_WIDTH) {
                x = WORLD_WIDTH - width;
            }

            if (y + height >= CHARACTER_GROUND_Y) {
                y = CHARACTER_GROUND_Y - height;
                velocityY = 0;
                onGround = true;
                if (state == PlayerState.JUMP || state == PlayerState.FALL) {
                    state = PlayerState.IDLE;
                }
            } else {
                onGround = false;
                if (velocityY > 0 || downHeld) {
                    state = PlayerState.FALL;
                }
            }

            if (!moveInputThisFrame && onGround && state != PlayerState.PUNCH && state != PlayerState.KICK && state != PlayerState.HURT) {
                velocityX = 0;
                if (state != PlayerState.IDLE) {
                    state = PlayerState.IDLE;
                }
            }

            moveInputThisFrame = false;
            runHeld = false;
            downHeld = false;
        }
        
        public void moveLeft() {
            if (state == PlayerState.DEAD || state == PlayerState.RESPAWNING) {
                return;
            }
            moveInputThisFrame = true;
            facing = -1;
            velocityX = (runHeld ? -RUN_SPEED : -WALK_SPEED) * getSpeedMultiplier();
            if (onGround && state != PlayerState.PUNCH && state != PlayerState.KICK && state != PlayerState.HURT) {
                state = runHeld ? PlayerState.RUN : PlayerState.WALK;
            }
        }

        public void moveRight() {
            if (state == PlayerState.DEAD || state == PlayerState.RESPAWNING) {
                return;
            }
            moveInputThisFrame = true;
            facing = 1;
            velocityX = (runHeld ? RUN_SPEED : WALK_SPEED) * getSpeedMultiplier();
            if (onGround && state != PlayerState.PUNCH && state != PlayerState.KICK && state != PlayerState.HURT) {
                state = runHeld ? PlayerState.RUN : PlayerState.WALK;
            }
        }

        public void setRunHeld(boolean runHeld) {
            this.runHeld = runHeld;
        }

        public void setDownHeld(boolean downHeld) {
            this.downHeld = downHeld;
        }

        public void jump() {
            if (!onGround || state == PlayerState.DEAD || state == PlayerState.RESPAWNING || noJumpRemainingMs > 0) {
                return;
            }
            velocityY = JUMP_VELOCITY * getJumpMultiplier();
            onGround = false;
            state = PlayerState.JUMP;
        }

        public void punch() {
            if (state == PlayerState.DEAD || state == PlayerState.RESPAWNING) {
                return;
            }
            if (punchCooldownRemaining > 0) {
                return;
            }
            state = PlayerState.PUNCH;
            attackFrameWindow = punchAttackWindowDefault;
            actionStateTimer = 220;
            currentAttackDamage = Math.max(1, (int) Math.round(getBasePunchDamage() * getDamageMultiplier()));
            velocityX = 0;
            punchCooldownRemaining = getAttackCooldownMs(1000);
            attackDelivered = false;
        }

        public void kick() {
            if (state == PlayerState.DEAD || state == PlayerState.RESPAWNING) {
                return;
            }
            if (kickCooldownRemaining > 0) {
                return;
            }
            state = PlayerState.KICK;
            attackFrameWindow = kickAttackWindowDefault;
            actionStateTimer = 260;
            currentAttackDamage = Math.max(1, (int) Math.round(getBaseKickDamage() * getDamageMultiplier()));
            velocityX = 0;
            kickCooldownRemaining = getAttackCooldownMs(2000);
            attackDelivered = false;
        }

        private long getAttackCooldownMs(int baseCooldownMs) {
            double speedMultiplier = 1.0 + (Math.min(permSpeedLevel, 10) * 0.15);
            return Math.max(200L, Math.round(baseCooldownMs / speedMultiplier));
        }

        public int takeDamage(int damage) {
            if (state == PlayerState.DEAD || state == PlayerState.RESPAWNING) {
                return 0;
            }
            int actualDamage = Math.max(1, (int) Math.round(damage * getIncomingDamageMultiplier()));
            hp -= actualDamage;
            if (hp <= 0) {
                hp = 0;
                lives--;
                state = PlayerState.DEAD;
                velocityX = 0;
                attackFrameWindow = 0;
                if (lives > 0) {
                    respawnTimer = 3000;
                }
            } else {
                state = PlayerState.HURT;
                actionStateTimer = 180;
            }
            return actualDamage;
        }

        public void respawn() {
            hp = maxHp;
            x = 100;
            y = CHARACTER_GROUND_Y - height;
            velocityX = 0;
            velocityY = 0;
            onGround = true;
            state = PlayerState.IDLE;
            respawnTimer = 0;
            punchCooldownRemaining = 0;
            kickCooldownRemaining = 0;
            attackFrameWindow = 0;
            attackDelivered = false;
            downHeld = false;
            damageBoostRemainingMs = 0;
            speedBoostRemainingMs = 0;
            jumpBoostRemainingMs = 0;
            damageReductionRemainingMs = 0;
            slowRemainingMs = 0;
            noJumpRemainingMs = 0;
        }
        
        public long getRespawnRemaining() { return respawnTimer; }
        
        public boolean tryDealAttack() {
            if (attackFrameWindow > 0 && !attackDelivered) {
                attackDelivered = true;
                return true;
            }
            return false;
        }
        
        public PlayerState getState() { return state; }
        public int getHp() { return hp; }
        public int getMaxHp() { return maxHp; }
        public int getLives() { return lives; }
        public int getFacing() { return facing; }
        public boolean hasActiveAttack() { return attackFrameWindow > 0; }
        public int getCurrentAttackDamage() { return currentAttackDamage; }
        public boolean isDownHeld() { return downHeld; }
        public boolean isOnGround() { return onGround; }
        public boolean isCrouching() { return crouching; }
        public void setCrouching(boolean c) { this.crouching = c; }

        public void heal(int amount) {
            if (amount <= 0) {
                return;
            }
            hp = Math.min(maxHp, hp + amount);
        }

        public String applyPowerUp(PowerUpType type) {
            switch (type) {
                case DOUBLE_DAMAGE:
                    damageBoostRemainingMs = 5000;
                    return "DMG x2";
                case SPEED_BOOST:
                    speedBoostRemainingMs = 5000;
                    return "SPEED+";
                case HIGH_JUMP:
                    jumpBoostRemainingMs = 5000;
                    return "JUMP+";
                case DAMAGE_REDUCTION:
                    damageReductionRemainingMs = 5000;
                    return "DMG-";
                case SLOW:
                    slowRemainingMs = 5000;
                    return "SLOW";
                case NO_JUMP:
                    noJumpRemainingMs = 5000;
                    return "NO JUMP";
                case LOSE_HP_50:
                    takeDamage(50);
                    return "-50";
                default:
                    return "?";
            }
        }


        // Apply a permanent purchase upgrade to the player
        public String applyPermanentUpgrade(AbilityType type) {
            switch (type) {
                case SPEED:
                    permSpeedLevel++;
                    return "Atk Speed +" + permSpeedLevel;
                case HEALTH:
                    permHealthLevel++;
                    maxHp += 10;
                    hp = Math.min(maxHp, hp + 10);
                    return "Max HP +10";
                case DAMAGE:
                    permDamageLevel++;
                    return "Damage +" + permDamageLevel;
                case JUMP:
                    permJumpLevel++;
                    return "Jump +" + permJumpLevel;
                default:
                    return "";
            }
        }
        public int getPermLevel(AbilityType type) {
            switch (type) {
                case SPEED: return permSpeedLevel;
                case HEALTH: return permHealthLevel;
                case DAMAGE: return permDamageLevel;
                case JUMP: return permJumpLevel;
            }
            return 0;
        }

        private int getBasePunchDamage() { return basePunchDamage + (permDamageLevel * 2); }
        private int getBaseKickDamage() { return baseKickDamage + (permDamageLevel * 3); }

        public List<String> getActiveEffectSummaries() {
            List<String> summaries = new ArrayList<>();
            addEffectSummary(summaries, "DMG x2", damageBoostRemainingMs);
            addEffectSummary(summaries, "SPEED+", speedBoostRemainingMs);
            addEffectSummary(summaries, "JUMP+", jumpBoostRemainingMs);
            addEffectSummary(summaries, "DMG-", damageReductionRemainingMs);
            addEffectSummary(summaries, "SLOW", slowRemainingMs);
            addEffectSummary(summaries, "NO JUMP", noJumpRemainingMs);
            // Permanent levels
            if (permSpeedLevel > 0) summaries.add("SPD L" + permSpeedLevel);
            if (permHealthLevel > 0) summaries.add("HP L" + permHealthLevel);
            if (permDamageLevel > 0) summaries.add("DMG L" + permDamageLevel);
            if (permJumpLevel > 0) summaries.add("JMP L" + permJumpLevel);
            return summaries;
        }

        private void addEffectSummary(List<String> summaries, String label, long remainingMs) {
            if (remainingMs > 0) {
                summaries.add(label + " " + ((remainingMs + 999) / 1000) + "s");
            }
        }

        private void tickEffectTimers(long deltaMs) {
            damageBoostRemainingMs = tickEffect(damageBoostRemainingMs, deltaMs);
            speedBoostRemainingMs = tickEffect(speedBoostRemainingMs, deltaMs);
            jumpBoostRemainingMs = tickEffect(jumpBoostRemainingMs, deltaMs);
            damageReductionRemainingMs = tickEffect(damageReductionRemainingMs, deltaMs);
            slowRemainingMs = tickEffect(slowRemainingMs, deltaMs);
            noJumpRemainingMs = tickEffect(noJumpRemainingMs, deltaMs);
        }

        private long tickEffect(long remainingMs, long deltaMs) {
            remainingMs -= deltaMs;
            return Math.max(0, remainingMs);
        }

        private double getDamageMultiplier() {
            return damageBoostRemainingMs > 0 ? 2.0 : 1.0;
        }

        private double getSpeedMultiplier() {
            double multiplier = 1.0;
            if (speedBoostRemainingMs > 0) {
                multiplier *= 1.35;
            }
            if (slowRemainingMs > 0) {
                multiplier *= 0.65;
            }
            return multiplier;
        }

        private double getJumpMultiplier() {
            double m = jumpBoostRemainingMs > 0 ? 1.4 : 1.0;
            m *= (1.0 + (permJumpLevel * 0.08));
            return m;
        }

        private int getAttackSpeedPercent() {
            return Math.min(10, permSpeedLevel);
        }

        private double getIncomingDamageMultiplier() {
            return damageReductionRemainingMs > 0 ? 0.5 : 1.0;
        }
    }
    
    // ========== ENEMY BASE CLASS ==========
    public static abstract class Enemy extends GameEntity {
        protected EnemyState state;
        protected int hp;
        protected int maxHp;
        protected int damage;
        protected double speed;
        protected double attackRange;
        protected long attackCooldown;
        protected long attackCooldownRemaining;
        protected long attackActiveWindow;
        protected boolean attackDelivered;
        protected long attackActiveWindowDefault;
        protected int facing = 1; // 1 = right, -1 = left
        
        public Enemy(double x, double y, int maxHp, int damage, double speed) {
            super(x, y, 72, 72);
            this.state = EnemyState.WALK;
            this.hp = maxHp;
            this.maxHp = maxHp;
            this.damage = damage;
            this.speed = speed;
            this.attackRange = 56;
            this.attackCooldown = 2000; // 2 seconds between attacks
            this.attackCooldownRemaining = 0;
            this.attackActiveWindow = 0;
            this.attackActiveWindowDefault = 220;
        }
        
        @Override
        public void update(double deltaTime) {
            // Use update(deltaTime, player) for AI behavior.
        }

        public void update(double deltaTime, Player player) {
            if (isDead()) {
                state = EnemyState.DEAD;
                return;
            }

            long deltaMs = (long) (deltaTime * 1000);
            if (attackCooldownRemaining > 0) {
                attackCooldownRemaining -= deltaMs;
                if (attackCooldownRemaining < 0) {
                    attackCooldownRemaining = 0;
                }
            }

            if (attackActiveWindow > 0) {
                attackActiveWindow -= deltaMs;
                if (attackActiveWindow <= 0) {
                    attackActiveWindow = 0;
                    // reset delivered marker for the next attack
                    attackDelivered = false;
                }
            }

            double distanceX = player.getX() - x;
            facing = distanceX >= 0 ? 1 : -1;
            boolean attackTriggered = false;

            if (Math.abs(distanceX) <= attackRange) {
                velocityX = 0;
                if (attackCooldownRemaining == 0) {
                    // begin an attack window during which collision controller can apply damage exactly once
                    attackActiveWindow = this.attackActiveWindowDefault > 0 ? this.attackActiveWindowDefault : 220;
                    attackCooldownRemaining = attackCooldown;
                    attackDelivered = false;
                    attackTriggered = true;
                }
            } else {
                walkToward(player.getX(), deltaTime);
            }

            if (attackActiveWindow > 0 || attackTriggered) {
                state = EnemyState.ATTACK;
            } else {
                state = EnemyState.WALK;
            }

            y = CHARACTER_GROUND_Y - height;
        }
        
        public void walkToward(double targetX, double deltaTime) {
            if (targetX > x) {
                velocityX = speed;
            } else {
                velocityX = -speed;
            }
            x += velocityX * deltaTime;
        }

        public void walkToward(double targetX) {
            // Backwards-compatible stub.
            walkToward(targetX, 0);
        }

        public void attack() {
            state = EnemyState.ATTACK;
        }

        public int takeDamage(int damage) {
            if (state == EnemyState.DEAD) {
                return 0;
            }
            hp -= damage;
            if (hp <= 0) {
                hp = 0;
                state = EnemyState.DEAD;
                velocityX = 0;
            } else {
                state = EnemyState.HURT;
            }
            return damage;
        }

        /**
         * Attempt to apply a single attack hit during the current active window.
         * Returns true only the first time it is called while the window is active.
         */
        public boolean tryDealAttack() {
            if (attackActiveWindow > 0 && !attackDelivered) {
                attackDelivered = true;
                return true;
            }
            return false;
        }

        public boolean isAttackActive() {
            return attackActiveWindow > 0;
        }
        
        public EnemyState getState() { return state; }
        public int getHp() { return hp; }
        public int getMaxHp() { return maxHp; }
        public int getFacing() { return facing; }
        public boolean isDead() { return hp <= 0; }
    }

    // ========== FLYING POWERUP ========== 
    public static class PowerUp extends GameEntity {
        private final PowerUpType type;
        private final Color color;
        private final String label;
        private double fallSpeed = 120.0;
        private long lifeMs = 12000;

        public PowerUp(double x, double y, PowerUpType type, Color color, String label) {
            super(x, y, 34, 34);
            this.type = type;
            this.color = color;
            this.label = label;
        }

        public static PowerUp spawnRandom(Random random) {
            int x = 60 + random.nextInt(Math.max(1, WORLD_WIDTH - 120));
            int roll = random.nextInt(100);
            if (roll < 18) return new PowerUp(x, -40, PowerUpType.DOUBLE_DAMAGE, new Color(255, 215, 0), "x2");
            if (roll < 34) return new PowerUp(x, -40, PowerUpType.SPEED_BOOST, new Color(80, 220, 255), "SPD");
            if (roll < 50) return new PowerUp(x, -40, PowerUpType.HIGH_JUMP, new Color(120, 255, 120), "JMP");
            if (roll < 62) return new PowerUp(x, -40, PowerUpType.MYSTERY_BOX, new Color(210, 120, 255), "?");
            if (roll < 74) return new PowerUp(x, -40, PowerUpType.DAMAGE_REDUCTION, new Color(170, 255, 170), "DMG-");
            if (roll < 84) return new PowerUp(x, -40, PowerUpType.SLOW, new Color(255, 180, 80), "SLOW");
            if (roll < 92) return new PowerUp(x, -40, PowerUpType.NO_JUMP, new Color(255, 120, 120), "NOJ");
            return new PowerUp(x, -40, PowerUpType.LOSE_HP_50, new Color(255, 80, 80), "-50");
        }

        @Override
        public void update(double deltaTime) {
            y += fallSpeed * deltaTime;
            lifeMs -= (long) (deltaTime * 1000);
        }

        public PowerUpType getType() { return type; }
        public Color getColor() { return color; }
        public String getLabel() { return label; }
        public boolean isExpired() { return lifeMs <= 0 || y > WORLD_HEIGHT + 40; }
    }

    // ========== FLOATING TEXT ==========
    public static class FloatingText {
        private final String text;
        private final Color color;
        private double x;
        private double y;
        private long remainingMs = 900;

        public FloatingText(double x, double y, String text, Color color) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
        }

        public void update(double deltaTime) {
            long deltaMs = (long) (deltaTime * 1000);
            remainingMs -= deltaMs;
            y -= 32.0 * deltaTime;
        }

        public boolean isExpired() { return remainingMs <= 0; }
        public double getX() { return x; }
        public double getY() { return y; }
        public String getText() { return text; }
        public Color getColor() { return color; }
        public long getRemainingMs() { return remainingMs; }
    }

    // ========== MONEY DROPS ==========
    public static class Money extends GameEntity {
        private final int value;
        private double fallSpeed = 160.0;
        private long lifeMs = 12000;

        public Money(double x, double y, int value) {
            super(x, y, 26, 26);
            this.value = value;
        }

        public void update(double deltaTime) {
            y += fallSpeed * deltaTime;
            lifeMs -= (long) (deltaTime * 1000);
        }

        public boolean isExpired() { return lifeMs <= 0 || y > WORLD_HEIGHT + 40; }
        public int getValue() { return value; }
    }
    
    // ========== ENEMY SUBCLASSES ==========
    public static class Goblin extends Enemy {
        public Goblin(double x, double y) {
            super(x, y, 30, 5, 100.0);
            this.width = 72;
            this.height = 72;
            this.y = CHARACTER_GROUND_Y - height;
            this.attackCooldown = 2000; // goblins are slow
            this.attackActiveWindowDefault = 220;
        }
    }
    
    public static class Wolf extends Enemy {
        public Wolf(double x, double y) {
            super(x, y, 40, 10, 170.0);
            this.width = 104;
            this.height = 80;
            this.y = CHARACTER_GROUND_Y - height;
            this.attackCooldown = 1500; // wolves faster
            this.attackActiveWindowDefault = 160;
        }
    }
    
    public static class Rogue extends Enemy {
        public Rogue(double x, double y) {
            super(x, y, 60, 15, 130.0);
            this.width = 86;
            this.height = 96;
            this.y = CHARACTER_GROUND_Y - height;
            this.attackCooldown = 1800; // rogue medium
            this.attackActiveWindowDefault = 240;
        }
    }
    
    // ========== GAME ENTITY BASE CLASS ==========
    public static abstract class GameEntity {
        protected double x, y;
        protected double width, height;
        protected double velocityX;
        
        public GameEntity(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.velocityX = 0;
        }
        
        public abstract void update(double deltaTime);
        
        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public void setX(double x) { this.x = x; }
        public void setY(double y) { this.y = y; }
    }
    
    // ========== SPAWNER CLASS ==========
    public static class Spawner {
        private final Random random = new Random();
        private int level;
        private long spawnInterval; // milliseconds
        private long spawnTimer;
        private int maxEnemiesOnScreen;
        private int wolfSpawnIndex;
        
        public Spawner(int level) {
            this.level = level;
            this.spawnInterval = 3000; // 3 seconds, tunable per level
            this.spawnTimer = 0;
            this.maxEnemiesOnScreen = 4;
            this.wolfSpawnIndex = 0;
            configureLevelSpawning();
        }
        
        /**
         * Configure spawning rules based on level.
         */
        private void configureLevelSpawning() {
            if (level == 1) {
                spawnInterval = 3200;
                maxEnemiesOnScreen = 3;
            } else if (level == 2) {
                spawnInterval = 2800;
                maxEnemiesOnScreen = 3;
            } else if (level == 3) {
                spawnInterval = 2400;
                maxEnemiesOnScreen = 4;
            } else {
                spawnInterval = 2100;
                maxEnemiesOnScreen = 4;
            }
        }
        
        /**
         * Update spawner and add enemies to the list if conditions met.
         */
        public void update(double deltaTime, List<Enemy> activeEnemies) {
            if (activeEnemies.size() >= maxEnemiesOnScreen) {
                return;
            }

            spawnTimer += (long) (deltaTime * 1000);
            if (spawnTimer >= spawnInterval) {
                spawnTimer = 0;
                Enemy spawned = spawnRandomEnemy();
                if (spawned != null) {
                    activeEnemies.add(spawned);
                }
            }
        }
        
        /**
         * Create a random enemy based on level config.
         */
        public Enemy spawnRandomEnemy() {
            int spawnX = WORLD_WIDTH + 20;
            int roll;
            if (level == 1) {
                return new Goblin(spawnX, GROUND_Y - 72);
            }

            if (level == 2) {
                roll = random.nextInt(2);
                return (roll == 0) ? new Goblin(spawnX, GROUND_Y - 72) : new Wolf(nextWolfSpawnX(spawnX), GROUND_Y - 80);
            }

            if (level == 3) {
                roll = random.nextInt(3);
                if (roll == 0) {
                    return new Goblin(spawnX, GROUND_Y - 72);
                }
                if (roll == 1) {
                    return new Wolf(nextWolfSpawnX(spawnX), GROUND_Y - 80);
                }
                return new Rogue(spawnX, GROUND_Y - 96);
            }

            // Level 4: all enemy types.
            roll = random.nextInt(3);
            if (roll == 0) {
                return new Goblin(spawnX, GROUND_Y - 72);
            }
            if (roll == 1) {
                return new Wolf(nextWolfSpawnX(spawnX), GROUND_Y - 80);
            }
            return new Rogue(spawnX, GROUND_Y - 96);
        }

        private int nextWolfSpawnX(int baseSpawnX) {
            int spawnX = baseSpawnX + (wolfSpawnIndex % 3) * 120;
            wolfSpawnIndex++;
            return spawnX;
        }
    }
}
