package io.github.aflaiderlirk.homepoints;

import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

final class Messages {
    private final HomePointsPlugin plugin;

    Messages(HomePointsPlugin plugin) {
        this.plugin = plugin;
    }

    String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }

    String format(String raw, Map<String, String> variables) {
        String expanded = raw == null ? "" : raw;
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            expanded = expanded.replace("{" + variable.getKey() + "}", variable.getValue());
        }
        return color(expanded);
    }

    String text(String path, Map<String, String> variables) {
        return format(plugin.getConfig().getString(path, ""), variables);
    }

    void send(CommandSender recipient, String key) {
        send(recipient, key, Map.of());
    }

    void send(CommandSender recipient, String key, Map<String, String> variables) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String message = plugin.getConfig().getString("messages." + key, "");
        if (!message.isBlank()) {
            recipient.sendMessage(format(prefix + message, variables));
        }
    }
}
