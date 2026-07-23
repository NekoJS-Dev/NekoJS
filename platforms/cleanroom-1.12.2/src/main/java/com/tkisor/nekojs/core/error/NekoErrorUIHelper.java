package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.NekoJSMod;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * 1.12.2 NekoErrorUIHelper - formats script errors for display.
 * Uses ITextComponent/TextComponentString instead of 1.21.1's Component system.
 */
public final class NekoErrorUIHelper {
    private NekoErrorUIHelper() {}

    public static ITextComponent getErrorComponent() {
        int count = NekoJSMod.RUNTIME_ROOT.errors().count();
        if (count == 0) {
            return new TextComponentString("No NekoJS script errors.");
        }
        String msg = String.format("There are %d NekoJS script error(s). Use /nekojs error for details.", count);
        ITextComponent component = new TextComponentString(msg);
        component.getStyle().setColor(TextFormatting.RED);
        return component;
    }

    public static String getErrorSummaryText() {
        int count = NekoJSMod.RUNTIME_ROOT.errors().count();
        if (count == 0) return "No errors";
        return String.format("%d error(s)", count);
    }
}
