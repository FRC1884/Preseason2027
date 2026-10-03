package org.Griffins1884.frc2027.util;

import org.wpilib.driverstation.internal.DriverStationBackend;
import org.wpilib.driverstation.Alliance;
import java.util.Optional;

/** Alliance state helper that does not encode a season field's dimensions or coordinates. */
public final class AllianceUtil {
  private static Optional<Alliance> cachedAlliance = Optional.empty();

  private AllianceUtil() {}

  public static Optional<Alliance> getAlliance() {
    Optional<Alliance> liveAlliance = DriverStationBackend.getAlliance();
    liveAlliance.ifPresent(alliance -> cachedAlliance = Optional.of(alliance));
    return liveAlliance.isPresent() ? liveAlliance : cachedAlliance;
  }

  public static boolean shouldFlip() {
    return getAlliance().orElse(Alliance.BLUE) == Alliance.RED;
  }

  static void clearCachedAllianceForTesting() {
    cachedAlliance = Optional.empty();
  }
}
