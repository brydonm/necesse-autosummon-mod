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
    
    // Per-player tick counters for performance optimization when at max summons
    public static final ConcurrentHashMap<Integer, Integer> playerTickCounters = new ConcurrentHashMap<>();

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
        
        // Clean up tick counters for players who haven't been active recently
        playerTickCounters.entrySet().removeIf(entry -> !playerCooldowns.containsKey(entry.getKey()));
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
     * Get the current summon count for the player using the buff bar method (fast)
     * @param player The player to check
     * @return The current number of active summons
     */
    public static int getCurrentSummonCountBuffBar(PlayerMob player) {
        int currentSummons = 0;
        for (necesse.entity.mobs.buffs.ActiveBuff buff : player.buffManager.getArrayBuffs()) {
            if (buff.buff instanceof necesse.entity.mobs.buffs.staticBuffs.SummonedMobBuff) {
                currentSummons += buff.getStacks();
            }
        }
        return currentSummons;
    }

    /**
     * Get the current summon count for the player by counting actual summoned mobs following them (accurate but slower)
     * @param player The player to check
     * @param staff The specific staff to check summons for
     * @param hotbarItem The inventory item containing the staff
     * @return The current number of active summons for this specific staff
     */
    public static int getCurrentSummonCountFollowers(PlayerMob player, SummonToolItem staff, InventoryItem hotbarItem) {
        int currentSummons = 0;
        
        try {
            // Get all mobs in the level
            for (necesse.entity.mobs.Mob mob : player.getLevel().entityManager.mobs) {
                if (mob instanceof necesse.entity.mobs.summon.SummonedMob) {
                    // Check if this summoned mob is following our player
                    necesse.entity.mobs.Mob followingMob = mob.getFollowingMob();
                    if (followingMob == player) {
                        // Check if this summoned mob was created by the specific staff
                        if (isSummonedByStaff(mob, staff, hotbarItem)) {
                            currentSummons++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If follower method fails, fall back to buff bar method
            return getCurrentSummonCountBuffBar(player);
        }
        
        return currentSummons;
    }

    /**
     * Check if a summoned mob was created by a specific staff
     * @param summonedMob The summoned mob to check
     * @param staff The staff to check against
     * @return True if the mob was summoned by this staff
     */
    private static boolean isSummonedByStaff(necesse.entity.mobs.Mob summonedMob, SummonToolItem staff, InventoryItem hotbarItem) {
        try {
            // Get the mob's string ID
            String mobStringID = summonedMob.getStringID();
            
            // Get the staff's mobStringID (first parameter from super() call)
            String staffMobStringID = staff.mobStringID;
            
            // Check if the mob's string ID matches the staff's mobStringID
            if (mobStringID != null && staffMobStringID != null && mobStringID.equals(staffMobStringID)) {
                return true;
            }
            
            return false;
                   
        } catch (Exception e) {
            // If all methods fail, assume it's not from this staff
            return false;
        }
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
        try {
            // Check for global custom summon limit first
            if (AutoSummonConfig.getSettings().hasGlobalCustomLimit()) {
                // Only apply global limit to staffs that have their own counters (drawMaxSummons = false)
                if (!staff.drawMaxSummons) {
                    return AutoSummonConfig.getSettings().getGlobalCustomSummonLimit();
                }
            }
            
            // Try to get staff-specific summon count using reflection with correct parameters
            java.lang.reflect.Method getMaxSummonsMethod = staff.getClass().getMethod("getMaxSummons", InventoryItem.class, necesse.entity.mobs.itemAttacker.ItemAttackerMob.class);
            Object result = getMaxSummonsMethod.invoke(staff, hotbarItem, player);
            
            if (result instanceof Integer) {
                int staffMax = (Integer) result;
                // If staff has its own max summon count, use that directly (don't limit by player max)
                return staffMax;
            }
        } catch (Exception e) {
            // Method doesn't exist or failed, staff has no custom max summon count
        }
        
        // If we get here, the staff doesn't have its own max summon count, so use player's max
        return playerMaxSummons;
    }

    /**
     * Check if the player is at max summons using the first found staff (performance optimization)
     * @param player The player to check
     * @return True if the first staff is at max capacity
     */
    public static boolean isAtMaxSummons(PlayerMob player) {
        int playerMaxSummons = player.buffManager.getModifier(necesse.entity.mobs.buffs.BuffModifiers.MAX_SUMMONS);
        
        // Find the first summoning staff in hotbar
        for (int i = 9; i >= 0; i--) {
            InventoryItem hotbarItem = player.getInv().main.getItem(i);
            
            if (hotbarItem != null && hotbarItem.item instanceof SummonToolItem) {
                SummonToolItem staff = (SummonToolItem) hotbarItem.item;
                int staffMaxSummons = getStaffMaxSummons(staff, player, hotbarItem, playerMaxSummons);
                
                // Use fast buff bar method for performance
                int currentSummons = getCurrentSummonCountBuffBar(player);
                
                // Return true if this staff is at max capacity
                return currentSummons >= staffMaxSummons;
            }
        }
        
        // No staffs found, skip anyway to prevent performance issues
        return true;
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
            String message = AutoSummonConfig.getAndClearChatMessage();
            if (message.isEmpty()) {
                // Default toggle message
                String status = AutoSummonConfig.isEnabled() ? "ON" : "OFF";
                message = "[Auto Summon] " + status;
            } else {
                // Custom message
                message = "[Auto Summon] " + message;
            }

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

        // Performance optimization: When at max summons, only run every 5 ticks
        Integer tickCounter = playerTickCounters.get(playerId);
        if (tickCounter == null) {
            tickCounter = 0;
        }
        
        // Check if we're at max summons (quick check)
        boolean atMaxSummons = isAtMaxSummons(player);
        
        if (atMaxSummons) {
            // Only run every 5 ticks when at max summons
            tickCounter++;
            if (tickCounter < 5) {
                playerTickCounters.put(playerId, tickCounter);
                return;
            }
            // Reset counter when we reach 5
            tickCounter = 0;
        }
        
        // Update tick counter
        playerTickCounters.put(playerId, tickCounter);

        // Check if player is currently attacking - if so, don't interrupt
        if (player.isAttacking) {
            return; // Player is currently attacking, don't auto-summon
        }

        int playerMaxSummons = player.buffManager.getModifier(necesse.entity.mobs.buffs.BuffModifiers.MAX_SUMMONS);

        // Scan the hotbar from right to left (slot 9 to 0) to find the rightmost staff
        for (int i = 9; i >= 0; i--) {
            InventoryItem hotbarItem = player.getInv().main.getItem(i);

            if (hotbarItem != null && hotbarItem.item instanceof SummonToolItem) {
                SummonToolItem staff = (SummonToolItem) hotbarItem.item;
                
                // Check if this staff has a custom summon count limit
                int staffMaxSummons = getStaffMaxSummons(staff, player, hotbarItem, playerMaxSummons);
                
                // Use appropriate counting method based on whether staff has custom max
                int currentSummons;
                boolean staffHasCustomMax = !staff.drawMaxSummons; // drawMaxSummons = false means staff has custom limits
                
                if (staffHasCustomMax) {
                    // Staff has its own max count, use accurate follower method
                    currentSummons = getCurrentSummonCountFollowers(player, staff, hotbarItem);
                } else {
                    // Staff uses player max count, use fast buff bar method
                    currentSummons = getCurrentSummonCountBuffBar(player);
                }
                
                // Get the summon space taken for this staff
                int summonCost = Math.round(staff.getSummonSpaceTaken(hotbarItem, player));
                
                // Only proceed if we have enough remaining capacity for at least one summon
                if (currentSummons + summonCost <= staffMaxSummons) {
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