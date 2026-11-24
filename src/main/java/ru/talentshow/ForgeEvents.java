package ru.talentshow;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TalentShowMod.ID)
public class ForgeEvents {
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) ShowManager.I.tick();
    }

    @SubscribeEvent
    public static void starting(ServerStartingEvent e) {
        Data.load();
    }
}
