package me.aura.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuraAntiCheat extends JavaPlugin implements Listener {

private final Map<UUID, Integer> violations = new ConcurrentHashMap<>();
private final Map<UUID, Long> lastAlert = new ConcurrentHashMap<>();

@Override
public void onEnable() {
    Bukkit.getPluginManager().registerEvents(this, this);
    getLogger().info("AuraAntiCheat enabled!");
}

@Override
public void onDisable() {
    violations.clear();
    lastAlert.clear();
    getLogger().info("AuraAntiCheat disabled!");
}

@EventHandler(ignoreCancelled = true)
public void onPlayerMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();

    if (!player.isOnline()
            || player.getGameMode() == GameMode.CREATIVE
            || player.getGameMode() == GameMode.SPECTATOR
            || player.isFlying()
            || player.isInsideVehicle()
            || player.isGliding()
            || player.isSwimming()
            || player.isRiptiding()) {
        return;
    }

    Location from = event.getFrom();
    Location to = event.getTo();

    if (to == null || from.getWorld() != to.getWorld()) {
        return;
    }

    // Ignore rotation-only movement.
    if (from.getX() == to.getX()
            && from.getY() == to.getY()
            && from.getZ() == to.getZ()) {
        return;
    }

    // Only perform this basic check on ground-level movement.
    if (!player.isOnGround() || !player.isSprinting()) {
        return;
    }

    double dx = to.getX() - from.getX();
    double dz = to.getZ() - from.getZ();
    double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

    // Conservative initial threshold, not a definitive cheat detector.
    if (horizontalDistance > 0.85) {
        recordViolation(player, horizontalDistance);
    }
}

private void recordViolation(Player player, double distance) {
    UUID uuid = player.getUniqueId();
    int count = violations.merge(uuid, 1, Integer::sum);

    long now = System.currentTimeMillis();
    long previous = lastAlert.getOrDefault(uuid, 0L);

    if (now - previous < 3000) {
        return;
    }

    lastAlert.put(uuid, now);

    String message = String.format(
            "[AuraAC] Suspicious movement: %s | distance=%.3f | VL=%d",
            player.getName(),
            distance,
            count
    );

    getLogger().warning(message);

    for (Player staff : Bukkit.getOnlinePlayers()) {
        if (staff.hasPermission("auraac.alerts")) {
            staff.sendMessage(message);
        }
    }
}

}
