package com.sf.audiomanagement;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(AudioManagementMod.MODID)
public class AudioManagementMod {
    public static final String MODID = "audio_management";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AudioManagementMod() {
    }
}
