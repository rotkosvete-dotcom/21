package ru.talentshow;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** Ведёт шоу: очередь номеров, действия по тикам, оценки судей-ботов и игрока. */
public class ShowManager {
    public static final ShowManager I = new ShowManager();
    static final Random R = new Random();
    static final String SYS = "Ты участник шоу талантов в Майнкрафте. Пиши коротко, по-русски, смешно, без мата и злых оскорблений.";

    public record Performer(String name, BotEntity bot, ServerPlayer player, Data.BotDef def) {}
    record Task(int wait, Runnable r) {}
    record Chast(String target, CompletableFuture<String> ai) {}
    interface Fx { boolean tick(); }
    enum State { IDLE, RUN, PICK, RATE }

    public BlockPos origin;
    public boolean running;
    MinecraftServer server; ServerLevel level; ServerPlayer player;
    State state = State.IDLE;
    int cooldown, waitTicks, judgeSum;
    boolean buzzerUsed;
    Vec3 playerHome;
    String guestName = "";
    Performer current;
    List<Data.Talent> pickList = new ArrayList<>();
    final Map<String, BotEntity> bots = new LinkedHashMap<>();
    final Map<String, Vec3> seats = new HashMap<>();
    final Deque<Task> tasks = new ArrayDeque<>();
    final List<Fx> effects = new ArrayList<>();
    final List<Performer> order = new ArrayList<>();
    final Map<String, Integer> scores = new LinkedHashMap<>();
    final List<Entity> spawned = new ArrayList<>();
    final List<Entity> guests = new ArrayList<>();
    final Map<Entity, Vec3> guestHome = new HashMap<>();
    final Map<Data.Action, Chast> chasts = new IdentityHashMap<>();

