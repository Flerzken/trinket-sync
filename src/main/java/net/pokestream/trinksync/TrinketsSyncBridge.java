package net.pokestream.trinksync;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.UUID;
import java.nio.ByteBuffer;
public class TrinketsSyncBridge implements ModInitializer {
  private static HikariDataSource DS;
  private static String jdbcUrl  = System.getenv().getOrDefault("TSB_DB_URL",  "jdbc:mysql://127.0.0.1:3306/trinkets?useSSL=false&characterEncoding=UTF-8&serverTimezone=UTC");
  private static String dbUser   = System.getenv().getOrDefault("TSB_DB_USER", "root");
  private static String dbPass   = System.getenv().getOrDefault("TSB_DB_PASS", "");
  @Override public void onInitialize() {
    ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
    ServerLifecycleEvents.SERVER_STOPPED.register(s -> { if (DS != null) DS.close(); });
    ServerPlayConnectionEvents.DISCONNECT.register(this::onDisconnect);
    ServerPlayConnectionEvents.JOIN.register(this::onJoin);
  }
  private void onServerStarting(MinecraftServer server) {
    try {
      HikariConfig cfg = new HikariConfig();
      cfg.setJdbcUrl(jdbcUrl);
      cfg.setUsername(dbUser);
      cfg.setPassword(dbPass);
      cfg.setMaximumPoolSize(5);
      cfg.setMinimumIdle(1);
      cfg.setPoolName("TSB-MySQL");
      DS = new HikariDataSource(cfg);
      try (Connection c = DS.getConnection();
           Statement st = c.createStatement()) {
        st.executeUpdate("""
          CREATE TABLE IF NOT EXISTS trinkets_data (
            uuid BINARY(16) PRIMARY KEY,
            data LONGBLOB NOT NULL,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """);
      }
      server.getLogger().info("[TrinketsSyncBridge/MySQL] Connected: " + jdbcUrl);
    } catch (Exception e) {
      server.getLogger().error("[TrinketsSyncBridge/MySQL] Failed to init datasource", e);
    }
  }
  private static byte[] toBytes(UUID uuid) {
    ByteBuffer bb = ByteBuffer.allocate(16);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return bb.array();
  }
  private void onDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server) {
    ServerPlayerEntity player = handler.player;
    TrinketsApi.getTrinketComponent(player).ifPresent(comp -> {
      try {
        RegistryWrapper.WrapperLookup lookup = player.getRegistryManager();
        NbtCompound tr = new NbtCompound();
        comp.writeToNbt(tr, lookup);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tr, out);
        byte[] nbt = out.toByteArray();
        try (Connection c = DS.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO trinkets_data (uuid, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data=VALUES(data), updated_at=CURRENT_TIMESTAMP")) {
          ps.setBytes(1, toBytes(player.getUuid()));
          ps.setBytes(2, nbt);
          ps.executeUpdate();
        }
        server.getLogger().info("[TrinketsSyncBridge/MySQL] Saved " + player.getName().getString());
      } catch (Exception e) {
        server.getLogger().error("[TrinketsSyncBridge/MySQL] Save failed", e);
      }
    });
  }
  private void onJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
    ServerPlayerEntity player = handler.player;
    try (Connection c = DS.getConnection();
         PreparedStatement ps = c.prepareStatement("SELECT data FROM trinkets_data WHERE uuid=?")) {
      ps.setBytes(1, toBytes(player.getUuid()));
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return;
        byte[] data = rs.getBytes(1);
        if (data == null || data.length == 0) return;
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        NbtCompound tr = NbtIo.readCompressed(in);
        if (tr == null || tr.isEmpty()) return;
        TrinketsApi.getTrinketComponent(player).ifPresent(comp -> {
          try {
            RegistryWrapper.WrapperLookup lookup = player.getRegistryManager();
            comp.readFromNbt(tr, lookup);
            comp.sync();
            server.getLogger().info("[TrinketsSyncBridge/MySQL] Loaded " + player.getName().getString());
          } catch (Exception ex) {
            server.getLogger().error("[TrinketsSyncBridge/MySQL] Load failed", ex);
          }
        });
      }
    } catch (Exception e) {
      server.getLogger().error("[TrinketsSyncBridge/MySQL] Read failed", e);
    }
  }
}
