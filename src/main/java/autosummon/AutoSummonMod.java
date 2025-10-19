package autosummon;

import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.modLoader.ModSettings;

@ModEntry
public class AutoSummonMod {

    public void init() {
        System.out.println("Auto Summon Mod loaded!");
        System.out.println("Automatically summons using the rightmost staff in your hotbar.");
        
        // Initialize settings first
        AutoSummonConfig.initSettings();
        
        // Initialize the control
        AutoSummonConfig.initControl();
    }

    public void initResources() {
        // No resources needed for this mod
    }

    public void postInit() {
        // No additional initialization needed
        System.out.println("Auto Summon Mod initialization complete!");
    }
    
    /**
     * Get mod settings for saving/loading
     * This method is called by the game to persist mod settings
     */
    public ModSettings getModSettings() {
        return AutoSummonConfig.getSettings();
    }
}