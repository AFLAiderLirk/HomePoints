package io.github.aflaiderlirk.homepoints;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

/** Delayed teleports are scheduled synchronously; the timer is cleared on cancellation. */
final class Teleports implements Listener {
    private record Pending(String name, BukkitTask task) { }

    private final HomePointsPlugin plugin;
    private final HomeStore store;
    private final Messages messages;
    private final Map<UUID, Pending> pending = new HashMap<>();
    // Entries are removed as soon as the cooldown expires; no unbounded UUID cache.
    private final Map<UUID, Long> nextAllowedAt = new HashMap<>();

    Teleports(HomePointsPlugin plugin, HomeStore store, Messages messages) {
        this.plugin = plugin;
        this.store = store;
        this.messages = messages;
    }

    void request(Player player, String name) {
        UUID uuid = player.getUniqueId();
        if (!store.exists(uuid, name)) {
            messages.send(player, "home-not-found", Map.of("home", name));
            return;
        }
        if (store.find(uuid, name) == null) {
            messages.send(player, "world-unavailable", Map.of("home", name));
            return;
        }
        if (pending.containsKey(uuid)) {
            messages.send(player, "already-teleporting");
            return;
        }

        int cooldown = Math.max(0, plugin.getConfig().getInt("teleport.cooldown-seconds", 30));
        long now = System.currentTimeMillis();
        long next = nextAllowedAt.getOrDefault(uuid, 0L);
        if (now < next) {
            long seconds = (next - now + 999) / 1000;
            messages.send(player, "teleport-cooldown", Map.of("seconds", Long.toString(seconds)));
            return;
        }

        int delay = Math.max(0, plugin.getConfig().getInt("teleport.delay-seconds", 3));
        if (delay == 0) {
            finish(player, name);
            return;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending current = pending.remove(uuid);
            if (current != null && player.isOnline()) {
                finish(player, current.name());
            }
        }, Math.max(1L, delay * 20L));
        pending.put(uuid, new Pending(name, task));
        messages.send(player, "teleport-wait", Map.of(
                "home", name, "seconds", Integer.toString(delay)));
    }

    private void finish(Player player, String name) {
        // Reload the home here, so deleted homes cannot still be used during warmup.
        if (!store.exists(player.getUniqueId(), name)) {
            messages.send(player, "home-not-found", Map.of("home", name));
            return;
        }
        Location target = store.find(player.getUniqueId(), name);
        if (target == null) {
            messages.send(player, "world-unavailable", Map.of("home", name));
            return;
        }
        if (player.teleport(target)) {
            int cooldown = Math.max(0, plugin.getConfig().getInt("teleport.cooldown-seconds", 30));
            if (cooldown > 0) {
                UUID uuid = player.getUniqueId();
                long expiresAt = System.currentTimeMillis() + cooldown * 1000L;
                nextAllowedAt.put(uuid, expiresAt);
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> nextAllowedAt.remove(uuid, expiresAt), cooldown * 20L);
            }
            messages.send(player, "teleport-success", Map.of("home", name));
        } else {
            messages.send(player, "teleport-failed");
        }
    }

    private void cancel(Player player, boolean notify) {
        Pending scheduled = pending.remove(player.getUniqueId());
        if (scheduled == null) {
            return;
        }
        scheduled.task().cancel();
        if (notify) {
            messages.send(player, "teleport-cancelled");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("teleport.cancel-on-move", true)) {
            return;
        }
        if (!pending.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || to.getWorld() != from.getWorld()
                || Math.abs(to.getX() - from.getX()) > 0.0001
                || Math.abs(to.getY() - from.getY()) > 0.0001
                || Math.abs(to.getZ() - from.getZ()) > 0.0001) {
            cancel(event.getPlayer(), true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // Other plugins' teleports must not leave an old warmup running.
        if (pending.containsKey(event.getPlayer().getUniqueId())) {
            cancel(event.getPlayer(), true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getFinalDamage() <= 0
                || !plugin.getConfig().getBoolean("teleport.cancel-on-damage", true)
                || !pending.containsKey(player.getUniqueId())) {
            return;
        }

        cancel(player, false);
        messages.send(player, "teleport-cancelled-damage");
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        cancel(event.getPlayer(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer(), false);
    }
}
