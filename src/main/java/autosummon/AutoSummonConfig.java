package autosummon;

/**
 * Configuration class for the auto summon mod.
 * Modify these values to adjust the summoning behavior.
 */
public class AutoSummonConfig {
    
    // Main configuration values
    public static final long SUMMON_CHECK_COOLDOWN = 250; // ms. How often to check for summoning (4 times/sec)
    
    // Runtime toggle state - now managed by persistent settings
    private static AutoSummonModSettings settings;
    private static boolean needsChatMessage = false;
    private static String chatMessage = "";
    
    // Control for toggling auto-summon
    public static necesse.engine.input.Control TOGGLE_AUTO_SUMMON;
    public static necesse.engine.input.Control SETTINGS_CONTROL;
    
    /**
     * Initialize settings
     */
    public static void initSettings() {
        settings = new AutoSummonModSettings();
    }
    
    /**
     * Get the settings instance
     */
    public static AutoSummonModSettings getSettings() {
        return settings;
    }
    
    /**
     * Check if the mod is enabled
     */
    public static boolean isEnabled() {
        return settings != null ? settings.isEnabled() : true;
    }
    
    /**
     * Toggle the mod on/off
     */
    public static void toggle() {
        if (settings != null) {
            settings.setEnabled(!settings.isEnabled());
        }
    }
    
    /**
     * Set flag that chat message is needed
     */
    public static void setNeedsChatMessage(boolean needs) {
        needsChatMessage = needs;
    }
    
    /**
     * Set flag that chat message is needed with a specific message
     */
    public static void setNeedsChatMessage(boolean needs, String message) {
        needsChatMessage = needs;
        chatMessage = message;
    }
    
    /**
     * Set chat message
     */
    public static void setChatMessage(String message) {
        chatMessage = message;
    }
    
    /**
     * Check if chat message is needed and clear the flag
     */
    public static boolean checkAndClearNeedsChatMessage() {
        if (needsChatMessage) {
            needsChatMessage = false;
            return true;
        }
        return false;
    }
    
    /**
     * Get the current chat message and clear it
     */
    public static String getAndClearChatMessage() {
        String message = chatMessage;
        chatMessage = "";
        return message;
    }
    
    /**
     * Initialize the control
     */
    public static void initControl() {
        TOGGLE_AUTO_SUMMON = necesse.engine.input.Control.addModControl(
            new AutoSummonControl() // F9 key
        );
        SETTINGS_CONTROL = necesse.engine.input.Control.addModControl(
            new AutoSummonSettingsControl() // F10 key
        );
    }
    
    /**
     * Get the summon check cooldown
     */
    public static long getSummonCheckCooldown() {
        return SUMMON_CHECK_COOLDOWN;
    }
}
