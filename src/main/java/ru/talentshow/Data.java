package ru.talentshow;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import net.minecraftforge.fml.loading.FMLPaths;

/** Всё содержимое шоу лежит в config/talentshow/*.json — правь и добавляй без кода. */
public class Data {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Map<String, BotDef> BOTS = new LinkedHashMap<>();
    public static List<Talent> PLAYER = new ArrayList<>();
    public static List<String> CH_ONE = new ArrayList<>(), CH_ALL = new ArrayList<>();
    public static AiCfg AI = new AiCfg();

    public static class AiCfg { public boolean enabled = false; public String url = ""; public String key = ""; public String model = ""; }
    public static class BotDef {
        public String id, nick, skin;
        public List<String> likes = new ArrayList<>();
        public Map<String, List<String>> lines = new HashMap<>();   // intro / low / mid / high
        public List<Talent> talents = new ArrayList<>();
    }
    public static class Talent {
        public String id, title;
        public double weight = 1.0;                                  // редкие номера: 0.1
        public List<String> tags = new ArrayList<>();
        public List<Action> actions = new ArrayList<>();
    }
    public static class Action {
        public String t;          // тип действия
        public String text, name, sound;
        public float pitch = 1f;
        public int n = 40;        // длительность / время суток / количество
        public int delay = -1;    // пауза после действия (тики), -1 = по умолчанию
    }

    static final String[] DEFAULTS = {
        "bots/domer.json", "bots/rhyme_gradus.json", "bots/nikita_romensky.json",
        "player_talents.json", "chastushki.json", "ai.json"
    };

    public static void load() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("talentshow");
            for (String f : DEFAULTS) {
                Path p = dir.resolve(f);
                if (Files.exists(p)) continue;
                Files.createDirectories(p.getParent());
                try (InputStream in = Data.class.getResourceAsStream("/defaults/" + f)) { Files.copy(in, p); }
            }
            BOTS.clear();
            List<Path> files;
            try (var s = Files.list(dir.resolve("bots"))) { files = s.filter(x -> x.toString().endsWith(".json")).sorted().toList(); }
            for (Path p : files) {
                BotDef b = GSON.fromJson(Files.readString(p), BotDef.class);
                BOTS.put(b.id, b);
            }
            PLAYER = GSON.fromJson(Files.readString(dir.resolve("player_talents.json")), new TypeToken<List<Talent>>(){}.getType());
            JsonObject ch = JsonParser.parseString(Files.readString(dir.resolve("chastushki.json"))).getAsJsonObject();
            CH_ONE = GSON.fromJson(ch.get("single"), new TypeToken<List<String>>(){}.getType());
            CH_ALL = GSON.fromJson(ch.get("all"), new TypeToken<List<String>>(){}.getType());
            AI = GSON.fromJson(Files.readString(dir.resolve("ai.json")), AiCfg.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
