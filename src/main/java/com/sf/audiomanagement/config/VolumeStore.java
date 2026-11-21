package com.sf.audiomanagement.config;

import com.sf.audiomanagement.AudioManagementMod;
import com.sf.audiomanagement.util.JsonFiles;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class VolumeStore {
    private static final VolumeStore INSTANCE = new VolumeStore();

    private final Map<String, Integer> modVolumes = new HashMap<>();
    private final Map<String, Integer> groupVolumes = new HashMap<>();
    private final Map<String, Integer> soundVolumes = new HashMap<>();
    private final Map<Path, Integer> pendingWrites = new HashMap<>();
    private final java.util.Set<Path> pendingDeletes = new java.util.HashSet<>();

    private boolean loaded;
    private boolean dirty;

    private VolumeStore() {
    }

    public static VolumeStore get() {
        return INSTANCE;
    }

    public synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (SoundCatalog.ModInfo mod : SoundCatalog.get().mods()) {
            String namespace = mod.namespace();
            int modVolume = JsonFiles.readVolume(ConfigPaths.modVolume(namespace));
            if (modVolume != 100) {
                modVolumes.put(namespace, clamp(modVolume));
            }
            for (SoundCatalog.GroupInfo group : mod.allGroups()) {
                int groupVolume = JsonFiles.readVolume(ConfigPaths.groupVolume(namespace, group.path()));
                if (groupVolume != 100) {
                    groupVolumes.put(groupKey(namespace, group.path()), clamp(groupVolume));
                }
                for (SoundCatalog.SoundInfo sound : group.sounds()) {
                    int soundVolume = JsonFiles.readVolume(
                            ConfigPaths.soundVolume(namespace, group.path(), sound.name()));
                    if (soundVolume != 100) {
                        soundVolumes.put(sound.fullId(), clamp(soundVolume));
                    }
                }
            }
        }
    }

    public int mod(String namespace) {
        return modVolumes.getOrDefault(namespace, 100);
    }

    public int group(String namespace, String group) {
        return groupVolumes.getOrDefault(groupKey(namespace, group), 100);
    }

    public int sound(ResourceLocation id) {
        return soundVolumes.getOrDefault(id.toString(), 100);
    }

    public float gain(ResourceLocation id) {
        String namespace = id.getNamespace();
        String group = ConfigPaths.groupOf(id);
        float gain = mod(namespace) / 100.0F;
        while (!group.isEmpty()) {
            gain *= group(namespace, group) / 100.0F;
            int dot = group.lastIndexOf('.');
            if (dot < 0) {
                break;
            }
            group = group.substring(0, dot);
        }
        gain *= sound(id) / 100.0F;
        return Math.max(0.0F, Math.min(2.0F, gain));
    }

    public synchronized void setMod(String namespace, int volume) {
        volume = clamp(volume);
        if (volume == 100) {
            modVolumes.remove(namespace);
        } else {
            modVolumes.put(namespace, volume);
        }
        queue(ConfigPaths.modVolume(namespace), volume);
    }

    public synchronized void setGroup(String namespace, String group, int volume) {
        volume = clamp(volume);
        String key = groupKey(namespace, group);
        if (volume == 100) {
            groupVolumes.remove(key);
        } else {
            groupVolumes.put(key, volume);
        }
        queue(ConfigPaths.groupVolume(namespace, group), volume);
    }

    public synchronized void setSound(ResourceLocation id, int volume) {
        volume = clamp(volume);
        String key = id.toString();
        if (volume == 100) {
            soundVolumes.remove(key);
        } else {
            soundVolumes.put(key, volume);
        }
        queue(ConfigPaths.soundVolume(id.getNamespace(), ConfigPaths.groupOf(id), ConfigPaths.nameOf(id)), volume);
    }

    private void queue(Path path, int volume) {
        dirty = true;
        pendingDeletes.remove(path);
        if (volume == 100) {
            pendingWrites.remove(path);
            pendingDeletes.add(path);
        } else {
            pendingWrites.put(path, volume);
        }
    }

    public synchronized void flushIfDirty() {
        if (!dirty) {
            return;
        }
        for (Map.Entry<Path, Integer> entry : pendingWrites.entrySet()) {
            try {
                JsonFiles.writeVolume(entry.getKey(), entry.getValue());
            } catch (Exception exception) {
                AudioManagementMod.LOGGER.warn("Failed to write {}", entry.getKey(), exception);
            }
        }
        for (Path path : pendingDeletes) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception exception) {
                AudioManagementMod.LOGGER.warn("Failed to delete {}", path, exception);
            }
        }
        pendingWrites.clear();
        pendingDeletes.clear();
        dirty = false;
    }

    private static String groupKey(String namespace, String group) {
        return namespace + ":" + group;
    }

    private static int clamp(int volume) {
        return Math.max(0, Math.min(200, volume));
    }
}
