package org.Griffins1884.frc2027.simV2.sim3d;

/** Shared scaling laws that translate terrain contact and wheel loading into drivetrain authority. */
public final class TerrainDriveLaws {
  private TerrainDriveLaws() {}

  public static double driveAuthorityScale(
      SwerveTractionState tractionState, SwerveCorner corner, TerrainContactSample contactSample) {
    if (tractionState == null || corner == null) {
      return contactSample == null ? 1.0 : slopeDriveScale(contactSample);
    }
    if (!tractionState.tractionAvailable()) {
      return 0.0;
    }

    double driveScale = 1.0;
    if (contactSample == null) {
      return driveScale;
    }
    if (!contactSample.traversableSurface()) {
      return 0.0;
    }
    if (!contactSample.clearanceSatisfied()) {
      driveScale *= 0.2;
    }
    return driveScale * slopeDriveScale(contactSample);
  }

  public static double steerAuthorityScale(
      SwerveTractionState tractionState, SwerveCorner corner, TerrainContactSample contactSample) {
    if (tractionState == null || corner == null) {
      return contactSample != null && !contactSample.traversableSurface() ? 0.0 : 1.0;
    }
    if (!tractionState.tractionAvailable()) {
      return 0.0;
    }

    double steerScale = 1.0;
    if (contactSample == null) {
      return steerScale;
    }
    if (!contactSample.traversableSurface()) {
      return 0.0;
    }
    if (!contactSample.clearanceSatisfied()) {
      steerScale *= 0.35;
    }
    return steerScale * slopeSteerScale(contactSample);
  }

  private static double slopeDriveScale(TerrainContactSample contactSample) {
    if (contactSample == null) {
      return 1.0;
    }
    double effectiveSlopeRadians = effectiveSlopeRadians(contactSample.terrainSample());
    double scale =
        Math.cos(effectiveSlopeRadians) - (0.35 * Math.sin(effectiveSlopeRadians));
    return clamp(scale, 0.55, 1.0);
  }

  private static double slopeSteerScale(TerrainContactSample contactSample) {
    if (contactSample == null) {
      return 1.0;
    }
    double effectiveSlopeRadians = effectiveSlopeRadians(contactSample.terrainSample());
    double scale =
        Math.cos(effectiveSlopeRadians) - (0.15 * Math.sin(effectiveSlopeRadians));
    return clamp(scale, 0.7, 1.0);
  }

  private static double effectiveSlopeRadians(TerrainSample terrainSample) {
    if (terrainSample == null) {
      return 0.0;
    }
    double slopeTan =
        Math.hypot(Math.tan(terrainSample.rollRadians()), Math.tan(terrainSample.pitchRadians()));
    return Math.atan(slopeTan);
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
