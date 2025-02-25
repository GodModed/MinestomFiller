package social.godmode.replay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import social.godmode.FillerAPI.FillerColor;
import social.godmode.user.GamePlayer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReplayManager {

    private static final Logger log = LoggerFactory.getLogger(ReplayManager.class);

    public ReplayManager() {
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        try (Connection connection = DriverManager.getConnection(GamePlayer.DATABASE_URL);
             Statement statement = connection.createStatement()) {

            String sql = "CREATE TABLE IF NOT EXISTS replays (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "seed INTEGER," +
                    "winner TEXT," +
                    "loser TEXT," +
                    "colors TEXT" +
                    ");";
            statement.execute(sql);

        } catch (SQLException e) {
            log.error("Error creating table", e);
        }
    }

    public int saveReplay(Replay replay) {
        try (Connection connection = DriverManager.getConnection(GamePlayer.DATABASE_URL);
             Statement statement = connection.createStatement()) {

            String sql = "INSERT INTO replays (seed, winner, loser, colors) VALUES (?, ?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                preparedStatement.setLong(1, replay.seed());
                preparedStatement.setString(2, replay.winner().toString());
                preparedStatement.setString(3, replay.loser().toString());
                preparedStatement.setString(4, replay.colors().toString());
                preparedStatement.executeUpdate();

                try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Creating replay failed, no ID obtained.");
                    }
                }
            }

        } catch (SQLException e) {
            log.error("Error saving replay", e);
        }
        return -1;
    }

    public Replay loadReplay(int id) {
        try (Connection connection = DriverManager.getConnection(GamePlayer.DATABASE_URL);
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT seed, winner, loser, colors FROM replays WHERE id = ?")) {

            preparedStatement.setInt(1, id);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {

                String colorsString = resultSet.getString("colors");
                List<FillerColor> colors = new ArrayList<>();
                if (colorsString != null && !colorsString.isEmpty()) {
                    String[] colorStrings = colorsString.substring(1, colorsString.length() - 1).split(", ");
                    for (String color : colorStrings) {
                        colors.add(FillerColor.valueOf(color));
                    }
                }

                return new Replay(resultSet.getLong("seed"), colors, UUID.fromString(resultSet.getString("winner")), UUID.fromString(resultSet.getString("loser")));
            }

        } catch (SQLException e) {
            log.error("Error loading replay", e);
        }
        return null;
    }

}
