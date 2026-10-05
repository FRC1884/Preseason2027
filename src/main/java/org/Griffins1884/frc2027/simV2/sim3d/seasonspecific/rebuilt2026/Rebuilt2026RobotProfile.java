package org.Griffins1884.frc2027.simV2.sim3d.seasonspecific.rebuilt2026;

import org.wpilib.math.util.Units;
import org.Griffins1884.frc2027.simV2.sim3d.ChassisFootprint;
import org.Griffins1884.frc2027.simV2.sim3d.ChassisMassProperties;

/** Default chassis envelope and mass properties for 2026 rebuilt field simulation work. */
public final class Rebuilt2026RobotProfile {
  public static final ChassisFootprint DEFAULT_FOOTPRINT =
      new ChassisFootprint(
          Units.inchesToMeters(34.0),
          Units.inchesToMeters(34.0),
          Units.inchesToMeters(21.75),
          Units.inchesToMeters(1.5));

  public static final ChassisMassProperties DEFAULT_CHASSIS_MASS_PROPERTIES =
      new ChassisMassProperties(
          61.235,
          Units.inchesToMeters(11.5),
          Units.inchesToMeters(27.5),
          Units.inchesToMeters(27.5),
          1.1);

  private Rebuilt2026RobotProfile() {}
}
