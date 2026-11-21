package com.sf.audiomanagement.config;

import com.sf.audiomanagement.AudioManagementMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ConfigPaths {
    private ConfigPaths() {
    }

    public static Path root() {
        return FMLPaths.CONFIGDIR.get().resolve(AudioManagementMod.MODID);
    }

    public static Path global() {
        return FMLPaths.CONFIGDIR.get().resolve(AudioManagementMod.MODID + ".json");
    }

    public static Path modVolume(String namespace) {
        return root().resolve(namespace).resolve("mod.json");
    }

    public static Path groupVolume(String namespace, String group) {
        return root().resolve(namespace).resolve(group + ".json");
    }

    public static Path soundVolume(String namespace, String group, String name) {
        return root().resolve(namespace).resolve(group).resolve(name + ".json");
    }

    public static Path groupOgg(String namespace, String group) {
        return root().resolve(namespace).resolve(group + ".ogg");
    }

    public static Path soundOgg(String namespace, String group, String name) {
        return root().resolve(namespace).resolve(group).resolve(name + ".ogg");
    }

    public static String groupOf(ResourceLocation id) {
        return groupOfPath(id.getPath());
    }

    public static String nameOf(ResourceLocation id) {
        return nameOfPath(id.getPath());
    }

    public static String groupOfPath(String path) {
        int dot = path.lastIndexOf('.');
        return dot <= 0 ? "misc" : path.substring(0, dot);
    }

    public static String nameOfPath(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
