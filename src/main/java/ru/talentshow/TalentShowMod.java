package ru.talentshow;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(TalentShowMod.ID)
public class TalentShowMod {
    public static final String ID = "talentshow";
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    public static final RegistryObject<EntityType<BotEntity>> BOT = ENTITIES.register("bot",
            () -> EntityType.Builder.of(BotEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).clientTrackingRange(10).build("bot"));

    public TalentShowMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        bus.addListener(this::attrs);
    }

    private void attrs(EntityAttributeCreationEvent e) {
        e.put(BOT.get(), BotEntity.attrs().build());
    }
}
