package org.Griffins1884.frc2027.simV2.sim3d;

/** Load distribution and traction estimates for a four-module swerve chassis. */
public record SwerveTractionState(
    WheelLoadSample frontLeft,
    WheelLoadSample frontRight,
    WheelLoadSample rearLeft,
    WheelLoadSample rearRight,
    double totalNormalForceNewtons,
    double averageNormalizedLoad,
    boolean tractionAvailable) {
  public WheelLoadSample forCorner(SwerveCorner corner) {
    return switch (corner) {
      case FRONT_LEFT -> frontLeft;
      case FRONT_RIGHT -> frontRight;
      case REAR_LEFT -> rearLeft;
      case REAR_RIGHT -> rearRight;
    };
  }

  public static SwerveTractionState unsupported() {
    WheelLoadSample unsupportedWheel = new WheelLoadSample(0.0, 0.0, 0.0);
    return new SwerveTractionState(
        unsupportedWheel,
        unsupportedWheel,
        unsupportedWheel,
        unsupportedWheel,
        0.0,
        0.0,
        false);
  }
}
