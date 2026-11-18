package io.github.aflaiderlirk.homepoints;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** GUI event routing through custom holders; never matches inventories by title. */
final class HomeMenus implements Listener {
    private static final int PAGE_SIZE = 45;

    private abstract static class OwnedHolder implements InventoryHolder {
        final UUID owner;
        private Inventory inventory;

        OwnedHolder(UUID owner) {
            this.owner = owner;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class HomeListHolder extends OwnedHolder {
        final int page;
        final Map<Integer, String> names = new HashMap<>();

        HomeListHolder(UUID owner, int page) {
            super(owner);
            this.page = page;
        }
    }

    private static final class ConfirmHolder extends OwnedHolder {
        final String name;
        final int returnPage;

        ConfirmHolder(UUID owner, String name, int returnPage) {
            super(owner);
            this.name = name;
            this.returnPage = returnPage;
        }
    }

    private final HomePointsPlugin plugin;
    private final HomeStore store;
    private final Messages messages;
    private final Teleports teleports;

    HomeMenus(HomePointsPlugin plugin, HomeStore store, Messages messages, Teleports teleports) {
        this.plugin = plugin;
        this.store = store;
        this.messages = messages;
        this.teleports = teleports;
    }

    void openList(Player player, int requestedPage) {
        List<String> all = store.names(player.getUniqueId());
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        HomeListHolder holder = new HomeListHolder(player.getUniqueId(), page);
        Map<String, String> shared = Map.of(
                "page", Integer.toString(page + 1), "pages", Integer.toString(pages),
                "count", Integer.toString(all.size()),
                "max", Integer.toString(Math.max(0, plugin.getConfig().getInt("homes.max-per-player", 3))));
        String title = messages.text("menu.title", shared);
        Inventory inventory = plugin.getServer().createInventory(holder, 54, title);
        holder.setInventory(inventory);

        for (int index = page * PAGE_SIZE; index < Math.min((page + 1) * PAGE_SIZE, all.size()); index++) {
            String name = all.get(index);
            Location location = store.find(player.getUniqueId(), name);
            Map<String, String> variables = new HashMap<>(shared);
            variables.put("home", name);
            variables.put("world", location == null
                    ? plugin.getConfig().getString("menu.unavailable-world-name", "unavailable")
                    : location.getWorld().getName());
            variables.put("x", location == null ? "?" : Integer.toString(location.getBlockX()));
            variables.put("y", location == null ? "?" : Integer.toString(location.getBlockY()));
            variables.put("z", location == null ? "?" : Integer.toString(location.getBlockZ()));

            int slot = index % PAGE_SIZE;
            Material icon = Material.matchMaterial(plugin.getConfig().getString("menu.home-icon", "COMPASS"));
            if (icon == null || icon.isAir() || !icon.isItem()) {
                icon = Material.COMPASS;
            }
            inventory.setItem(slot, item(icon,
                    plugin.getConfig().getString("menu.home-name", "{home}"),
                    plugin.getConfig().getStringList("menu.home-lore"), variables));
            holder.names.put(slot, name);
        }

        if (all.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER,
                    plugin.getConfig().getString("menu.empty-name", "No homes"),
                    plugin.getConfig().getStringList("menu.empty-lore"), shared));
        }
        inventory.setItem(49, item(Material.BOOK,
                plugin.getConfig().getString("menu.info-name", "Homes: {count}/{max}"), List.of(), shared));
        if (page > 0) {
            inventory.setItem(45, item(Material.ARROW,
                    plugin.getConfig().getString("menu.previous-name", "Previous"), List.of(), shared));
        }
        if (page + 1 < pages) {
            inventory.setItem(53, item(Material.ARROW,
                    plugin.getConfig().getString("menu.next-name", "Next"), List.of(), shared));
        }
        player.openInventory(inventory);
    }

    private void openConfirmation(Player player, String name, int returnPage) {
        ConfirmHolder holder = new ConfirmHolder(player.getUniqueId(), name, returnPage);
        Map<String, String> variables = Map.of("home", name);
        Inventory inventory = plugin.getServer().createInventory(holder, 9,
                messages.text("menu.confirm-title", variables));
        holder.setInventory(inventory);
        inventory.setItem(3, item(Material.RED_WOOL,
                plugin.getConfig().getString("menu.confirm-name", "Confirm"), List.of(), variables));
        inventory.setItem(5, item(Material.LIME_WOOL,
                plugin.getConfig().getString("menu.cancel-name", "Cancel"), List.of(), variables));
        player.openInventory(inventory);
    }

    private ItemStack item(Material material, String title, List<String> lore, Map<String, String> variables) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.format(title, variables));
            meta.setLore(lore.stream().map(line -> messages.format(line, variables)).toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void openListNextTick(Player player, int page) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                openList(player, page);
            }
        });
    }

    private void openConfirmationNextTick(Player player, String name, int returnPage) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                openConfirmation(player, name, returnPage);
            }
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof OwnedHolder holder)) {
            return;
        }
        // Also block shift-clicks from the player's own inventory into our GUI.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.owner.equals(player.getUniqueId())
                || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        int slot = event.getRawSlot();
        if (holder instanceof HomeListHolder list) {
            if (slot == 45 && list.page > 0) {
                openListNextTick(player, list.page - 1);
            } else if (slot == 53) {
                openListNextTick(player, list.page + 1);
            } else if (list.names.containsKey(slot)) {
                String name = list.names.get(slot);
                if (event.isShiftClick()) {
                    if (!player.hasPermission("homepoints.delhome")) {
                        messages.send(player, "no-permission");
                        return;
                    }
                    openConfirmationNextTick(player, name, list.page);
                } else if (event.isLeftClick()) {
                    if (!player.hasPermission("homepoints.home")) {
                        messages.send(player, "no-permission");
                        return;
                    }
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.closeInventory();
                            teleports.request(player, name);
                        }
                    });
                }
            }
        } else if (holder instanceof ConfirmHolder confirm) {
            if (slot == 5) {
                openListNextTick(player, confirm.returnPage);
            } else if (slot == 3) {
                if (!player.hasPermission("homepoints.delhome")) {
                    messages.send(player, "no-permission");
                    plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
                    return;
                }
                try {
                    if (store.exists(player.getUniqueId(), confirm.name)) {
                        store.delete(player.getUniqueId(), confirm.name);
                        messages.send(player, "home-deleted", Map.of("home", confirm.name));
                    } else {
                        messages.send(player, "home-not-found", Map.of("home", confirm.name));
                    }
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not delete home: " + ex.getMessage());
                    messages.send(player, "storage-error");
                }
                openListNextTick(player, confirm.returnPage);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof OwnedHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }
}
