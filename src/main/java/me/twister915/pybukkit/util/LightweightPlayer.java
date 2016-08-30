package me.twister915.pybukkit.util;

import lombok.ToString;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.UUID;

@ToString(of = {"uuid"})
public final class LightweightPlayer {
    private final WeakReference<Player> player;
    private final UUID uuid;

    public LightweightPlayer(Player key) {
        player = new WeakReference<>(key);
        uuid = key.getUniqueId();
    }

    public Player getPlayer() {
        Player player = this.player.get();
        if (player == null) player = Bukkit.getPlayer(uuid);
        if (player == null) throw new IllegalStateException("Could not get the player!");
        if (!player.isOnline()) throw new IllegalStateException("The player is offline!");
        return player;
    }

    public Optional<Player> getPlayerSafe() {
        try {
            return Optional.of(getPlayer());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(Object o) {
        return (this == o) || (!(o == null || getClass() != o.getClass()) && uuid.equals(((LightweightPlayer) o).uuid));
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
