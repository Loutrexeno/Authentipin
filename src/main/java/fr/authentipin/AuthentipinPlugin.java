package fr.authentipin;

import fr.authentipin.command.AuthentipinAdminCommand;
import fr.authentipin.data.VerificationStore;
import fr.authentipin.discord.DiscordVerificationBot;
import fr.authentipin.listener.PlayerVerificationListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuthentipinPlugin extends JavaPlugin {
    private VerificationStore store;
    private DiscordVerificationBot discordBot;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        store = new VerificationStore(this);
        store.load();

        getServer().getPluginManager().registerEvents(new PlayerVerificationListener(this, store), this);
        AuthentipinAdminCommand adminCommand = new AuthentipinAdminCommand(this, store);
        if (getCommand("authentipin") != null) {
            getCommand("authentipin").setExecutor(adminCommand);
            getCommand("authentipin").setTabCompleter(adminCommand);
        }

        String token = getConfig().getString("discord.token", "");
        String guildId = getConfig().getString("discord.guild-id", "");

        if (token.isBlank() || guildId.isBlank() || token.startsWith("PUT_") || guildId.startsWith("PUT_")) {
            getLogger().warning("Discord bot not configured. Fill discord.token and discord.guild-id in config.yml");
            return;
        }

        discordBot = new DiscordVerificationBot(this, store, guildId);
        try {
            discordBot.start(token);
            getLogger().info("Discord bot connected and ready.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLogger().severe("Discord bot startup was interrupted.");
        } catch (Exception e) {
            getLogger().severe("Unable to start Discord bot: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.stop();
        }

        if (store != null) {
            store.save();
        }
    }
}
