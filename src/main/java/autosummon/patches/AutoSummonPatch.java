package autosummon.patches;

import autosummon.AutoSummonConfig;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;
import net.bytebuddy.asm.Advice;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This patch automatically summons using the rightmost staff in inventory when
 * below max summons.
 * It targets the clientTick method in the PlayerMob class with proper
 * multiplayer isolation.
 */
@ModMethodPatch(target = PlayerMob.class, name = "clientTick", arguments = {})
public class AutoSummonPatch {

    // Per-player cooldown tracking to prevent multiplayer interference
    public static final ConcurrentHashMap<Integer, Long> playerCooldowns = new ConcurrentHashMap<>();

    // Timer for delayed clearing
    private static Timer clearTimer = new Timer("AutoSummonClearTimer", true);

    /**
     * Clear all SummonedMobBuff stacks from the player and despawn followers
     */
    public static void clearSummonBuffs(PlayerMob player) {
        player.buffManager.removeBuff("summonedmob", true);
    }

    /**
     * Clean up old player cooldowns to prevent memory leaks
     */
    public static void cleanupOldCooldowns(long currentTime) {
        // Remove cooldowns older than 10 seconds to prevent memory leaks
        long cleanupThreshold = currentTime - 10000; // 10 seconds in milliseconds
        playerCooldowns.entrySet().removeIf(entry -> entry.getValue() < cleanupThreshold);
    }

