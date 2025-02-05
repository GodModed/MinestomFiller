package social.godmode;

public enum PlayerRank {
    DEFAULT,
    DONATOR,
    OWNER;

    public String getPrefix() {
        return switch (this) {
            case DEFAULT -> "<gray><bold>DEFAULT</bold>";
            case DONATOR -> "<aqua><bold>DONATOR</bold>";
            case OWNER -> "<red><bold>OWNER</bold>";
        };
    }
}
