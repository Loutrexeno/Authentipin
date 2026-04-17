package fr.authentipin.command;

import fr.authentipin.data.VerificationRecord;
import fr.authentipin.data.VerificationStore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuthentipinAdminCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final VerificationStore store;
    private final HttpClient httpClient;

    public AuthentipinAdminCommand(JavaPlugin plugin, VerificationStore store) {
        this.plugin = plugin;
        this.store = store;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("authentipin.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("unlink")) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " unlink <username|uuid> <reason>");
            return true;
        }

        String target = args[1];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();
        if (reason.isBlank()) {
            sender.sendMessage(ChatColor.RED + "A reason is required.");
            return true;
        }

        UUID uuid = parseUuidOrResolveUsername(target);
        if (uuid == null) {
            sender.sendMessage(ChatColor.RED + "Username/UUID not found: " + target);
            return true;
        }

        Optional<VerificationRecord> removed = store.unlink(uuid);
        if (removed.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No link found for " + target + ".");
            return true;
        }

        store.save();
        VerificationRecord record = removed.get();

        sender.sendMessage(ChatColor.GREEN + "Link removed for " + target + " (UUID: " + uuid + ").");
        plugin.getLogger().info("[UNLINK] By=" + sender.getName()
                + " | UUID=" + uuid
                + " | DiscordId=" + record.discordId()
                + " | Reason=" + reason);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return "unlink".startsWith(args[0].toLowerCase()) ? List.of("unlink") : Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private UUID parseUuidOrResolveUsername(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            return resolveUuidFromUsername(input);
        }
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

            Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*\"([a-fA-F0-9]{32})\"").matcher(response.body());
            if (!matcher.find()) {
                return null;
            }

            return toDashedUuid(matcher.group(1));
        } catch (Exception e) {
            plugin.getLogger().warning("Error resolving UUID for unlink (" + username + "): " + e.getMessage());
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
