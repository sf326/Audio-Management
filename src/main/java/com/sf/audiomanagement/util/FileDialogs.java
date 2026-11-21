package com.sf.audiomanagement.util;

import com.sf.audiomanagement.gui.OggFilePickerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Path;
import java.util.function.Consumer;

public final class FileDialogs {
    private FileDialogs() {
    }

    public static void pickOgg(Screen parent, Consumer<Path> consumer) {
        Minecraft.getInstance().setScreen(new OggFilePickerScreen(parent, consumer));
    }
}
