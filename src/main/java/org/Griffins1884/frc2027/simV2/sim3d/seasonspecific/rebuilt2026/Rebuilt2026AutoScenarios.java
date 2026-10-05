package org.Griffins1884.frc2027.simV2.sim3d.seasonspecific.rebuilt2026;

import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.util.Units;
import java.util.List;
import org.Griffins1884.frc2027.simV2.sim3d.integration.SimulationScenario;

/** Repeatable autonomous-oriented scenarios for the rebuilt 2026 field. */
public final class Rebuilt2026AutoScenarios {
  private Rebuilt2026AutoScenarios() {}

  public static List<SimulationScenario> all() {
    return List.of(blueLeftBumpCross(), blueRightTrenchUnderpass(), redRightBumpCross(), redLeftTrenchUnderpass());
  }

  public static SimulationScenario blueLeftBumpCross() {
    return new SimulationScenario(
        "blue-left-bump-cross",
        "Crosses the blue left bump from hub-side approach into the left outer lane.",
        new Pose2d(4.60, 4.85, Rotation2d.fromDegrees(90.0)),
        new Pose2d(4.60, 6.15, Rotation2d.fromDegrees(90.0)));
  }

  public static SimulationScenario blueRightTrenchUnderpass() {
    return new SimulationScenario(
        "blue-right-trench-underpass",
        "Traverses the blue right trench opening while remaining below the underpass clearance.",
        new Pose2d(4.60, Units.inchesToMeters(8.0), new Rotation2d()),
        new Pose2d(4.60, Units.inchesToMeters(40.0), new Rotation2d()));
  }

  public static SimulationScenario redRightBumpCross() {
    return new SimulationScenario(
        "red-right-bump-cross",
        "Crosses the red right bump from hub-side approach into the right outer lane.",
        new Pose2d(11.90, 3.20, Rotation2d.fromDegrees(-90.0)),
        new Pose2d(11.90, 1.90, Rotation2d.fromDegrees(-90.0)));
  }

  public static SimulationScenario redLeftTrenchUnderpass() {
    return new SimulationScenario(
        "red-left-trench-underpass",
        "Traverses the red left trench opening while remaining below the underpass clearance.",
        new Pose2d(11.90, Rebuilt2026FieldContactModel.fieldWidthMeters() - Units.inchesToMeters(40.0), new Rotation2d()),
        new Pose2d(11.90, Rebuilt2026FieldContactModel.fieldWidthMeters() - Units.inchesToMeters(8.0), new Rotation2d()));
  }
}
