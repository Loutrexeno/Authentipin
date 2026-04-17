package fr.authentipin.listener;

import fr.authentipin.data.VerificationStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerVerificationListener implements Listener {
    private static final String VERIFY_TITLE = ChatColor.DARK_AQUA + "Authentipin - PIN";
    private static final int SLOT_DISPLAY = 4;
    private static final int SLOT_VALIDATE = 22;
    private static final int SLOT_CLEAR = 24;
    private static final int SLOT_BACKSPACE = 20;
    private final JavaPlugin plugin;
    private final VerificationStore store;
    private final Map<UUID, String> inputs = new HashMap<>();

    public PlayerVerificationListener(JavaPlugin plugin, VerificationStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (store.isVerified(uuid)) {
            return;
        }

        if (store.get(uuid).isEmpty()) {
            player.kickPlayer(ChatColor.RED + "You must run /verify on Discord before joining.");
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> openVerifyGui(player), 20L);
        player.sendMessage(ChatColor.YELLOW + "Enter your Authentipin PIN code to play.");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!VERIFY_TITLE.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        int slot = event.getRawSlot();
        String currentInput = inputs.getOrDefault(player.getUniqueId(), "");

        if (slot == SLOT_VALIDATE) {
            if (currentInput.length() != 4) {
                player.sendMessage(ChatColor.RED + "The code must contain 4 digits.");
                refreshGui(player, event.getInventory(), currentInput);
                return;
            }

            boolean ok = store.verify(player.getUniqueId(), currentInput);
            if (!ok) {
                player.sendMessage(ChatColor.RED + "Incorrect code.");
                inputs.put(player.getUniqueId(), "");
                refreshGui(player, event.getInventory(), "");
                return;
            }

            store.save();
            inputs.remove(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Verification successful, have fun!");
            return;
        }

        if (slot == SLOT_CLEAR) {
            currentInput = "";
            inputs.put(player.getUniqueId(), currentInput);
            refreshGui(player, event.getInventory(), currentInput);
            return;
        }

        if (slot == SLOT_BACKSPACE) {
            if (!currentInput.isEmpty()) {
                currentInput = currentInput.substring(0, currentInput.length() - 1);
            }
            inputs.put(player.getUniqueId(), currentInput);
            refreshGui(player, event.getInventory(), currentInput);
            return;
        }

        int digit = getDigitFromSlot(slot);
        if (digit == -1 || currentInput.length() >= 4) {
            return;
        }

        currentInput = currentInput + digit;
        inputs.put(player.getUniqueId(), currentInput);
        refreshGui(player, event.getInventory(), currentInput);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!VERIFY_TITLE.equals(event.getView().getTitle())) {
            return;
        }
        if (store.isVerified(player.getUniqueId())) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> openVerifyGui(player));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (store.isVerified(player.getUniqueId())) {
            return;
        }
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!store.isVerified(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!store.isVerified(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!store.isVerified(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!store.isVerified(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!store.isVerified(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (store.isVerified(event.getPlayer().getUniqueId())) {
            return;
        }

        if (!event.getMessage().toLowerCase().startsWith("/pin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You must verify your PIN before using commands.");
        }
    }

    private void openVerifyGui(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, VERIFY_TITLE);
        String input = inputs.getOrDefault(player.getUniqueId(), "");
        refreshGui(player, inv, input);
        player.openInventory(inv);
    }

    private void refreshGui(Player player, Inventory inv, String input) {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(SLOT_DISPLAY, createItem(Material.PAPER, ChatColor.AQUA + "Code: " + maskInput(input)));
        inv.setItem(SLOT_BACKSPACE, createItem(Material.ORANGE_DYE, ChatColor.GOLD + "Delete 1"));
        inv.setItem(SLOT_VALIDATE, createItem(Material.LIME_DYE, ChatColor.GREEN + "Validate"));
        inv.setItem(SLOT_CLEAR, createItem(Material.BARRIER, ChatColor.RED + "Clear all"));

        inv.setItem(10, createDigitItem(1));
        inv.setItem(11, createDigitItem(2));
        inv.setItem(12, createDigitItem(3));
        inv.setItem(13, createDigitItem(4));
        inv.setItem(14, createDigitItem(5));
        inv.setItem(15, createDigitItem(6));
        inv.setItem(16, createDigitItem(7));
        inv.setItem(17, createDigitItem(8));
        inv.setItem(18, createDigitItem(9));
        inv.setItem(19, createDigitItem(0));
    }

    private ItemStack createDigitItem(int digit) {
        return createItem(Material.LIGHT_BLUE_DYE, ChatColor.WHITE + Integer.toString(digit));
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String maskInput(String input) {
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            masked.append('*');
        }
        while (masked.length() < 4) {
            masked.append('_');
        }
        return masked.toString();
    }

    private int getDigitFromSlot(int slot) {
        return switch (slot) {
            case 10 -> 1;
            case 11 -> 2;
            case 12 -> 3;
            case 13 -> 4;
            case 14 -> 5;
            case 15 -> 6;
            case 16 -> 7;
            case 17 -> 8;
            case 18 -> 9;
            case 19 -> 0;
            default -> -1;
        };
    }
}
