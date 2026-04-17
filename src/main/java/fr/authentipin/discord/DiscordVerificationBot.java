package fr.authentipin.discord;

import fr.authentipin.data.VerificationRecord;
import fr.authentipin.data.VerificationStore;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiscordVerificationBot extends ListenerAdapter {
    private final JavaPlugin plugin;
    private final VerificationStore store;
    private final String guildId;
    private final SecureRandom random;
    private final HttpClient httpClient;
    private JDA jda;

    public DiscordVerificationBot(JavaPlugin plugin, VerificationStore store, String guildId) {
        this.plugin = plugin;
        this.store = store;
        this.guildId = guildId;
        this.random = new SecureRandom();
        this.httpClient = HttpClient.newHttpClient();
    }

    public void start(String token) throws InterruptedException {
        jda = JDABuilder.createDefault(token)
                .addEventListeners(this)
                .build()
                .awaitReady();
    }

    public void stop() {
        if (jda != null) {
            jda.shutdownNow();
            jda = null;
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        Guild guild = event.getJDA().getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().severe("Discord guild not found: " + guildId);
            return;
        }

        guild.upsertCommand(
                Commands.slash("verify", "Generate a PIN code for Minecraft")
                        .addOption(OptionType.STRING, "username", "Minecraft player username", true)
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("verify")) {
            return;
        }

        String username = Objects.requireNonNull(event.getOption("username")).getAsString().trim();
        if (username.isBlank()) {
            event.reply("Invalid username.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        UUID uuid = resolveUuidFromUsername(username);
        if (uuid == null) {
            event.reply("Minecraft username not found: `" + username + "`")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String discordId = event.getUser().getId();

        Optional<VerificationRecord> verifiedRecordForDiscord = store.getVerifiedByDiscordId(discordId);
        if (verifiedRecordForDiscord.isPresent() && !verifiedRecordForDiscord.get().uuid().equals(uuid)) {
            event.reply("Your Discord account is already permanently linked to another Minecraft account.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Optional<VerificationRecord> existingForUuid = store.get(uuid);
        if (existingForUuid.isPresent()
                && existingForUuid.get().verified()
                && !existingForUuid.get().discordId().equals(discordId)) {
            event.reply("This Minecraft account is already permanently linked to another Discord account.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (verifiedRecordForDiscord.isPresent()
                && verifiedRecordForDiscord.get().uuid().equals(uuid)) {
            event.reply("Your Discord account is already verified and linked to this Minecraft account.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Optional<VerificationRecord> activeRecord = store.getActiveByDiscordId(discordId);
        if (activeRecord.isPresent() && !activeRecord.get().uuid().equals(uuid)) {
            event.reply("You already have an active code for another Minecraft account. "
                            + "Use that code in-game first or complete verification before generating a new one.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String pin = String.format("%04d", random.nextInt(10_000));
        if ("0000".equals(pin)) {
            pin = "0001";
        }

        String finalPin = pin;
        Bukkit.getScheduler().runTask(plugin, () -> {
            store.setNewPin(uuid, discordId, finalPin);
            store.save();
        });

        event.reply("Your Authentipin code is: **" + pin + "**\nPlayer: **" + username + "**\nEnter it in-game to verify.")
                .setEphemeral(true)
                .queue();
    }

    private UUID resolveUuidFromUsername(String username) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return null;
            }

            String body = response.body();
            Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*\"([a-fA-F0-9]{32})\"").matcher(body);
            if (!matcher.find()) {
                return null;
            }

            return toDashedUuid(matcher.group(1));
        } catch (Exception e) {
            plugin.getLogger().warning("Error resolving Mojang UUID for " + username + ": " + e.getMessage());
            return null;
        }
    }

    private UUID toDashedUuid(String rawUuid) {
        String dashed = rawUuid.replaceFirst(
                "([a-fA-F0-9]{8})([a-fA-F0-9]{4})([a-fA-F0-9]{4})([a-fA-F0-9]{4})([a-fA-F0-9]{12})",
                "$1-$2-$3-$4-$5"
        );
        return UUID.fromString(dashed);
    }
}
