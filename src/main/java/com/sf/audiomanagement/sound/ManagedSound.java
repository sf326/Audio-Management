package com.sf.audiomanagement.sound;

import com.mojang.blaze3d.audio.OggAudioStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class ManagedSound implements SoundInstance {
    protected final SoundInstance delegate;
    private final Sound sound;
    private final float gain;
    private final Path ogg;
    private final boolean replaced;
    private float baseVolume;
    private WeighedSoundEvents resolved;

    public ManagedSound(SoundInstance delegate, Sound sound, float gain, Path ogg, boolean replaced) {
        this.delegate = delegate;
        this.sound = sound;
        this.gain = gain;
        this.ogg = ogg;
        this.replaced = replaced;
        this.baseVolume = safeVolume(delegate, 1.0F);
    }

    public float getRawVolume() {
        return baseVolume * gain;
    }

    @Override
    public ResourceLocation getLocation() {
        return delegate.getLocation();
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager soundManager) {
        WeighedSoundEvents delegated = delegate.resolve(soundManager);
        this.baseVolume = safeVolume(delegate, this.baseVolume);
        if (replaced) {
            if (resolved == null) {
                resolved = new WeighedSoundEvents(delegate.getLocation(), null);
                resolved.addSound(sound);
            }
            return resolved;
        }
        return delegated;
    }

    @Override
    public Sound getSound() {
        if (replaced) {
            return sound;
        }
        Sound current = delegate.getSound();
        if (current == null || current == SoundManager.EMPTY_SOUND) {
            WeighedSoundEvents events = delegate.resolve(Minecraft.getInstance().getSoundManager());
            if (events != null) {
                Sound candidate = events.getSound(SoundInstance.createUnseededRandom());
                if (candidate != null && candidate != SoundManager.EMPTY_SOUND) {
                    return candidate;
                }
            }
        }
        return current;
    }

    @Override
    public SoundSource getSource() {
        return delegate.getSource();
    }

    @Override
    public boolean isLooping() {
        return delegate.isLooping();
    }

    @Override
    public boolean isRelative() {
        return delegate.isRelative();
    }

    @Override
    public int getDelay() {
        return delegate.getDelay();
    }

    @Override
    public float getVolume() {
        return baseVolume * gain;
    }

    @Override
    public float getPitch() {
        return delegate.getPitch();
    }

    @Override
    public double getX() {
        return delegate.getX();
    }

    @Override
    public double getY() {
        return delegate.getY();
    }

    @Override
    public double getZ() {
        return delegate.getZ();
    }

    @Override
    public SoundInstance.Attenuation getAttenuation() {
        return delegate.getAttenuation();
    }

    @Override
    public boolean canStartSilent() {
        return delegate.canStartSilent();
    }

    @Override
    public boolean canPlaySound() {
        return delegate.canPlaySound();
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        if (ogg != null) {
            try {
                if (looping) {
                    InputStream input = Files.newInputStream(ogg);
                    return CompletableFuture.<AudioStream>completedFuture(
                            new LoopingAudioStream(OggAudioStream::new, input));
                }
                return CompletableFuture.<AudioStream>completedFuture(
                        new OggAudioStream(Files.newInputStream(ogg)));
            } catch (IOException exception) {
                return CompletableFuture.<AudioStream>failedFuture(exception);
            }
        }
        Sound resolved = sound == null || sound == SoundManager.EMPTY_SOUND ? getSound() : sound;
        return delegate.getStream(soundBuffers, resolved, looping);
    }

    @Override
    public String toString() {
        return "ManagedSound[" + delegate.getLocation() + "]";
    }

    private static float safeVolume(SoundInstance instance, float fallback) {
        try {
            return instance.getVolume();
        } catch (Exception exception) {
            return fallback;
        }
    }
}
