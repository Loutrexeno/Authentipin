package fr.authentipin.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class VerificationStore {
    private final JavaPlugin plugin;
    private final File file;
    private final ConcurrentMap<UUID, VerificationRecord> records;

    public VerificationStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "verifications.yml");
        this.records = new ConcurrentHashMap<>();
    }

    public void load() {
        records.clear();
        if (!file.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            String discordId = section.getString(key + ".discordId", "");
            String pin = section.getString(key + ".pin", "");
            boolean verified = section.getBoolean(key + ".verified", false);
            records.put(uuid, new VerificationRecord(uuid, discordId, pin, verified));
        }
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        ConfigurationSection section = config.createSection("players");
        for (VerificationRecord record : records.values()) {
            String base = record.uuid().toString();
            section.set(base + ".discordId", record.discordId());
            section.set(base + ".pin", record.pin());
            section.set(base + ".verified", record.verified());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Unable to save verifications.yml: " + e.getMessage());
        }
    }

    public void setNewPin(UUID uuid, String discordId, String pin) {
        records.put(uuid, new VerificationRecord(uuid, discordId, pin, false));
    }

    public Optional<VerificationRecord> get(UUID uuid) {
        return Optional.ofNullable(records.get(uuid));
    }

    public Optional<VerificationRecord> getActiveByDiscordId(String discordId) {
        return records.values().stream()
                .filter(record -> record.discordId().equals(discordId))
                .filter(record -> !record.verified())
                .findFirst();
    }

    public Optional<VerificationRecord> getVerifiedByDiscordId(String discordId) {
        return records.values().stream()
                .filter(record -> record.discordId().equals(discordId))
                .filter(VerificationRecord::verified)
                .findFirst();
    }

    public boolean isVerified(UUID uuid) {
        VerificationRecord record = records.get(uuid);
        return record != null && record.verified();
    }

    public boolean verify(UUID uuid, String pinInput) {
        VerificationRecord record = records.get(uuid);
        if (record == null) {
            return false;
        }
        if (!record.pin().equals(pinInput)) {
            return false;
        }

        records.put(uuid, new VerificationRecord(uuid, record.discordId(), record.pin(), true));
        return true;
    }

    public Optional<VerificationRecord> unlink(UUID uuid) {
        return Optional.ofNullable(records.remove(uuid));
    }
}