    /**
     * Schedule delayed clearing of summon buffs (like setTimeout in JS)
     */
    public static void scheduleDelayedClear(PlayerMob player) {
        clearTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // Only clear if the mod is still disabled
                if (!AutoSummonConfig.isEnabled()) {
                    clearSummonBuffs(player);
                }
            }
        }, 500); // 500ms delay
    }

    /**
     * Get the current summon count for the player using the buff bar method
     * @param player The player to check
     * @return The current number of active summons
     */
    public static int getCurrentSummonCount(PlayerMob player) {
        int currentSummons = 0;
        for (necesse.entity.mobs.buffs.ActiveBuff buff : player.buffManager.getArrayBuffs()) {
            if (buff.buff instanceof necesse.entity.mobs.buffs.staticBuffs.SummonedMobBuff) {
                currentSummons += buff.getStacks();
            }
        }
        System.out.println("[Auto Summon] Current summons: " + currentSummons + " (using buff bar method)");
        return currentSummons;
    }

    /**
     * Get the maximum summon count for a specific staff, or fall back to player max count
     * @param staff The summon staff to check
     * @param player The player using the staff
     * @param hotbarItem The inventory item containing the staff
     * @param playerMaxSummons The player's maximum summon count
     * @return The maximum summon count for this staff
     */
    public static int getStaffMaxSummons(SummonToolItem staff, PlayerMob player, InventoryItem hotbarItem, int playerMaxSummons) {
        String staffName = staff.getDisplayName(hotbarItem);
        
        try {
            // Try to get staff-specific summon count using reflection with correct parameters
            java.lang.reflect.Method getMaxSummonsMethod = staff.getClass().getMethod("getMaxSummons", InventoryItem.class, necesse.entity.mobs.itemAttacker.ItemAttackerMob.class);
            Object result = getMaxSummonsMethod.invoke(staff, hotbarItem, player);
            
            if (result instanceof Integer) {
                int staffMax = (Integer) result;
                // If staff has its own max summon count, use that directly (don't limit by player max)
                System.out.println("[Auto Summon] Staff max summons: " + staffMax + " (using staff's own limit) - " + staffName);
                return staffMax;
            }
        } catch (Exception e) {
            // Method doesn't exist or failed, staff has no custom max summon count
        }
        
        // If we get here, the staff doesn't have its own max summon count, so use player's max
        System.out.println("[Auto Summon] Staff max summons: " + playerMaxSummons + " (using player max) - " + staffName);
        return playerMaxSummons;
    }

    /**
     * This code runs after the original clientTick method.
     * It processes auto-summoning for the local player with proper multiplayer
     * isolation.
     */
    @Advice.OnMethodExit
    static void onExit(@Advice.This PlayerMob player) {
        // We only want this logic to run for the client who is controlling the player
        if (player == null || !player.isClient() || !player.isPlayer) {
            return;
        }

        // Additional validation: ensure this is the local player
        if (player.getLevel() == null || !player.getLevel().isClient()) {
            return;
        }

        // CRITICAL: Only run for the actual local player, not other players
        if (player.getLevel().getClient() == null || player.getLevel().getClient().getPlayer() != player) {
            return;
        }

        // Check if we need to send a chat message (from control activation)
        if (AutoSummonConfig.checkAndClearNeedsChatMessage()) {
            String status = AutoSummonConfig.isEnabled() ? "ON" : "OFF";
            String message = "[Auto Summon] " + status;

            // If turning off, schedule delayed clearing (like setTimeout in JS)
            if (!AutoSummonConfig.isEnabled()) {
                scheduleDelayedClear(player);
            }

            // Send to global chat using the client's chat system
            if (player.getLevel() != null && player.getLevel().isClient() && player.getLevel().getClient() != null) {
                necesse.engine.network.client.Client client = player.getLevel().getClient();
                if (client != null && client.chat != null) {
                    client.chat.addMessage(message);
                }
            }
        }

        // Only apply if the mod is enabled
        if (!AutoSummonConfig.isEnabled()) {
            return;
        }

        // Get player-specific cooldown
        int playerId = player.getUniqueID();
        long currentTime = player.getWorldEntity().getTime();

        // Clean up old cooldowns periodically to prevent memory leaks
        cleanupOldCooldowns(currentTime);

        Long lastCheckTime = playerCooldowns.get(playerId);

        if (lastCheckTime != null && currentTime < lastCheckTime) {
            return; // Don't run if this player is on cooldown
        }

        // Get current summon count more efficiently
        int currentSummons = getCurrentSummonCount(player);
        int playerMaxSummons = player.buffManager.getModifier(necesse.entity.mobs.buffs.BuffModifiers.MAX_SUMMONS);

        // Scan the hotbar from right to left (slot 9 to 0) to find the rightmost staff
        for (int i = 9; i >= 0; i--) {
            InventoryItem hotbarItem = player.getInv().main.getItem(i);

            if (hotbarItem != null && hotbarItem.item instanceof SummonToolItem) {
                SummonToolItem staff = (SummonToolItem) hotbarItem.item;
                
                // Check if this staff has a custom summon count limit
                int staffMaxSummons = getStaffMaxSummons(staff, player, hotbarItem, playerMaxSummons);
                
                // Only proceed if we're below the staff's limit
                if (currentSummons < staffMaxSummons) {
                    String canAttackResult = staff.canAttack(player.getLevel(), (int) player.getX(),
                            (int) player.getY(), player, hotbarItem);

                    if (canAttackResult == null) {
                        // Final safety check: ensure this is still the local player
                        if (player.getLevel().getClient() != null
                                && player.getLevel().getClient().getPlayer() == player) {
                            
                            necesse.inventory.PlayerInventorySlot slot = new necesse.inventory.PlayerInventorySlot(
                                    player.getInv().main, i);

                            player.tryAttack(slot, (int) player.getX(), (int) player.getY());

                            // Immediately try to stop any attack animation
                            try {
                                player.doAndSendStopAttackAttacker(true);
                            } catch (Exception e) {
                                // Method doesn't exist or failed, ignore
                            }

                            // Set the cooldown for this specific player to avoid using all staffs instantly
                            playerCooldowns.put(playerId, currentTime + AutoSummonConfig.getSummonCheckCooldown());
                        }

                        break;
                    }
                }
            }
        }
    }
}