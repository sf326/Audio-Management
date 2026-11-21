package com.sf.audiomanagement.config;

import com.sf.audiomanagement.AudioManagementMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SoundCatalog {
    private static SoundCatalog instance;

    private final List<ModInfo> mods;
    private final Map<String, ModInfo> modsByNamespace;
    private final Map<String, GroupInfo> groupsByKey;
    private final Map<String, SoundInfo> soundsById;
    private final int soundCount;

    private SoundCatalog(List<ModInfo> mods, Map<String, ModInfo> modsByNamespace,
                         Map<String, GroupInfo> groupsByKey, Map<String, SoundInfo> soundsById) {
        this.mods = Collections.unmodifiableList(mods);
        this.modsByNamespace = Collections.unmodifiableMap(modsByNamespace);
        this.groupsByKey = Collections.unmodifiableMap(groupsByKey);
        this.soundsById = Collections.unmodifiableMap(soundsById);
        int count = 0;
        for (ModInfo mod : mods) {
            count += mod.soundCount();
        }
        this.soundCount = count;
    }

    public static SoundCatalog get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static synchronized void load() {
        if (instance != null) {
            return;
        }
        Map<String, List<SoundInfo>> byNamespace = new LinkedHashMap<>();
        for (ResourceLocation id : ForgeRegistries.SOUND_EVENTS.getKeys()) {
            if (id.getNamespace().equals("minecraft")
                    && (id.getPath().equals("empty") || id.getPath().equals("intentionally_empty"))) {
                continue;
            }
            String group = ConfigPaths.groupOf(id);
            String name = ConfigPaths.nameOf(id);
            SoundInfo info = new SoundInfo(id, id.getNamespace(), group, name);
            byNamespace.computeIfAbsent(id.getNamespace(), key -> new ArrayList<>()).add(info);
        }

        List<ModInfo> mods = new ArrayList<>();
        Map<String, ModInfo> modsByNamespace = new LinkedHashMap<>();
        Map<String, GroupInfo> groupsByKey = new LinkedHashMap<>();
        Map<String, SoundInfo> soundsById = new LinkedHashMap<>();

        for (Map.Entry<String, List<SoundInfo>> entry : byNamespace.entrySet()) {
            String namespace = entry.getKey();
            List<SoundInfo> sounds = entry.getValue();
            sounds.sort(Comparator.comparing((SoundInfo info) -> info.id().getPath()));

            Map<String, GroupInfo> groups = new LinkedHashMap<>();
            for (SoundInfo sound : sounds) {
                GroupInfo group = ensureGroup(groups, namespace, sound.group());
                group.sounds.add(sound);
            }

            List<GroupInfo> top = new ArrayList<>();
            for (GroupInfo group : groups.values()) {
                if (group.depth == 0) {
                    top.add(group);
                }
            }
            top.sort(Comparator.comparing(GroupInfo::path));
            List<GroupInfo> ordered = new ArrayList<>(groups.values());
            ordered.sort(Comparator.comparingInt(GroupInfo::depth).reversed());
            for (GroupInfo group : ordered) {
                group.freeze();
            }
            for (GroupInfo group : groups.values()) {
                groupsByKey.put(groupKey(namespace, group.path()), group);
            }
            for (SoundInfo sound : sounds) {
                soundsById.put(sound.id().toString(), sound);
            }
            ModInfo mod = new ModInfo(namespace, displayName(namespace), top);
            mods.add(mod);
            modsByNamespace.put(namespace, mod);
        }

        mods.sort(Comparator
                .comparing((ModInfo mod) -> !mod.namespace().equals("minecraft"))
                .thenComparing((ModInfo mod) -> mod.displayName().toLowerCase(Locale.ROOT))
                .thenComparing(ModInfo::namespace));

        instance = new SoundCatalog(mods, modsByNamespace, groupsByKey, soundsById);
        AudioManagementMod.LOGGER.info("Indexed {} namespaces and {} sound events", mods.size(), instance.soundCount);
    }

    private static GroupInfo ensureGroup(Map<String, GroupInfo> groups, String namespace, String groupPath) {
        String current = "";
        GroupInfo parent = null;
        int depth = 0;
        for (String part : groupPath.split("\\.")) {
            if (part.isEmpty()) {
                continue;
            }
            current = current.isEmpty() ? part : current + "." + part;
            GroupInfo group = groups.get(current);
            if (group == null) {
                group = new GroupInfo(namespace, current, part, depth);
                groups.put(current, group);
                if (parent != null) {
                    parent.children.add(group);
                }
            }
            parent = group;
            depth++;
        }
        if (parent == null) {
            GroupInfo misc = groups.get("misc");
            if (misc == null) {
                misc = new GroupInfo(namespace, "misc", "misc", 0);
                groups.put("misc", misc);
            }
            parent = misc;
        }
        return parent;
    }

    private static String displayName(String namespace) {
        if (namespace.equals("minecraft")) {
            return "Minecraft";
        }
        ModList modList = ModList.get();
        if (modList != null) {
            return modList.getModContainerById(namespace)
                    .map(container -> container.getModInfo().getDisplayName())
                    .orElse(namespace);
        }
        return namespace;
    }

    public static String groupKey(String namespace, String group) {
        return namespace + ":" + group;
    }

    public List<ModInfo> mods() {
        return mods;
    }

    public ModInfo mod(String namespace) {
        return modsByNamespace.get(namespace);
    }

    public GroupInfo group(String namespace, String group) {
        return groupsByKey.get(groupKey(namespace, group));
    }

    public SoundInfo sound(ResourceLocation id) {
        return soundsById.get(id.toString());
    }

    public List<SoundInfo> soundsInGroup(String namespace, String group) {
        GroupInfo info = groupsByKey.get(groupKey(namespace, group));
        return info == null ? Collections.emptyList() : info.allSounds();
    }

    public int soundCount() {
        return soundCount;
    }

    public record SoundInfo(ResourceLocation id, String namespace, String group, String name) {
        public String fullId() {
            return id.toString();
        }
    }

    public static final class GroupInfo {
        private final String namespace;
        private final String path;
        private final String name;
        private final int depth;
        private final List<GroupInfo> children = new ArrayList<>();
        private final List<SoundInfo> sounds = new ArrayList<>();
        private List<SoundInfo> allSounds = List.of();

        private GroupInfo(String namespace, String path, String name, int depth) {
            this.namespace = namespace;
            this.path = path;
            this.name = name;
            this.depth = depth;
        }

        private void freeze() {
            children.sort(Comparator.comparing(GroupInfo::name));
            sounds.sort(Comparator.comparing(SoundInfo::name));
            List<SoundInfo> collected = new ArrayList<>();
            for (GroupInfo child : children) {
                collected.addAll(child.allSounds);
            }
            collected.addAll(sounds);
            this.allSounds = Collections.unmodifiableList(collected);
        }

        public String namespace() {
            return namespace;
        }

        public String path() {
            return path;
        }

        public String name() {
            return name;
        }

        public int depth() {
            return depth;
        }

        public List<GroupInfo> children() {
            return Collections.unmodifiableList(children);
        }

        public List<SoundInfo> sounds() {
            return Collections.unmodifiableList(sounds);
        }

        public List<SoundInfo> allSounds() {
            return allSounds;
        }

        public int soundCount() {
            return allSounds.size();
        }

        public boolean hasChildren() {
            return !children.isEmpty();
        }

        public boolean hasEntries() {
            return !children.isEmpty() || !sounds.isEmpty();
        }
    }

    public static final class ModInfo {
        private final String namespace;
        private final String displayName;
        private final List<GroupInfo> groups;
        private final List<GroupInfo> allGroups;
        private final List<SoundInfo> allSounds;
        private final int soundCount;

        private ModInfo(String namespace, String displayName, List<GroupInfo> groups) {
            this.namespace = namespace;
            this.displayName = displayName;
            this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
            List<GroupInfo> flattened = new ArrayList<>();
            List<SoundInfo> sounds = new ArrayList<>();
            for (GroupInfo group : groups) {
                collect(group, flattened, sounds);
            }
            this.allGroups = Collections.unmodifiableList(flattened);
            this.allSounds = Collections.unmodifiableList(sounds);
            this.soundCount = sounds.size();
        }

        private static void collect(GroupInfo group, List<GroupInfo> groups, List<SoundInfo> sounds) {
            groups.add(group);
            sounds.addAll(group.sounds());
            for (GroupInfo child : group.children()) {
                collect(child, groups, sounds);
            }
        }

        public String namespace() {
            return namespace;
        }

        public String displayName() {
            return displayName;
        }

        public List<GroupInfo> groups() {
            return groups;
        }

        public List<GroupInfo> allGroups() {
            return allGroups;
        }

        public List<SoundInfo> allSounds() {
            return allSounds;
        }

        public int soundCount() {
            return soundCount;
        }
    }
}
