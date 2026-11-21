package com.sf.audiomanagement.config;

import com.sf.audiomanagement.AudioManagementMod;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class ReplacementStore {
    private static final ReplacementStore INSTANCE = new ReplacementStore();

    private Map<String, Path> groupOggs = Map.of();
    private Map<String, Path> soundOggs = Map.of();
    private long lastRefresh;
    private int revision;

    private ReplacementStore() {
    }

    public static ReplacementStore get() {
        return INSTANCE;
    }

    public synchronized void tick() {
        if (System.currentTimeMillis() - lastRefresh > 1000L) {
            refresh();
        }
    }

    public synchronized void refresh() {
        Map<String, Path> groups = new HashMap<>();
        Map<String, Path> sounds = new HashMap<>();
        Path root = ConfigPaths.root();
        if (Files.isDirectory(root)) {
            try (DirectoryStream<Path> modDirectories = Files.newDirectoryStream(root)) {
                for (Path modDirectory : modDirectories) {
                    if (!Files.isDirectory(modDirectory)) {
                        continue;
                    }
                    String namespace = modDirectory.getFileName().toString();
                    try (DirectoryStream<Path> entries = Files.newDirectoryStream(modDirectory)) {
                        for (Path entry : entries) {
                            String fileName = entry.getFileName().toString();
                            if (Files.isDirectory(entry)) {
                                try (DirectoryStream<Path> files = Files.newDirectoryStream(entry)) {
                                    for (Path file : files) {
                                        if (!isOgg(file)) {
                                            continue;
                                        }
                                        String soundName = stripExtension(file.getFileName().toString());
                                        sounds.put(soundKey(namespace, fileName, soundName), file);
                                    }
                                }
                            } else if (isOgg(entry)) {
                                groups.put(namespace + ":" + stripExtension(fileName), entry);
                            }
                        }
                    }
                }
            } catch (Exception exception) {
                AudioManagementMod.LOGGER.warn("Failed to scan replacement sounds", exception);
            }
        }
        Map<String, Path> newGroups = Map.copyOf(groups);
        Map<String, Path> newSounds = Map.copyOf(sounds);
        boolean changed = !this.groupOggs.equals(newGroups) || !this.soundOggs.equals(newSounds);
        this.groupOggs = newGroups;
        this.soundOggs = newSounds;
        if (changed) {
            this.revision++;
        }
        this.lastRefresh = System.currentTimeMillis();
    }

    public synchronized int revision() {
        return this.revision;
    }

    public synchronized Path groupOgg(String namespace, String group) {
        return groupOggs.get(namespace + ":" + group);
    }

    public synchronized Path soundOgg(ResourceLocation id) {
        return soundOggs.get(id.toString());
    }

    public synchronized boolean hasGroupOgg(String namespace, String group) {
        return groupOggs.containsKey(namespace + ":" + group);
    }

    public synchronized boolean hasSoundOgg(ResourceLocation id) {
        return soundOggs.containsKey(id.toString());
    }

    public static boolean isOgg(Path path) {
        if (!Files.isRegularFile(path) || !path.getFileName().toString().toLowerCase().endsWith(".ogg")) {
            return false;
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] header = input.readNBytes(4);
            return header.length == 4 && header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S';
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String soundKey(String namespace, String group, String soundName) {
        SoundCatalog catalog = SoundCatalog.get();
        if ("misc".equals(group)) {
            ResourceLocation plain = new ResourceLocation(namespace, soundName);
            if (catalog.sound(plain) != null) {
                return plain.toString();
            }
        }
        return namespace + ":" + group + "." + soundName;
    }
}
