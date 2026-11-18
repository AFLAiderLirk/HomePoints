package io.github.aflaiderlirk.homepoints;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Small YAML-backed repository. All operations run on the server thread. */
final class HomeStore {
    private final File homesFile;
    private YamlConfiguration data;

    HomeStore(HomePointsPlugin plugin) throws IOException, InvalidConfigurationException {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IOException("Cannot create plugin data directory");
        }
        homesFile = new File(plugin.getDataFolder(), "homes.yml");
        data = new YamlConfiguration();
        if (homesFile.exists()) {
            data.load(homesFile);
        }
    }

    static String normalized(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private String homePath(UUID owner, String name) {
        return "players." + owner + ".homes." + normalized(name);
    }

    boolean exists(UUID owner, String name) {
        return data.isConfigurationSection(homePath(owner, name));
    }

    List<String> names(UUID owner) {
        ConfigurationSection section = data.getConfigurationSection("players." + owner + ".homes");
        if (section == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>(section.getKeys(false));
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    int count(UUID owner) {
        return names(owner).size();
    }

    Location find(UUID owner, String name) {
        ConfigurationSection section = data.getConfigurationSection(homePath(owner, name));
        if (section == null) {
            return null;
        }

        World world = null;
        String worldId = section.getString("world-uuid");
        if (worldId != null) {
            try {
                world = Bukkit.getWorld(UUID.fromString(worldId));
            } catch (IllegalArgumentException ignored) {
                // Fall back to the world name if an external edit damaged the UUID.
            }
        }
        if (world == null) {
            world = Bukkit.getWorld(section.getString("world-name", ""));
        }
        if (world == null) {
            return null;
        }
        return new Location(world,
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    void save(UUID owner, String name, Location location) throws IOException {
        YamlConfiguration draft = copy();
        String base = homePath(owner, name);
        draft.set(base + ".world-uuid", location.getWorld().getUID().toString());
        draft.set(base + ".world-name", location.getWorld().getName());
        draft.set(base + ".x", location.getX());
        draft.set(base + ".y", location.getY());
        draft.set(base + ".z", location.getZ());
        draft.set(base + ".yaw", location.getYaw());
        draft.set(base + ".pitch", location.getPitch());
        persist(draft);
    }

    void delete(UUID owner, String name) throws IOException {
        YamlConfiguration draft = copy();
        draft.set(homePath(owner, name), null);
        persist(draft);
    }

    private YamlConfiguration copy() {
        YamlConfiguration draft = new YamlConfiguration();
        try {
            draft.loadFromString(data.saveToString());
        } catch (InvalidConfigurationException ex) {
            throw new IllegalStateException("Generated YAML could not be parsed", ex);
        }
        return draft;
    }

    private void persist(YamlConfiguration draft) throws IOException {
        Path target = homesFile.toPath();
        Path temp = Files.createTempFile(target.getParent(), "homes-", ".tmp");
        try {
            Files.writeString(temp, draft.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            data = draft;
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
