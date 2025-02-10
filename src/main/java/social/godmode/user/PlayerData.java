package social.godmode.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "player_data")
public class PlayerData {

    @NotNull
    @Id
    UUID uuid;

    @NotNull
    String name;

    int wins, losses;

    @NotNull
    @Enumerated(EnumType.STRING)
    PlayerRank rank;

    @NotNull
    @JdbcTypeCode(SqlTypes.VARCHAR)
    Set<Integer> replayIDs = new HashSet<>();

    public PlayerData() {
    }

    public PlayerData(@NotNull UUID uuid, @NotNull String name) {
        this.uuid = uuid;
        this.name = name;
        this.rank = PlayerRank.DEFAULT;
        this.wins = 0;
        this.losses = 0;
        this.replayIDs = new HashSet<>();
    }

    public PlayerData(@NotNull UUID uuid, @NotNull String name, int wins, int losses, @NotNull PlayerRank rank, @NotNull Set<Integer> replayIDs) {
        this.uuid = uuid;
        this.name = name;
        this.wins = wins;
        this.losses = losses;
        this.rank = rank;
        this.replayIDs = replayIDs;
    }


}
