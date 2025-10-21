package autosummon;

import necesse.engine.input.Control;
import necesse.engine.input.InputEvent;
import necesse.engine.localization.message.StaticMessage;

/**
 * Control for opening the auto summon settings menu
 */
public class AutoSummonSettingsControl extends Control {
    
    public AutoSummonSettingsControl() {
        super(necesse.engine.input.InputID.KEY_F10, "autosummonsettings", new StaticMessage("Auto Summon Settings"));
    }
    
    @Override
    public void activate(InputEvent event) {
        // Only handle key press events (not releases)
        if (event.state) {
            // Cycle through summon limit values: 1, 2, 3, 4, 5, then staff defaults (0)
            AutoSummonModSettings settings = AutoSummonConfig.getSettings();
            int currentLimit = settings.getGlobalCustomSummonLimit();
            
            int nextLimit;
            String message;
            
            switch (currentLimit) {
                case 0: // Staff defaults -> 1
                    nextLimit = 1;
                    message = "Set global summon limit to 1 (applies to all custom staffs)";
                    break;
                case 1: // 1 -> 2
                    nextLimit = 2;
                    message = "Set global summon limit to 2 (applies to all custom staffs)";
                    break;
                case 2: // 2 -> 3
                    nextLimit = 3;
                    message = "Set global summon limit to 3 (applies to all custom staffs)";
                    break;
                case 3: // 3 -> 4
                    nextLimit = 4;
                    message = "Set global summon limit to 4 (applies to all custom staffs)";
                    break;
                case 4: // 4 -> 5
                    nextLimit = 5;
                    message = "Set global summon limit to 5 (applies to all custom staffs)";
                    break;
                case 5: // 5 -> Staff defaults
                    nextLimit = 0;
                    message = "Reset to use staff defaults";
                    break;
                default: // Any other value -> 1
                    nextLimit = 1;
                    message = "Set global summon limit to 1 (applies to all custom staffs)";
                    break;
            }
            
            settings.setGlobalCustomSummonLimit(nextLimit);
            AutoSummonConfig.setNeedsChatMessage(true, message);
        }
        
        // Call the parent activate method to handle the normal control behavior
        super.activate(event);
    }
}
