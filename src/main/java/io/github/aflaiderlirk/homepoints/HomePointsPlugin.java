package io.github.aflaiderlirk.homepoints;

import java.io.IOException;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class HomePointsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            HomeStore store = new HomeStore(this);
            Messages messages = new Messages(this);
            Teleports teleports = new Teleports(this, store, messages);
            HomeMenus menus = new HomeMenus(this, store, messages, teleports);
            HomeCommands commands = new HomeCommands(this, store, messages, teleports, menus);

            Objects.requireNonNull(getCommand("sethome")).setExecutor(commands);
            Objects.requireNonNull(getCommand("home")).setExecutor(commands);
            Objects.requireNonNull(getCommand("delhome")).setExecutor(commands);
            Objects.requireNonNull(getCommand("sethome")).setTabCompleter(commands);
            Objects.requireNonNull(getCommand("home")).setTabCompleter(commands);
            Objects.requireNonNull(getCommand("delhome")).setTabCompleter(commands);

            getServer().getPluginManager().registerEvents(teleports, this);
            getServer().getPluginManager().registerEvents(menus, this);
            getLogger().info("HomePoints enabled.");
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            getLogger().severe("Could not initialize homes.yml: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }
}
