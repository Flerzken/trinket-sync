package net.lucasdev.trinketssync;

import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

public class TrinketsService {
    private final DatabaseManager db;
    private long lastAutosaveMs = 0L;

    public TrinketsService(DatabaseManager db) { this.db = db; }

    public void loadFor(ServerPlayerEntity player) throws Exception {
        if (!Config.INSTANCE.loadOnJoin) return;
        Optional<String> encoded = db.load(player.getUuid());
        if (encoded.isEmpty()) return;

        TrinketComponent comp = TrinketsApi.getTrinketComponent(player).orElse(null);
        if (comp == null) return;

        byte[] bytes = Base64.getDecoder().decode(encoded.get());
        NbtCompound nbt;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            nbt = NbtIo.readCompressed(in);
        }
        RegistryWrapper.WrapperLookup lookup = (RegistryWrapper.WrapperLookup) player.getRegistryManager();
        comp.readFromNbt(nbt, lookup);
        comp.sync();
        // Resync au tick suivant pour éviter les conflits de mods à init tardif
        player.server.execute(() -> TrinketsApi.getTrinketComponent(player).ifPresent(TrinketComponent::sync));
    }

    public void saveFor(ServerPlayerEntity player) throws Exception {
        if (!Config.INSTANCE.saveOnQuit) return;
        TrinketComponent comp = TrinketsApi.getTrinketComponent(player).orElse(null);
        if (comp == null) return;
        NbtCompound nbt = new NbtCompound();
        RegistryWrapper.WrapperLookup lookup = (RegistryWrapper.WrapperLookup) player.getRegistryManager();
        comp.writeToNbt(nbt, lookup);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(nbt, out);
        String encoded = Base64.getEncoder().encodeToString(out.toByteArray());
        db.save(player.getUuid(), encoded);
    }

    public void flushAll(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            try { saveFor(p); } catch (Exception ignored) {}
        }
    }

    public void maybeAutosave(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastAutosaveMs < Config.INSTANCE.autosaveSeconds * 1000L) return;
        lastAutosaveMs = now;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            try { saveFor(p); } catch (Exception e) {
                TrinketsSyncMod.LOGGER.error("[TrinketsSync] Autosave failed for {}", p.getGameProfile().getName(), e);
            }
        }
    }
}
