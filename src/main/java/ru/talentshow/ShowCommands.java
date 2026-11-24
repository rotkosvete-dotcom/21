package ru.talentshow;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TalentShowMod.ID)
public class ShowCommands {
    @SubscribeEvent
    public static void reg(RegisterCommandsEvent e) {
        e.getDispatcher().register(literal("talentshow")
            .then(literal("build").requires(s -> s.hasPermission(2)).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                ShowManager.I.origin = p.blockPosition();
                StageBuilder.build(p.serverLevel(), p.blockPosition());
                c.getSource().sendSuccess(() -> Component.literal("Сцена построена (зал на юг). Дальше: /talentshow start"), false);
                return 1;
            }))
            .then(literal("start").requires(s -> s.hasPermission(2)).executes(c -> {
                ShowManager.I.start(c.getSource().getPlayerOrException()); return 1;
            }))
            .then(literal("stop").requires(s -> s.hasPermission(2)).executes(c -> { ShowManager.I.stop(); return 1; }))
            .then(literal("reload").requires(s -> s.hasPermission(2)).executes(c -> {
                Data.load();
                c.getSource().sendSuccess(() -> Component.literal("Загружено ботов: " + Data.BOTS.size()), false);
                return 1;
            }))
            .then(literal("perform").executes(c -> { ShowManager.I.perform(c.getSource().getPlayerOrException()); return 1; }))
            .then(literal("do").then(argument("id", StringArgumentType.word()).executes(c -> {
                ShowManager.I.doTalent(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "id")); return 1;
            })))
            .then(literal("rate").then(argument("n", IntegerArgumentType.integer(0, 11)).executes(c -> {
                ShowManager.I.rate(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n")); return 1;
            }))));
    }
}
