package com.sf.audiomanagement.sound;

import com.sf.audiomanagement.AudioManagement;
import com.sf.audiomanagement.config.ConfigPaths;
import com.sf.audiomanagement.config.GlobalConfig;
import com.sf.audiomanagement.config.ReplacementStore;
import com.sf.audiomanagement.config.SoundCatalog;
import com.sf.audiomanagement.config.VolumeStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.SoundEvent.SoundSourceEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SoundReplacer {
    private SoundReplacer() {
    }

    public static void play(PlaySoundEvent event) {
        if (!AudioManagement.isReady()) {
            return;
        }
        SoundInstance original = event.getSound();
        if (original == null || original instanceof ManagedSound) {
            return;
        }
        ResourceLocation id = original.getLocation();
        GlobalConfig config = GlobalConfig.get();
        if (!config.isManaged(id.toString())) {
            return;
        }

        float gain = VolumeStore.get().gain(id);
        Replacement replacement = findReplacement(original, id, config);
        if (replacement == null && Math.abs(gain - 1.0F) < 0.0001F) {
            return;
        }

        Sound sound = replacement == null ? null : replacement.sound();
        boolean replaced = replacement != null;
        Path ogg = replacement == null ? null : replacement.ogg();
        ManagedSound managed = original instanceof TickableSoundInstance tickable
                ? new ManagedTickableSound(tickable, sound, gain, ogg, replaced)
                : new ManagedSound(original, sound, gain, ogg, replaced);
        event.setSound(managed);
    }

    private static Replacement findReplacement(SoundInstance original, ResourceLocation id, GlobalConfig config) {
        if (!config.enabled) {
            return null;
        }
        String namespace = id.getNamespace();
        String group = ConfigPaths.groupOf(id);
        ReplacementStore store = ReplacementStore.get();

        Path soundOgg = store.soundOgg(id);
        if (soundOgg != null) {
            return new Replacement(customSound(id, soundOgg), soundOgg);
        }
        while (!group.isEmpty()) {
            Path groupOgg = store.groupOgg(namespace, group);
            if (groupOgg != null) {
                return new Replacement(customSound(id, groupOgg), groupOgg);
            }
            int dot = group.lastIndexOf('.');
            if (dot < 0) {
                break;
            }
            group = group.substring(0, dot);
        }
        if (config.excludeMusicAndRecords
                && (original.getSource() == SoundSource.MUSIC || original.getSource() == SoundSource.RECORDS)) {
            return null;
        }

        Sound target = resolveTarget(config);
        return target == null ? null : new Replacement(target, null);
    }

    private static Sound resolveTarget(GlobalConfig config) {
        ResourceLocation target = config.targetLocation();
        RandomSource random = SoundInstance.createUnseededRandom();
        if (config.targetType == GlobalConfig.TargetType.SOUND) {
            return resolve(target, random);
        }
        List<SoundCatalog.SoundInfo> sounds = SoundCatalog.get().soundsInGroup(target.getNamespace(), target.getPath());
        for (int attempt = 0; attempt < 8 && !sounds.isEmpty(); attempt++) {
            SoundCatalog.SoundInfo info = sounds.get(random.nextInt(sounds.size()));
            Sound sound = resolve(info.id(), random);
            if (sound != null) {
                return sound;
            }
        }
        return null;
    }

    private static Sound resolve(ResourceLocation id, RandomSource random) {
        WeighedSoundEvents events = Minecraft.getInstance().getSoundManager().getSoundEvent(id);
        if (events == null) {
            return null;
        }
        Sound sound = events.getSound(random);
        return sound == null || sound == SoundManager.EMPTY_SOUND ? null : sound;
    }

    private static Sound customSound(ResourceLocation id, Path path) {
        long modified = 0L;
        try {
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
        }
        String location = "audio_management:custom/" + id.getNamespace() + "/"
                + id.getPath().replace('.', '/') + "/" + modified;
        return new Sound(location, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1,
                Sound.Type.FILE, true, false, 16);
    }

    public static void boost(SoundSourceEvent event) {
        if (!(event.getSound() instanceof ManagedSound managed)) {
            return;
        }
        float volume = managed.getRawVolume();
        if (volume <= 1.0F) {
            return;
        }
        SoundSource source = managed.getSource();
        float category = source == SoundSource.MASTER ? 1.0F
                : Minecraft.getInstance().options.getSoundSourceVolume(source);
        event.getChannel().setVolume(Math.min(volume, 2.0F) * category);
    }

    private record Replacement(Sound sound, Path ogg) {
    }
}
