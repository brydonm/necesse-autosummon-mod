package autosummon;

import necesse.engine.modLoader.ModSettings;
import necesse.engine.save.SaveData;
import necesse.engine.save.LoadData;

/**
 * Settings class for the auto summon mod that persists the enabled/disabled state.
 * This extends ModSettings to integrate with Necesse's save/load system.
 */
public class AutoSummonModSettings extends ModSettings {
    
    // Default values
    private boolean isEnabled = true;
    
    // Global custom summon limit for all staffs that have their own counters
    // If set to 0 or negative, uses the staff's default limit
    private int globalCustomSummonLimit = 1;
    
    public AutoSummonModSettings() {
        super();
    }
    
    @Override
    public void addSaveData(SaveData saveData) {
        saveData.addBoolean("autosummon_enabled", isEnabled);
        saveData.addInt("global_custom_summon_limit", globalCustomSummonLimit);
    }
    
    @Override
    public void applyLoadData(LoadData loadData) {
        isEnabled = loadData.getBoolean("autosummon_enabled", true); // Default to true if not found
        globalCustomSummonLimit = loadData.getInt("global_custom_summon_limit", 1); // Default to 1
    }
    
    /**
     * Check if auto summon is enabled
     */
    public boolean isEnabled() {
        return isEnabled;
    }
    
    /**
     * Set whether auto summon is enabled
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }
    
    /**
     * Set global custom summon limit for all staffs with their own counters
     * @param limit The limit to set (0 or negative to use staff defaults)
     */
    public void setGlobalCustomSummonLimit(int limit) {
        this.globalCustomSummonLimit = limit;
    }
    
    /**
     * Get global custom summon limit
     * @return The global limit (0 means use staff defaults)
     */
    public int getGlobalCustomSummonLimit() {
        return globalCustomSummonLimit;
    }
    
    /**
     * Check if global custom limit is active
     * @return True if global limit is set and positive
     */
    public boolean hasGlobalCustomLimit() {
        return globalCustomSummonLimit > 0;
    }
}
