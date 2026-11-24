package ru.talentshow;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

public class BotRenderer extends MobRenderer<BotEntity, PlayerModel<BotEntity>> {
    private static final Map<String, GameProfile> PROFILES = new ConcurrentHashMap<>();

    public BotRenderer(EntityRendererProvider.Context c) {
        super(c, new PlayerModel<>(c.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(BotEntity e) {
        String n = e.getSkin();
        GameProfile p = PROFILES.get(n);
        if (p == null) {
            GameProfile base = new GameProfile(null, n);
            PROFILES.put(n, base);
            SkullBlockEntity.updateGameprofile(base, filled -> PROFILES.put(n, filled)); // грузит скин по нику
            p = base;
        }
        return Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(p);
    }
}
