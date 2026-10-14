package me.aura.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class AuraAntiCheat extends JavaPlugin
        implements Listener {

    private final Map<UUID, Location> lastLocations =
            new HashMap<>();

    private final Map<UUID, Integer> speedVL =
            new HashMap<>();

    private final Map<UUID, Integer> flyVL =
            new HashMap<>();

    private final Map<UUID, Integer> spiderVL =
            new HashMap<>();

    private final Map<UUID, Integer> reachVL =
            new HashMap<>();

    private final Map<UUID, Integer> combatVL =
            new HashMap<>();

    private final Map<UUID, Integer> velocityVL =
            new HashMap<>();

    private final Map<UUID, Integer> breakVL =
            new HashMap<>();

    private final Map<UUID, Integer> inventoryVL =
            new HashMap<>();

    private final Map<UUID, Long> lastAlert =
            new HashMap<>();

    private final Map<UUID, Long> lastDamage =
            new HashMap<>();

    private final Map<UUID, Long> lastBreak =
            new HashMap<>();

    private final Map<UUID, Long> lastInventoryAction =
            new HashMap<>();

    private final Map<UUID, Deque<Long>> attacks =
            new HashMap<>();

    private final Map<UUID, UUID> lastTarget =
            new HashMap<>();

    private final Map<UUID, Integer> targetSwitches =
            new HashMap<>();

    private BukkitTask movementTask;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            lastLocations.put(
                    player.getUniqueId(),
                    player.getLocation().clone()
            );
        }

        movementTask = Bukkit.getScheduler().runTaskTimer(
                this,
                this::checkMovement,
                1L,
                1L
        );

        getLogger().info("AuraAntiCheat enabled!");
        getLogger().info("Movement and combat checks initialized.");
    }

    @Override
    public void onDisable() {
        if (movementTask != null) {
            movementTask.cancel();
        }

        lastLocations.clear();
        speedVL.clear();
        flyVL.clear();
        spiderVL.clear();
        reachVL.clear();
        combatVL.clear();
        velocityVL.clear();
        breakVL.clear();
        inventoryVL.clear();
        lastAlert.clear();
        lastDamage.clear();
        lastBreak.clear();
        lastInventoryAction.clear();
        attacks.clear();
        lastTarget.clear();
        targetSwitches.clear();

        getLogger().info("AuraAntiCheat disabled!");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        lastLocations.put(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getLocation().clone()
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();

        lastLocations.remove(id);
        attacks.remove(id);
        lastTarget.remove(id);
        targetSwitches.remove(id);
    }

    /*
     * MOVEMENT CHECKS
     */

    private void checkMovement() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();

            Location current = player.getLocation();
            Location previous = lastLocations.put(
                    id,
                    current.clone()
            );

            if (previous == null) {
                continue;
            }

            if (!eligibleForMovement(player, previous, current)) {
                continue;
            }

            double dx = current.getX() - previous.getX();
            double dy = current.getY() - previous.getY();
            double dz = current.getZ() - previous.getZ();

            double horizontal = Math.sqrt(dx * dx + dz * dz);

            /*
             * SPEED
             *
             * Conservative heuristic. This is not a definitive
             * speed-hack detector.
             */

            if (player.isOnGround()
                    && player.isSprinting()
                    && !player.hasPotionEffect(
                            PotionEffectType.SPEED)
                    && horizontal > 0.48
                    && horizontal < 1.5) {

                flag(player, speedVL, "Speed",
                        "horizontal=" + format(horizontal));
            }

            /*
             * FLY
             *
             * A single vertical movement is never enough to
             * establish cheating. We only flag repeated movement
             * that deserves review.
             */

            if (!player.isOnGround()
                    && !player.isFlying()
                    && !player.isGliding()
                    && !player.isSwimming()
                    && !player.isInsideVehicle()
                    && !player.hasPotionEffect(
                            PotionEffectType.LEVITATION)
                    && !player.hasPotionEffect(
                            PotionEffectType.SLOW_FALLING)
                    && Math.abs(dy) < 0.02
                    && horizontal > 0.10) {

                flag(player, flyVL, "Fly",
                        "unusual vertical movement");
            }

            /*
             * SPIDER
             *
             * Detecting wall climbing reliably requires more
             * context than a single movement sample.
             */

            if (!player.isOnGround()
                    && dy > 0.35
                    && isNearWall(current)
                    && !player.isSwimming()
                    && !player.isInsideVehicle()
                    && !player.isClimbing()) {

                flag(player, spiderVL, "Spider",
                        "unusual upward movement near wall");
            }
        }
    }

    private boolean eligibleForMovement(
            Player player,
            Location previous,
            Location current
    ) {
        if (!player.isOnline()
                || player.hasPermission("auraac.bypass")
                || player.getGameMode() != GameMode.SURVIVAL
                || player.isFlying()
                || player.isInsideVehicle()
                || player.isGliding()
                || player.isSwimming()
                || player.isRiptiding()
                || player.isDead()) {
            return false;
        }

        if (previous.getWorld() != current.getWorld()) {
            return false;
        }

        if (player.getPing() > 250) {
            return false;
        }

        if (previous.distanceSquared(current) > 16) {
            return false;
        }

        return !isSpecialMovementEnvironment(current);
    }

    private boolean isSpecialMovementEnvironment(
            Location location
    ) {
        Material feet = location.getBlock().getType();

        Material below = location.clone()
                .subtract(0, 0.1, 0)
                .getBlock()
                .getType();

        return feet == Material.WATER
                || feet == Material.LAVA
                || feet == Material.COBWEB
                || feet == Material.POWDER_SNOW
                || feet == Material.SCAFFOLDING
                || below == Material.ICE
                || below == Material.PACKED_ICE
                || below == Material.BLUE_ICE
                || below == Material.SLIME_BLOCK
                || below == Material.HONEY_BLOCK
                || below == Material.SOUL_SAND
                || below == Material.SOUL_SOIL;
    }

    private boolean isNearWall(Location location) {
        World world = location.getWorld();

        if (world == null) {
            return false;
        }

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        return isSolid(world, x + 1, y, z)
                || isSolid(world, x - 1, y, z)
                || isSolid(world, x, y, z + 1)
                || isSolid(world, x, y, z - 1);
    }

    private boolean isSolid(World world, int x, int y, int z) {
        return world.getBlockAt(x, y, z)
                .getType().isSolid();
    }

    /*
     * COMBAT, KILLAURA, AIM AND REACH
     */

    @EventHandler(ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (attacker.hasPermission("auraac.bypass")
                || attacker.getGameMode() != GameMode.SURVIVAL
                || attacker.isInsideVehicle()
                || attacker.isDead()) {
            return;
        }

        UUID id = attacker.getUniqueId();

        Location eye = attacker.getEyeLocation();
        BoundingBox box = target.getBoundingBox();

        /*
         * Measure the shortest distance from the attacker's
         * eye position to the target's bounding box.
         */

        double closestX = clamp(
                eye.getX(), box.getMinX(), box.getMaxX()
        );

        double closestY = clamp(
                eye.getY(), box.getMinY(), box.getMaxY()
        );

        double closestZ = clamp(
                eye.getZ(), box.getMinZ(), box.getMaxZ()
        );

        double dx = eye.getX() - closestX;
        double dy = eye.getY() - closestY;
        double dz = eye.getZ() - closestZ;

        double distance = Math.sqrt(
                dx * dx + dy * dy + dz * dz
        );

        /*
         * REACH
         *
         * High ping makes the result less reliable.
         * The threshold is deliberately conservative.
         */

        int ping = Math.max(0, attacker.getPing());

        double allowedReach = 3.25
                + Math.min(ping, 200) / 200.0 * 0.35;

        if (distance > allowedReach) {
            flag(attacker, reachVL, "Reach",
                    "distance=" + format(distance)
                            + ", ping=" + ping);
        }

        /*
         * AIM DIRECTION
         *
         * Compare the look direction with the direction
         * from the eyes to the nearest point on the target.
         */

        Vector look = eye.getDirection().normalize();

        Vector towardTarget = new Vector(
                closestX - eye.getX(),
                closestY - eye.getY(),
                closestZ - eye.getZ()
        );

        if (towardTarget.lengthSquared() > 0.0001) {
            towardTarget.normalize();

            double dot = look.dot(towardTarget);

            if (dot < -0.1) {
                flag(attacker, combatVL, "CombatAim",
                        "attack direction is unusual");
            }
        }

        /*
         * KILLAURA HEURISTICS
         *
         * Track attack frequency and target changes.
         * Neither pattern alone proves KillAura.
         */

        long now = System.currentTimeMillis();

        Deque<Long> history = attacks.computeIfAbsent(
                id, key -> new ArrayDeque<>()
        );

        while (!history.isEmpty()
                && now - history.peekFirst() > 1000) {
            history.removeFirst();
        }

        history.addLast(now);

        UUID targetId = target.getUniqueId();
        UUID previousTarget = lastTarget.put(id, targetId);

        if (previousTarget != null
                && !previousTarget.equals(targetId)) {

            int switches = targetSwitches.merge(
                    id, 1, Integer::sum
            );

            if (switches >= 6 && history.size() >= 8) {
                flag(attacker, combatVL, "KillAuraReview",
                        "rapid target switching");
                targetSwitches.put(id, 0);
            }
        } else if (previousTarget == null) {
            targetSwitches.put(id, 0);
        }

        if (history.size() > 15) {
            flag(attacker, combatVL, "CombatRate",
                    "unusually frequent attacks");
            history.clear();
        }

        /*
         * Record the weapon for review.
         * Vanilla swords and axes do not automatically grant
         * different reach distances.
         */

        ItemStack weapon = attacker.getInventory()
                .getItemInMainHand();

        Material weaponType = weapon.getType();

        getLogger().fine(
                "[AuraAC] Combat sample: "
                        + attacker.getName()
                        + " weapon=" + weaponType
                        + " ping=" + ping
                        + " distance=" + format(distance)
        );
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /*
     * VELOCITY / KNOCKBACK
     *
     * Bukkit events alone cannot reliably determine whether
     * a player is using a velocity cheat. Record damage events
     * for later testing instead of automatically flagging them.
     */

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (player.hasPermission("auraac.bypass")) {
            return;
        }

        lastDamage.put(
                player.getUniqueId(),
                System.currentTimeMillis()
        );
    }

    /*
     * FASTBREAK
     *
     * Track break frequency as a review signal.
     * Block hardness, tools, enchantments, effects and latency
     * must be considered before making a real decision.
     */

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("auraac.bypass")
                || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long previous = lastBreak.getOrDefault(id, 0L);

        lastBreak.put(id, now);

        if (previous != 0L && now - previous < 65) {
            flag(player, breakVL, "FastBreakReview",
                    "rapid consecutive block breaks");
        }
    }

    /*
     * INVENTORY
     *
     * This is only a placeholder for future packet-level
     * analysis. Normal inventory events cannot identify
     * most inventory cheats reliably.
     */

    private void recordInventoryActivity(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        long previous = lastInventoryAction.getOrDefault(id, 0L);

        lastInventoryAction.put(id, now);

        if (previous != 0L && now - previous < 10) {
            flag(player, inventoryVL, "InventoryReview",
                    "rapid inventory interaction");
        }
    }

    /*
     * FLIGHT TOGGLE
     *
     * Record unauthorized flight toggles for review.
     */

    @EventHandler(ignoreCancelled = true)
    public void onFlightToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        if (event.isFlying()
                && player.getGameMode() == GameMode.SURVIVAL
                && !player.hasPermission("auraac.bypass")) {

            flag(player, flyVL, "FlightToggle",
                    "flight toggle in survival");
        }
    }

    /*
     * ALERTS
     */

    private void flag(
            Player player,
            Map<UUID, Integer> violations,
            String check,
            String detail
    ) {
        UUID id = player.getUniqueId();

        int count = violations.merge(id, 1, Integer::sum);

        long now = System.currentTimeMillis();
        long previous = lastAlert.getOrDefault(id, 0L);

        if (now - previous < 3000) {
            return;
        }

        lastAlert.put(id, now);

        String message = String.format(
                "[AuraAC] %s suspected: %s | VL=%d | %s",
                check,
                player.getName(),
                count,
                detail
        );

        getLogger().warning(message);

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("auraac.alerts")) {
                staff.sendMessage(message);
            }
        }
    }

    private String format(double value) {
        return String.format(java.util.Locale.US, "%.3f", value);
    }
            }
