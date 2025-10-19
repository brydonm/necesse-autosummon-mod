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
    
    public AutoSummonModSettings() {
        super();
    }
    
    @Override
    public void addSaveData(SaveData saveData) {
        saveData.addBoolean("autosummon_enabled", isEnabled);
    }
    
    @Override
    public void applyLoadData(LoadData loadData) {
        isEnabled = loadData.getBoolean("autosummon_enabled", true); // Default to true if not found
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
}
