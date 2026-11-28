package com.coolexpanded.coolexpandeddeduction.fingerprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class FingerprintLeaveData extends SavedData {

    public static final long BLOCK_LIFETIME_MS = 48L * 60L * 60L * 1000L;   // 2 days
    public static final long PLAYER_LIFETIME_MS = 12L * 60L * 60L * 1000L;  // 12 hours

    public record Trace(UUID playerId, String playerName, String fingerprintId, long createdAt) {

        public boolean isExpired(long lifetimeMs) {
            return System.currentTimeMillis() - createdAt >= lifetimeMs;
        }
    }


    private final Map<String, List<Trace>> blockTraces = new HashMap<>();


    private final Map<UUID, List<Trace>> playerTraces = new HashMap<>();

    public static FingerprintLeaveData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FingerprintLeaveData::new, FingerprintLeaveData::load),
                "coolexpandeddeduction_fingerprint_traces"
        );
    }

    private static String blockKey(Level level, BlockPos pos) {
        return level.dimension().location() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    public boolean addBlockTrace(Level level, BlockPos pos, Trace trace) {
        List<Trace> list = blockTraces.computeIfAbsent(blockKey(level, pos), key -> new ArrayList<>());
        return addTrace(list, trace, BLOCK_LIFETIME_MS);
    }

    public boolean addPlayerTrace(UUID victimId, Trace trace) {
        List<Trace> list = playerTraces.computeIfAbsent(victimId, key -> new ArrayList<>());
        return addTrace(list, trace, PLAYER_LIFETIME_MS);
    }

    private boolean addTrace(List<Trace> list, Trace trace, long lifetimeMs) {
        removeExpired(list, lifetimeMs);

        for (Trace old : list) {
            if (old.playerId().equals(trace.playerId())) {
                return false;
            }
        }

        list.add(0, trace);
        setDirty();
        return true;
    }

    public List<Trace> getBlockTraces(Level level, BlockPos pos) {
        List<Trace> list = blockTraces.get(blockKey(level, pos));
        if (list == null) {
            return List.of();
        }
        removeExpired(list, BLOCK_LIFETIME_MS);
        return List.copyOf(list);
    }

    public List<Trace> getPlayerTraces(UUID victimId) {
        List<Trace> list = playerTraces.get(victimId);
        if (list == null) {
            return List.of();
        }
        removeExpired(list, PLAYER_LIFETIME_MS);
        return List.copyOf(list);
    }

    private void removeExpired(List<Trace> list, long lifetimeMs) {
        if (list.removeIf(trace -> trace.isExpired(lifetimeMs))) {
            setDirty();
        }
    }

    public void removeAllExpired() {
        for (List<Trace> list : blockTraces.values()) {
            removeExpired(list, BLOCK_LIFETIME_MS);
        }
        for (List<Trace> list : playerTraces.values()) {
            removeExpired(list, PLAYER_LIFETIME_MS);
        }

        blockTraces.values().removeIf(List::isEmpty);
        playerTraces.values().removeIf(List::isEmpty);
    }


    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag blocks = new ListTag();
        for (Map.Entry<String, List<Trace>> entry : blockTraces.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            CompoundTag blockTag = new CompoundTag();
            blockTag.putString("key", entry.getKey());
            blockTag.put("traces", saveList(entry.getValue()));
            blocks.add(blockTag);
        }
        tag.put("blocks", blocks);

        ListTag players = new ListTag();
        for (Map.Entry<UUID, List<Trace>> entry : playerTraces.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("victim", entry.getKey());
            playerTag.put("traces", saveList(entry.getValue()));
            players.add(playerTag);
        }
        tag.put("players", players);

        return tag;
    }

    public static FingerprintLeaveData load(CompoundTag tag, HolderLookup.Provider registries) {
        FingerprintLeaveData data = new FingerprintLeaveData();

        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag blockTag = blocks.getCompound(i);
            data.blockTraces.put(
                    blockTag.getString("key"),
                    loadList(blockTag.getList("traces", Tag.TAG_COMPOUND))
            );
        }

        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            data.playerTraces.put(
                    playerTag.getUUID("victim"),
                    loadList(playerTag.getList("traces", Tag.TAG_COMPOUND))
            );
        }

        return data;
    }

    private static ListTag saveList(List<Trace> list) {
        ListTag listTag = new ListTag();
        for (Trace trace : list) {
            CompoundTag traceTag = new CompoundTag();
            traceTag.putUUID("player", trace.playerId());
            traceTag.putString("name", trace.playerName());
            traceTag.putString("id", trace.fingerprintId());
            traceTag.putLong("time", trace.createdAt());
            listTag.add(traceTag);
        }
        return listTag;
    }

    private static List<Trace> loadList(ListTag listTag) {
        List<Trace> list = new ArrayList<>();
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag traceTag = listTag.getCompound(i);
            list.add(new Trace(
                    traceTag.getUUID("player"),
                    traceTag.getString("name"),
                    traceTag.getString("id"),
                    traceTag.getLong("time")
            ));
        }
        return list;
    }
}