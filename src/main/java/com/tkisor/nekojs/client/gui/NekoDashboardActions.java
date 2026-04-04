package com.tkisor.nekojs.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class NekoDashboardActions {

    public static void locateScript(String scriptPath) {
        Util.getPlatform().openFile(new File(Minecraft.getInstance().gameDirectory, "nekojs/" + scriptPath));
    }

    public static void openLog() {
        Util.getPlatform().openFile(new File(Minecraft.getInstance().gameDirectory, "logs/latest.log"));
    }

    public static void copyToClipboard(String content) {
        Minecraft.getInstance().keyboardHandler.setClipboard(content);
    }

    public static String readScript(String scriptPath) {
        try {
            Path path = new File(Minecraft.getInstance().gameDirectory, "nekojs/" + scriptPath).toPath();
            return Files.exists(path) ? Files.readString(path) : "// 文件不存在: " + path.toString();
        } catch (Exception e) {
            return "// 读取失败: " + e.getMessage();
        }
    }

    public static boolean saveScript(String scriptPath, String content) {
        try {
            Path path = new File(Minecraft.getInstance().gameDirectory, "nekojs/" + scriptPath).toPath();
            Files.writeString(path, content);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}