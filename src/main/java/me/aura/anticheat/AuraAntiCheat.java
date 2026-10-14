package me.aura.anticheat;

import org.bukkit.plugin.java.JavaPlugin;

public final class AuraAntiCheat extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("AuraAntiCheat enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("AuraAntiCheat disabled!");
    }
}