    // ================= управление =================
    public void start(ServerPlayer p) {
        stop();
        player = p; level = p.serverLevel(); server = level.getServer();
        if (origin == null) origin = p.blockPosition();
        if (Data.BOTS.isEmpty()) { msg(p, "Нет ботов в config/talentshow/bots"); return; }
        List<Data.BotDef> defs = new ArrayList<>(Data.BOTS.values());
        Collections.shuffle(defs);
        List<BlockPos> sb = StageBuilder.seatBlocks(origin);
        for (int i = 0; i < defs.size(); i++) {
            Data.BotDef d = defs.get(i);
            BlockPos sp = sb.get(i % sb.size());
            Vec3 v = new Vec3(sp.getX() + .5, sp.getY() + .5, sp.getZ() + .5);
            BotEntity b = TalentShowMod.BOT.get().create(level);
            b.botId = d.id; b.setSkin(d.skin);
            b.setCustomName(Component.literal(d.nick)); b.setCustomNameVisible(true);
            b.moveTo(v.x, v.y, v.z, 180f, 0f); b.setYHeadRot(180f);
            level.addFreshEntity(b);
            bots.put(d.id, b); seats.put(d.id, v);
            order.add(new Performer(d.nick, b, null, d));
        }
        running = true; state = State.RUN;
        broadcast(Component.literal("=== ШОУ ТАЛАНТОВ ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tasks.add(new Task(60, () -> {}));
        tasks.add(new Task(0, this::next));
    }

    public void stop() {
        running = false; state = State.IDLE;
        tasks.clear(); effects.clear(); order.clear(); guests.clear(); guestHome.clear();
        for (BotEntity b : bots.values()) b.discard();
        bots.clear(); seats.clear();
        for (Entity e : spawned) e.discard();
        spawned.clear(); scores.clear(); cooldown = 0;
    }

    /** Игрок хочет выступить: встаёт в начало очереди (если шоу не идёт — запускает его). */
    public void perform(ServerPlayer p) {
        if (!running) start(p);
        if (!running) return;
        order.add(0, new Performer(p.getGameProfile().getName(), null, p, null));
        msg(p, "Ты выступаешь следующим!");
    }

    public void tick() {
        if (!running) return;
        effects.removeIf(Fx::tick);
        if (state == State.RATE || state == State.PICK) {
            if (--waitTicks <= 0) {
                if (state == State.PICK) { broadcast(Component.literal("Номер пропущен.")); next(); }
                else finishPerformance(judgeSum);
            }
            return;
        }
        if (cooldown > 0) { cooldown--; return; }
        Task t = tasks.poll();
        if (t == null) return;
        cooldown = t.wait();
        t.r().run();
    }

    // ================= ход шоу =================
    void next() {
        if (!running) return;
        state = State.RUN; chasts.clear();
        if (order.isEmpty()) { finish(); return; }
        Performer p = order.remove(0);
        current = p;
        if (p.player() != null) playerTurn(p); else botTurn(p);
    }

    void botTurn(Performer p) {
        Data.Talent t = pick(p.def().talents);
        if (t == null) { next(); return; }
        Vec3 c = stageCenter(); tp(p.bot(), c, 0f); poof(c);
        broadcast(Component.literal("На сцене: " + p.name() + " — «" + t.title + "»").withStyle(ChatFormatting.YELLOW));
        String intro = line(p.def().lines.get("intro"));
        if (intro != null) tasks.add(new Task(30, () -> say(p, intro)));
        queue(t, p);
        tasks.add(new Task(10, () -> judge(p, t, () -> askRate(p))));
    }

    void playerTurn(Performer p) {
        playerHome = player.position(); tp(player, stageCenter(), 0f);
        pickList = new ArrayList<>(Data.PLAYER);
        Collections.shuffle(pickList);
        if (pickList.size() > 8) pickList = new ArrayList<>(pickList.subList(0, 8));
        state = State.PICK; waitTicks = 1200;
        player.sendSystemMessage(Component.literal("Твой выход! Выбери номер:").withStyle(ChatFormatting.GOLD));
        for (Data.Talent t : pickList) player.sendSystemMessage(btn("> " + t.title, "/talentshow do " + t.id));
    }

    public void doTalent(ServerPlayer sp, String id) {
        if (state != State.PICK || current == null || current.player() == null) return;
        Data.Talent t = pickList.stream().filter(x -> x.id.equals(id)).findFirst().orElse(null);
        if (t == null) return;
        state = State.RUN; chasts.clear();
        Performer p = current;
        broadcast(Component.literal("На сцене: " + p.name() + " — «" + t.title + "»").withStyle(ChatFormatting.YELLOW));
        queue(t, p);
        tasks.add(new Task(10, () -> judge(p, t, () -> finishPerformance(judgeSum))));
    }

    void queue(Data.Talent t, Performer p) {
        for (Data.Action a : t.actions) if ("chastushka".equals(a.t)) chasts.put(a, startChast(a, p));
        for (Data.Action a : t.actions) tasks.add(new Task(delay(a), () -> exec(a, p)));
        tasks.add(new Task(20, () -> cleanup(p)));
    }

    int delay(Data.Action a) {
        if (a.delay >= 0) return a.delay;
        return switch (a.t) {
            case "dance", "wait" -> a.n;
            case "say", "whisper", "chastushka", "guest_say" -> 40;
            default -> 20;
        };
    }

    void cleanup(Performer p) {
        if (p.bot() != null) {
            tp(p.bot(), seats.get(p.def().id), 180f);
            p.bot().setSkin(p.def().skin); p.bot().removeAllEffects();
        }
        if (p.player() != null && playerHome != null) tp(player, playerHome, player.getYRot());
        for (Entity g : guests) {
            Vec3 h = guestHome.get(g);
            if (h != null) tp(g, h, g instanceof ServerPlayer ? g.getYRot() : 180f);
        }
        guests.clear(); guestHome.clear();
        for (Entity e : spawned) e.discard();
        spawned.clear();
    }

    // ================= оценки =================
    void judge(Performer p, Data.Talent t, Runnable after) {
        List<Data.BotDef> js = new ArrayList<>(Data.BOTS.values());
        js.remove(p.def());
        Collections.shuffle(js);
        js = js.subList(0, Math.min(4, js.size()));
        int sum = 0;
        for (Data.BotDef j : js) {
            int sc = score(j, t); sum += sc;
            tasks.add(new Task(45, () -> judgeSay(j, sc)));
        }
        judgeSum = sum;
        tasks.add(new Task(20, after));
    }

    int score(Data.BotDef j, Data.Talent t) {
        int s = 4 + R.nextInt(3), m = 0;
        for (String tag : t.tags) if (j.likes.contains(tag)) m++;
        s += Math.min(m, 2) * 2;
        if (R.nextInt(100) < 20) s += R.nextBoolean() ? 1 : -1;
        return Math.max(1, Math.min(10, s));
    }

    void judgeSay(Data.BotDef j, int s) {
        String tier = s <= 4 ? "low" : s <= 7 ? "mid" : "high";
        String l = line(j.lines.get(tier));
        chat(j.nick, (l == null ? "..." : l) + " (" + s + "/10)");
        BotEntity b = bots.get(j.id);
        if (b != null && s >= 9) b.swing(InteractionHand.MAIN_HAND);
    }

    void askRate(Performer p) {
        state = State.RATE; waitTicks = 400;
        MutableComponent m = Component.literal("Судьи дали " + judgeSum + ". Твоя оценка для " + p.name() + ": ");
        for (int i = 1; i <= 10; i++) m.append(btn("[" + i + "] ", "/talentshow rate " + i));
        m.append(btn("[ЗОЛОТОЙ БАЗЗЕР]", "/talentshow rate 11"));
        player.sendSystemMessage(m);
    }

    public void rate(ServerPlayer sp, int n) {
        if (state != State.RATE || current == null) return;
        int total = judgeSum + n;
        if (n == 11) {
            if (buzzerUsed) { msg(sp, "Золотой баззер уже использован."); return; }
            buzzerUsed = true; total = judgeSum + 25;
            broadcast(Component.literal("ЗОЛОТОЙ БАЗЗЕР для " + current.name() + "!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            firework(stageCenter());
        }
        finishPerformance(total);
    }

    void finishPerformance(int total) {
        scores.merge(current.name(), total, Integer::sum);
        broadcast(Component.literal("Итог за номер " + current.name() + ": " + total).withStyle(ChatFormatting.GREEN));
        state = State.RUN;
        tasks.add(new Task(40, this::next));
    }

    void finish() {
        broadcast(Component.literal("=== ИТОГИ ШОУ ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        List<Map.Entry<String, Integer>> l = new ArrayList<>(scores.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        int i = 1;
        for (var e : l) {
            broadcast(Component.literal(i + ". " + e.getKey() + " — " + e.getValue())
                    .withStyle(i == 1 ? ChatFormatting.GOLD : ChatFormatting.WHITE));
            i++;
        }
        for (int k = 0; k < 3; k++) firework(stageCenter().add(k * 3 - 3, 0, 0));
        tasks.add(new Task(200, this::stop));
    }

    // ================= действия номеров =================
    void exec(Data.Action a, Performer p) {
        Entity e = p.bot() != null ? p.bot() : p.player();
        if (e == null || a.t == null) return;
        switch (a.t) {
            case "say" -> say(p, a.text);
            case "whisper" -> broadcast(Component.literal("<" + p.name() + "> " + fill(a.text, p))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            case "title" -> {
                for (ServerPlayer sp : server.getPlayerList().getPlayers())
                    sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal(fill(a.text, p))));
            }
            case "dance" -> { if (p.bot() != null) dance(p.bot(), a.n); }
            case "skin" -> {
                if (p.bot() != null) {
                    String n = a.name;
                    if (n == null || n.equals("random")) {
                        List<Data.BotDef> o = new ArrayList<>(Data.BOTS.values());
                        n = o.get(R.nextInt(o.size())).skin;
                    }
                    p.bot().setSkin(n); poof(p.bot().position());
                }
            }
            case "skin_reset" -> { if (p.bot() != null) p.bot().setSkin(p.def().skin); }
            case "firework" -> firework(e.position());
            case "sound" -> {
                SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(a.sound));
                if (se != null) level.playSound(null, BlockPos.containing(e.position()), se, SoundSource.PLAYERS, 1f, a.pitch);
            }
            case "lightning" -> {
                LightningBolt b = EntityType.LIGHTNING_BOLT.create(level);
                b.moveTo(e.position()); b.setVisualOnly(true); level.addFreshEntity(b);
            }
            case "time" -> level.setDayTime(a.n);
            case "weather" -> {
                if ("clear".equals(a.name)) level.setWeatherParameters(6000, 0, false, false);
                else level.setWeatherParameters(0, 6000, true, "thunder".equals(a.name));
            }
            case "invisible" -> { if (p.bot() != null) p.bot().addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, a.n, 0, false, false)); }
            case "glow" -> { if (p.bot() != null) p.bot().addEffect(new MobEffectInstance(MobEffects.GLOWING, a.n, 0, false, false)); }
            case "chastushka" -> chastushka(a, p);
            case "invite" -> invite(a, p);
            case "guest_say" -> {
                if (!guests.isEmpty()) chat(nameOf(guests.get(guests.size() - 1)), fill(a.text, p));
            }
            case "spawn" -> spawnMobs(a);
            default -> {}
        }
    }

    void dance(Entity e, int ticks) {
        int[] n = {ticks};
        effects.add(() -> {
            if (--n[0] <= 0 || !e.isAlive()) return true;
            e.setYRot(e.getYRot() + 15); e.setYHeadRot(e.getYRot());
            if (e instanceof LivingEntity le) { le.yBodyRot = le.getYRot(); if (n[0] % 8 == 0) le.swing(InteractionHand.MAIN_HAND); }
            if (n[0] % 14 == 0 && e.onGround()) e.setDeltaMovement(e.getDeltaMovement().add(0, .42, 0));
            return false;
        });
    }

    void invite(Data.Action a, Performer p) {
        Entity g;
        if ("player".equals(a.name) && p.player() == null) {
            g = player; guestHome.put(g, g.position());
        } else {
            List<BotEntity> c = new ArrayList<>();
            for (BotEntity b : bots.values()) if (b != p.bot() && !guests.contains(b)) c.add(b);
            if (c.isEmpty()) return;
            BotEntity b = c.get(R.nextInt(c.size()));
            g = b; guestHome.put(g, seats.get(b.botId));
        }
        tp(g, stageCenter().add(2 + guests.size() * 1.5, 0, 0), 0f);
        guests.add(g); guestName = nameOf(g);
        poof(g.position());
        if (a.text != null) say(p, a.text);
    }

    void spawnMobs(Data.Action a) {
        EntityType.byString(a.name).ifPresent(et -> {
            List<Entity> ms = new ArrayList<>();
            for (int i = 0; i < Math.min(a.n, 12); i++) {
                Entity m = et.create(level);
                if (m == null) continue;
                Vec3 c = stageCenter();
                m.moveTo(c.x + (R.nextDouble() - .5) * 12, c.y, c.z + (R.nextDouble() - .5) * 4, R.nextFloat() * 360, 0);
                if (m instanceof Mob mob) mob.setNoAi(true);
                level.addFreshEntity(m); spawned.add(m); ms.add(m);
            }
            int[] k = {120};
            effects.add(() -> {
                if (--k[0] <= 0) return true;
                for (Entity m : ms) { m.setYRot(m.getYRot() + 20); m.setYHeadRot(m.getYRot()); }
                return false;
            });
        });
    }

    // ---- частушки про всех (ИИ с запасным шаблоном) ----
    Chast startChast(Data.Action a, Performer p) {
        String mode = a.name == null ? "random" : a.name;
        String target = switch (mode) {
            case "all" -> "all";
            case "player" -> player.getGameProfile().getName();
            case "performer" -> p.name();
            default -> randomName(p.name());
        };
        CompletableFuture<String> f = null;
        if (Data.AI != null && Data.AI.enabled) {
            String who = "all".equals(target) ? "про всех участников шоу: " + String.join(", ", allNames()) : "про участника " + target;
            f = AiClient.ask(SYS, "Сочини весёлую частушку из 4 строк " + who + ". Автор: " + p.name() + ". Только текст частушки.");
        }
        return new Chast(target, f);
    }

    void chastushka(Data.Action a, Performer p) {
        Chast c = chasts.computeIfAbsent(a, k -> startChast(a, p));
        String text = c.ai() != null && c.ai().isDone() ? c.ai().getNow(null) : null;
        if (text == null) {
            List<String> l = "all".equals(c.target()) ? Data.CH_ALL : Data.CH_ONE;
            if (l.isEmpty()) return;
            text = l.get(R.nextInt(l.size())).replace("{target}", c.target()).replace("{all}", String.join(", ", allNames()));
        }
        for (String ln : text.split("\n")) chat(p.name(), ln);
    }

    // ================= утилиты =================
    void say(Performer p, String text) { chat(p.name(), fill(text, p)); }
    void chat(String name, String text) { broadcast(Component.literal("<" + name + "> " + text)); }
    void broadcast(Component c) { server.getPlayerList().broadcastSystemMessage(c, false); }
    void msg(ServerPlayer p, String s) { p.sendSystemMessage(Component.literal(s)); }

    MutableComponent btn(String t, String cmd) {
        return Component.literal(t).withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd)));
    }

    String fill(String s, Performer p) {
        if (s == null) return "";
        s = s.replace("{performer}", p.name()).replace("{player}", player.getGameProfile().getName())
             .replace("{guest}", guestName).replace("{all}", String.join(", ", allNames()));
        while (s.contains("{random}")) s = s.replaceFirst("\\{random}", Matcher.quoteReplacement(randomName(p.name())));
        return s;
    }

    List<String> allNames() {
        List<String> l = new ArrayList<>();
        for (Data.BotDef d : Data.BOTS.values()) l.add(d.nick);
        l.add(player.getGameProfile().getName());
        return l;
    }

    String randomName(String exclude) {
        List<String> l = new ArrayList<>(allNames());
        l.remove(exclude);
        return l.isEmpty() ? exclude : l.get(R.nextInt(l.size()));
    }

    String nameOf(Entity e) { return e instanceof ServerPlayer sp ? sp.getGameProfile().getName() : e.getCustomName().getString(); }

    String line(List<String> l) { return l == null || l.isEmpty() ? null : l.get(R.nextInt(l.size())); }

    Data.Talent pick(List<Data.Talent> l) {
        if (l == null || l.isEmpty()) return null;
        double sum = 0;
        for (Data.Talent t : l) sum += t.weight;
        double r = R.nextDouble() * sum;
        for (Data.Talent t : l) { r -= t.weight; if (r <= 0) return t; }
        return l.get(l.size() - 1);
    }

    Vec3 stageCenter() { return new Vec3(origin.getX() + .5, origin.getY() + 1, origin.getZ() + 1.5); }

    void tp(Entity e, Vec3 v, float yaw) {
        if (e instanceof ServerPlayer sp) sp.teleportTo(level, v.x, v.y, v.z, yaw, 0f);
        else { e.moveTo(v.x, v.y, v.z, yaw, 0f); e.setYHeadRot(yaw); }
    }

    void poof(Vec3 c) { level.sendParticles(ParticleTypes.POOF, c.x, c.y + 1, c.z, 25, .3, .6, .3, .02); }

    void firework(Vec3 v) {
        ItemStack s = new ItemStack(Items.FIREWORK_ROCKET);
        CompoundTag fw = new CompoundTag(); fw.putByte("Flight", (byte) 1);
        ListTag ex = new ListTag(); CompoundTag e = new CompoundTag();
        e.putByte("Type", (byte) R.nextInt(5));
        e.putIntArray("Colors", new int[]{0xFF3030, 0xFFD700, 0x30A0FF});
        ex.add(e); fw.put("Explosions", ex);
        s.getOrCreateTag().put("Fireworks", fw);
        level.addFreshEntity(new FireworkRocketEntity(level, v.x, v.y + 1, v.z, s));
    }
}
