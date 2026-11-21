package com.sf.audiomanagement.config;

import com.sf.audiomanagement.util.JsonFiles;
import net.minecraft.resources.ResourceLocation;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GlobalConfig {
    public static final String DEFAULT_TARGET = "minecraft:entity.cat";
    public static final String EMPTY_TARGET = "minecraft:empty";

    private static GlobalConfig instance;

    public boolean enabled = false;
    public TargetType targetType = TargetType.GROUP;
    public String target = DEFAULT_TARGET;
    public boolean excludeMusicAndRecords = true;
    public Set<String> blacklist = new LinkedHashSet<>();
    public Set<String> whitelist = new LinkedHashSet<>();

    public enum TargetType {
        GROUP,
        SOUND
    }

    public static GlobalConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static synchronized void load() {
        GlobalConfig loaded = null;
        Path path = ConfigPaths.global();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                loaded = JsonFiles.GSON.fromJson(reader, GlobalConfig.class);
            } catch (Exception ignored) {
            }
        }
        if (loaded == null) {
            loaded = new GlobalConfig();
        }
        loaded.fix();
        instance = loaded;
        save();
    }

    public static void save() {
        GlobalConfig config = instance;
        if (config == null) {
            return;
        }
        Path path = ConfigPaths.global();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                JsonFiles.GSON.toJson(config, writer);
            }
        } catch (Exception ignored) {
        }
    }

    private void fix() {
        if (targetType == null) {
            targetType = TargetType.GROUP;
        }
        if (target == null || target.isBlank()) {
            target = DEFAULT_TARGET;
            targetType = TargetType.GROUP;
        }
        if (blacklist == null) {
            blacklist = new LinkedHashSet<>();
        }
        if (whitelist == null) {
            whitelist = new LinkedHashSet<>();
        }
    }

    public ResourceLocation targetLocation() {
        try {
            return new ResourceLocation(target);
        } catch (Exception ignored) {
            return new ResourceLocation(DEFAULT_TARGET);
        }
    }

    public void setTarget(String id, TargetType type) {
        this.target = id;
        this.targetType = type;
        save();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public void setExcludeMusicAndRecords(boolean exclude) {
        this.excludeMusicAndRecords = exclude;
        save();
    }

    public boolean isManaged(String fullId) {
        if (!whitelist.isEmpty() && !whitelist.contains(fullId)) {
            return false;
        }
        return !blacklist.contains(fullId);
    }

    public boolean isBlacklisted(String fullId) {
        return blacklist.contains(fullId);
    }

    public boolean isWhitelisted(String fullId) {
        return whitelist.contains(fullId);
    }

    public boolean areAllBlacklisted(Collection<String> ids) {
        if (ids.isEmpty()) {
            return false;
        }
        for (String id : ids) {
            if (!blacklist.contains(id)) {
                return false;
            }
        }
        return true;
    }

    public boolean areAllWhitelisted(Collection<String> ids) {
        if (ids.isEmpty()) {
            return false;
        }
        for (String id : ids) {
            if (!whitelist.contains(id)) {
                return false;
            }
        }
        return true;
    }

    public void setBlacklisted(Collection<String> ids, boolean add) {
        if (add) {
            blacklist.addAll(ids);
        } else {
            blacklist.removeAll(ids);
        }
        save();
    }

    public void setWhitelisted(Collection<String> ids, boolean add) {
        if (add) {
            whitelist.addAll(ids);
        } else {
            whitelist.removeAll(ids);
        }
        save();
    }

    public List<String> idsOf(SoundCatalog.ModInfo mod) {
        List<String> ids = new ArrayList<>();
        for (SoundCatalog.SoundInfo sound : mod.allSounds()) {
            ids.add(sound.fullId());
        }
        return ids;
    }

    public List<String> idsOf(SoundCatalog.GroupInfo group) {
        List<String> ids = new ArrayList<>();
        for (SoundCatalog.SoundInfo sound : group.allSounds()) {
            ids.add(sound.fullId());
        }
        return ids;
    }
}
