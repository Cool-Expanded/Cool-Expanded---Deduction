package com.coolexpanded.coolexpandeddeduction.fingerprint;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class FingerprintData extends SavedData {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Random RANDOM = new Random();

    private final Map<UUID, String> fingerprints = new HashMap<>();

    public static FingerprintData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FingerprintData::new, FingerprintData::load),
                "coolexpandeddeduction_fingerprints"
        );
    }



    public String getId(UUID playerId) {
        return fingerprints.get(playerId);
    }

    public String getOrCreateId(UUID playerId) {
        String id = fingerprints.get(playerId);

        if (id != null) {
            return id;
        }

        id = generateUniqueId();
        fingerprints.put(playerId, id);
        setDirty();

        return id;
    }


    public void setId(UUID playerId, String id) {
        fingerprints.put(playerId, id);
        setDirty();
    }

    public boolean isTaken(String id, UUID owner) {
        for (Map.Entry<UUID, String> entry : fingerprints.entrySet()) {
            if (entry.getValue().equals(id) && !entry.getKey().equals(owner)) {
                return true;
            }
        }

        return false;
    }



    public String generateUniqueId() {
        String id;

        do {
            int length = 8 + RANDOM.nextInt(3);
            StringBuilder result = new StringBuilder(length);

            for (int i = 0; i < length; i++) {
                int index = RANDOM.nextInt(CHARACTERS.length());
                result.append(CHARACTERS.charAt(index));
            }

            id = result.toString();
        } while (isTaken(id, null));

        return id;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();

        for (Map.Entry<UUID, String> entry : fingerprints.entrySet()) {
            CompoundTag playerTag = new CompoundTag();

            playerTag.putString("uuid", entry.getKey().toString());
            playerTag.putString("id", entry.getValue());

            players.add(playerTag);
        }

        tag.put("players", players);
        return tag;
    }


    public static FingerprintData load(CompoundTag tag, HolderLookup.Provider registries) {
        FingerprintData data = new FingerprintData();
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);

        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);

            UUID playerUid = UUID.fromString(playerTag.getString("uuid"));
            String id = playerTag.getString("id");

            data.fingerprints.put(playerUid, id);
        }

        return data;
    }
}