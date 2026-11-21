package com.sf.audiomanagement.sound;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.TickableSoundInstance;

import java.nio.file.Path;

public final class ManagedTickableSound extends ManagedSound implements TickableSoundInstance {
    private final TickableSoundInstance tickable;

    public ManagedTickableSound(TickableSoundInstance delegate, Sound sound, float gain, Path ogg, boolean replaced) {
        super(delegate, sound, gain, ogg, replaced);
        this.tickable = delegate;
    }

    @Override
    public void tick() {
        tickable.tick();
    }

    @Override
    public boolean isStopped() {
        return tickable.isStopped();
    }
}
