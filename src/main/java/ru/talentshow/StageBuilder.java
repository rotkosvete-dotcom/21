package ru.talentshow;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Сцена как в шоу. Origin = центр сцены (пол сцены на слое Y).
 * Сцена смотрит на +Z (юг): позади (-Z) задник с башнями, впереди (+Z) зал с ярусами.
 */
public class StageBuilder {
    public static final int RX = 14, RZ = 9;

    static BlockState s(Block b) { return b.defaultBlockState(); }
    static void set(ServerLevel l, int x, int y, int z, BlockState st) { l.setBlock(new BlockPos(x, y, z), st, 2); }
    static boolean in(int x, int z, int rx, int rz) { return (double) x * x / (rx * rx) + (double) z * z / (rz * rz) <= 1.0; }

    public static void build(ServerLevel l, BlockPos o) {
        int ox = o.getX(), y0 = o.getY(), oz = o.getZ();
        var r = l.random;
        // очистить место
        for (int x = -24; x <= 24; x++) for (int z = -RZ - 10; z <= RZ + 26; z++) for (int y = 0; y <= 18; y++)
            set(l, ox + x, y0 + y, oz + z, s(Blocks.AIR));
        // сцена: красная шерсть, по краю тёмный дуб
        for (int x = -RX - 1; x <= RX + 1; x++) for (int z = -RZ - 1; z <= RZ + 1; z++) {
            if (in(x, z, RX + 1, RZ + 1)) for (int y = -3; y <= 0; y++) set(l, ox + x, y0 + y, oz + z, s(Blocks.DARK_OAK_PLANKS));
            if (in(x, z, RX, RZ)) set(l, ox + x, y0, oz + z, s(Blocks.RED_WOOL));
        }
        // дорожка и ступени к сцене
        for (int x = -13; x <= 13; x++) for (int z = RZ + 2; z <= RZ + 4; z++) {
            for (int y = -3; y <= -2; y++) set(l, ox + x, y0 + y, oz + z, s(Blocks.DARK_OAK_PLANKS));
            set(l, ox + x, y0 - 1, oz + z, s(Blocks.SMOOTH_SANDSTONE));
        }
        for (int x = -8; x <= 8; x++)
            set(l, ox + x, y0, oz + RZ + 2, s(Blocks.DARK_OAK_STAIRS).setValue(StairBlock.FACING, Direction.NORTH));
        // ярусы зала
        for (int t = 0; t < 4; t++) for (int x = -13; x <= 13; x++) for (int dz = 0; dz < 2; dz++) {
            int z = oz + RZ + 5 + 2 * t + dz;
            for (int y = y0 - 3; y <= y0 - 2 + t; y++) set(l, ox + x, y, z, s(Blocks.DARK_OAK_PLANKS));
            set(l, ox + x, y0 - 1 + t, z, s(t == 0 ? Blocks.SMOOTH_SANDSTONE : Blocks.DARK_OAK_PLANKS));
        }
        // кресла
        for (BlockPos p : seatBlocks(o))
            set(l, p.getX(), p.getY(), p.getZ(), s(Blocks.DARK_OAK_STAIRS).setValue(StairBlock.FACING, Direction.NORTH));
        // задник: стена + две замшелые башни с синими окнами
        for (int x = -13; x <= 13; x++) for (int z = -RZ - 4; z <= -RZ - 3; z++) for (int y = 1; y <= 12; y++)
            set(l, ox + x, y0 + y, oz + z, s(r.nextInt(3) == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int a = 13; a <= 18; a++) for (int z = -RZ - 6; z <= -RZ - 3; z++) for (int y = 0; y <= 15; y++)
                set(l, ox + sx * a, y0 + y, oz + z, s(r.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE_BRICKS));
            for (int a = 15; a <= 16; a++) for (int y = 8; y <= 9; y++)
                set(l, ox + sx * a, y0 + y, oz + (-RZ - 3), s(Blocks.BLUE_STAINED_GLASS));
        }
        // панель под плакат «ШОУ ТАЛАНТОВ» (повесь сюда карту-арт в рамках)
        for (int x = -2; x <= 2; x++) for (int y = 3; y <= 8; y++)
            set(l, ox + x, y0 + y, oz - RZ - 2, s(Blocks.SMOOTH_SANDSTONE));
        // трибуна ведущего
        set(l, ox, y0 + 1, oz - 3, s(Blocks.LECTERN).setValue(LecternBlock.FACING, Direction.SOUTH));
    }

    /** Блоки кресел (слой, где ставится лестница). Те же позиции использует ShowManager для ботов. */
    public static List<BlockPos> seatBlocks(BlockPos o) {
        List<BlockPos> l = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            int step = t == 0 ? 4 : 3;
            for (int x = -12; x <= 12; x += step)
                l.add(new BlockPos(o.getX() + x, o.getY() + t, o.getZ() + RZ + 6 + 2 * t));
        }
        return l;
    }
}
