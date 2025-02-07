package social.godmode.replay;

import social.godmode.FillerAPI.FillerColor;

import java.util.List;
import java.util.UUID;

public record Replay(long seed, List<FillerColor> colors, UUID winner, UUID loser) {
}
