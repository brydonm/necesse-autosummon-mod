package autosummon.patches;

import autosummon.AutoSummonConfig;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;
import net.bytebuddy.asm.Advice;

/**
 * This patch automatically summons using the rightmost staff in inventory when below max summons.
 * It targets the clientTick method in the PlayerMob class.
 */
@ModMethodPatch(target = PlayerMob.class, name = "clientTick", arguments = {})
public class AutoSummonPatch {

    public static long nextCheckTime = 0;
    
    /**
     * Clear all SummonedMobBuff stacks from the player and despawn followers
     */
    public static void clearSummonBuffs(PlayerMob player) {
        player.buffManager.removeBuff("summonedmob", true);
    }

    /**
     * This code runs after the original clientTick method.
     */
    @Advice.OnMethodExit
    static void onExit(@Advice.This PlayerMob player) {        
        // We only want this logic to run for the client who is controlling the player
        if (!player.isClient() || !player.isPlayer) {
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
            if (player.getLevel().isClient() && player.getLevel().getClient() != null) {
                necesse.engine.network.client.Client client = player.getLevel().getClient();
                if (client.chat != null) {
                    client.chat.addMessage(message);
                }
            }
        }

        // Only apply if the mod is enabled
        if (!AutoSummonConfig.isEnabled()) {
            return;
        }

        long currentTime = player.getWorldEntity().getTime();
        if (currentTime < nextCheckTime) {
            return; // Don't run if we're on cooldown
        }

        // Check if the player is below their max summon count
        // We'll count SummonedMobBuff buff stacks to track current summons
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

                // Check if the slot contains a summoning staff
                if (hotbarItem != null && hotbarItem.item instanceof SummonToolItem) {
                    SummonToolItem staff = (SummonToolItem) hotbarItem.item;

                    // The canAttack method checks for things like mana, cooldowns, and other restrictions.
                    // If it returns null, it means the staff can be used.
                    String canAttackResult = staff.canAttack(player.getLevel(), (int)player.getX(), (int)player.getY(), player, hotbarItem);
                    
                    if (canAttackResult == null) {
                        // Use the staff from the hotbar slot we found. Target coordinates don't matter much.
                        // Try using the player's attack system instead of calling onAttack directly
                        try {
                            // Use the player's tryAttack method which should handle summoning properly
                            necesse.inventory.PlayerInventorySlot slot = new necesse.inventory.PlayerInventorySlot(player.getInv().main, i);
                            player.tryAttack(slot, (int)player.getX(), (int)player.getY());
                        } catch (Exception e) {
                            System.err.println("AutoSummonPatch: tryAttack failed: " + e.getMessage());
                        }

                        // Set the cooldown for the next check to avoid using all staffs instantly
                        nextCheckTime = currentTime + AutoSummonConfig.getSummonCheckCooldown();

                        // Break the loop so we only summon one minion per check
                        break;
                    }
                }
            }
        }
    }
}