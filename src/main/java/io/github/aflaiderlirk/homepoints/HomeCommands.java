package io.github.aflaiderlirk.homepoints;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class HomeCommands implements CommandExecutor, TabCompleter {
    private final HomePointsPlugin plugin;
    private final HomeStore store;
    private final Messages messages;
    private final Teleports teleports;
    private final HomeMenus menus;

    HomeCommands(HomePointsPlugin plugin, HomeStore store, Messages messages,
                 Teleports teleports, HomeMenus menus) {
        this.plugin = plugin;
        this.store = store;
        this.messages = messages;
        this.teleports = teleports;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }

        String action = command.getName().toLowerCase(java.util.Locale.ROOT);
        String permission = switch (action) {
            case "sethome" -> "homepoints.sethome";
            case "delhome" -> "homepoints.delhome";
            default -> "homepoints.home";
        };
        if (!player.hasPermission(permission)) {
            messages.send(player, "no-permission");
            return true;
        }
        if (action.equals("home") && args.length == 0) {
            menus.openList(player, 0);
            return true;
        }
        if (args.length != 1) {
            messages.send(player, switch (action) {
                case "sethome" -> "usage-set";
                case "delhome" -> "usage-delete";
                default -> "usage-home";
            });
            return true;
        }

        String name = HomeStore.normalized(args[0]);
        if (!validName(name)) {
            messages.send(player, "invalid-name");
            return true;
        }

        switch (action) {
            case "sethome" -> {
                int limit = Math.max(0, plugin.getConfig().getInt("homes.max-per-player", 3));
                if (!store.exists(player.getUniqueId(), name) && store.count(player.getUniqueId()) >= limit) {
                    messages.send(player, "limit-reached", Map.of("max", Integer.toString(limit)));
                    return true;
                }
                try {
                    store.save(player.getUniqueId(), name, player.getLocation());
                    messages.send(player, "home-saved", Map.of("home", name));
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not save homes: " + ex.getMessage());
                    messages.send(player, "storage-error");
                }
            }
            case "home" -> teleports.request(player, name);
            case "delhome" -> delete(player, name);
            default -> { return false; }
        }
        return true;
    }

    void delete(Player player, String name) {
        if (!store.exists(player.getUniqueId(), name)) {
            messages.send(player, "home-not-found", Map.of("home", name));
            return;
        }
        try {
            store.delete(player.getUniqueId(), name);
            messages.send(player, "home-deleted", Map.of("home", name));
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not delete home: " + ex.getMessage());
            messages.send(player, "storage-error");
        }
    }

    static boolean validName(String name) {
        // An explicit, non-configurable safety check prevents YAML dotted paths and regex abuse.
        return name.matches("[a-z0-9_-]{1,16}");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        String permission = command.getName().equalsIgnoreCase("delhome")
                ? "homepoints.delhome" : "homepoints.home";
        if (!command.getName().equalsIgnoreCase("sethome") && !player.hasPermission(permission)) {
            return List.of();
        }
        if (command.getName().equalsIgnoreCase("sethome")) {
            return List.of();
        }
        return store.names(player.getUniqueId()).stream()
                .filter(name -> name.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }
}
