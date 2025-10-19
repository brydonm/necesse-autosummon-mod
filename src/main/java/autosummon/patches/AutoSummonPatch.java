package autosummon.patches;

import autosummon.AutoSummonConfig;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;
import net.bytebuddy.asm.Advice;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This patch automatically summons using the rightmost staff in inventory when below max summons.
 * It targets the clientTick method in the PlayerMob class with proper multiplayer isolation.
 */
@ModMethodPatch(target = PlayerMob.class, name = "clientTick", arguments = {})
public class AutoSummonPatch {

    // Per-player cooldown tracking to prevent multiplayer interference
    public static final ConcurrentHashMap<Integer, Long> playerCooldowns = new ConcurrentHashMap<>();
    
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
     * This code runs after the original clientTick method.
     * It processes auto-summoning for the local player with proper multiplayer isolation.
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
            
            // If turning off, clear all summon buffs
            if (!AutoSummonConfig.isEnabled()) {
                clearSummonBuffs(player);
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

        // Check if the player is below their max summon count
        int currentSummons = 0;
        for (necesse.entity.mobs.buffs.ActiveBuff buff : player.buffManager.getArrayBuffs()) {
            if (buff.buff instanceof necesse.entity.mobs.buffs.staticBuffs.SummonedMobBuff) {
                currentSummons += buff.getStacks();
            }
        }
        int maxSummons = player.buffManager.getModifier(necesse.entity.mobs.buffs.BuffModifiers.MAX_SUMMONS);
        
        if (currentSummons < maxSummons) {
            // Scan the hotbar from right to left (slot 9 to 0) to find the rightmost staff
            for (int i = 9; i >= 0; i--) {
                InventoryItem hotbarItem = player.getInv().main.getItem(i);

                if (hotbarItem != null && hotbarItem.item instanceof SummonToolItem) {
                    SummonToolItem staff = (SummonToolItem) hotbarItem.item;
                    String canAttackResult = staff.canAttack(player.getLevel(), (int)player.getX(), (int)player.getY(), player, hotbarItem);
                    
                    if (canAttackResult == null) {
                        // Final safety check: ensure this is still the local player
                        if (player.getLevel().getClient() != null && player.getLevel().getClient().getPlayer() == player) {
                            necesse.inventory.PlayerInventorySlot slot = new necesse.inventory.PlayerInventorySlot(player.getInv().main, i);
                            player.tryAttack(slot, (int)player.getX(), (int)player.getY());

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