package org.Griffins1884.frc2027.simV2.sim3d;

import org.wpilib.math.geometry.Rotation3d;
import org.wpilib.math.geometry.Translation3d;

/**
 * Quasi-static load-transfer estimator for a four-module swerve chassis.
 *
 * <p>This is the Phase 4 bridge between terrain/contact geometry and traction-aware drivetrain
 * behavior. It does not replace a full rigid-body tire model, but it provides meaningful wheel loading
 * changes as pitch, roll, and acceleration vary.
 */
public final class SwerveLoadTransferEstimator {
  private static final double GRAVITY_MPS2 = 9.80665;

  private SwerveLoadTransferEstimator() {}

  public static SwerveTractionState estimate(
      ChassisState3d chassisState3d,
      TerrainContactSample terrainContactSample,
      ChassisMassProperties chassisMassProperties) {
    double totalNormalForceNewtons = chassisMassProperties.massKg() * GRAVITY_MPS2;
    double baseWheelLoadNewtons = totalNormalForceNewtons / 4.0;

    Rotation3d orientation = chassisState3d.pose().getRotation();
    double yaw = orientation.getZ();
    Translation3d fieldAcceleration = chassisState3d.fieldRelativeLinearAccelerationMetersPerSecSq();

    double robotForwardAcceleration =
        (fieldAcceleration.getX() * Math.cos(yaw)) + (fieldAcceleration.getY() * Math.sin(yaw));
    double robotLeftAcceleration =
        (-fieldAcceleration.getX() * Math.sin(yaw)) + (fieldAcceleration.getY() * Math.cos(yaw));

    double effectiveForwardAcceleration =
        robotForwardAcceleration + (GRAVITY_MPS2 * Math.sin(orientation.getY()));
    double effectiveLeftAcceleration =
        robotLeftAcceleration + (GRAVITY_MPS2 * Math.sin(orientation.getX()));

    double longitudinalTransferNewtons =
        chassisMassProperties.massKg()
            * chassisMassProperties.centerOfGravityHeightMeters()
            * effectiveForwardAcceleration
            / Math.max(chassisMassProperties.wheelBaseMeters(), 1e-9);
    double lateralTransferNewtons =
        chassisMassProperties.massKg()
            * chassisMassProperties.centerOfGravityHeightMeters()
            * effectiveLeftAcceleration
            / Math.max(chassisMassProperties.trackWidthMeters(), 1e-9);

    double frontLeftNormal =
        baseWheelLoadNewtons - (longitudinalTransferNewtons * 0.5) - (lateralTransferNewtons * 0.5);
    double frontRightNormal =
        baseWheelLoadNewtons - (longitudinalTransferNewtons * 0.5) + (lateralTransferNewtons * 0.5);
    double rearLeftNormal =
        baseWheelLoadNewtons + (longitudinalTransferNewtons * 0.5) - (lateralTransferNewtons * 0.5);
    double rearRightNormal =
        baseWheelLoadNewtons + (longitudinalTransferNewtons * 0.5) + (lateralTransferNewtons * 0.5);

    double[] normalizedLoads =
        normalizeNonNegative(
            frontLeftNormal,
            frontRightNormal,
            rearLeftNormal,
            rearRightNormal,
            totalNormalForceNewtons);

    boolean tractionAvailable =
        terrainContactSample == null
            || (terrainContactSample.traversableSurface() && terrainContactSample.clearanceSatisfied());
    double gripCoefficient =
        tractionAvailable ? chassisMassProperties.nominalTireGripCoefficient() : 0.0;

    WheelLoadSample frontLeft =
        wheelLoad(normalizedLoads[0], baseWheelLoadNewtons, gripCoefficient);
    WheelLoadSample frontRight =
        wheelLoad(normalizedLoads[1], baseWheelLoadNewtons, gripCoefficient);
    WheelLoadSample rearLeft =
        wheelLoad(normalizedLoads[2], baseWheelLoadNewtons, gripCoefficient);
    WheelLoadSample rearRight =
        wheelLoad(normalizedLoads[3], baseWheelLoadNewtons, gripCoefficient);

    return new SwerveTractionState(
        frontLeft,
        frontRight,
        rearLeft,
        rearRight,
        totalNormalForceNewtons,
        (frontLeft.normalizedLoad()
                + frontRight.normalizedLoad()
                + rearLeft.normalizedLoad()
                + rearRight.normalizedLoad())
            / 4.0,
        tractionAvailable);
  }

  private static WheelLoadSample wheelLoad(
      double normalForceNewtons, double baseWheelLoadNewtons, double gripCoefficient) {
    return new WheelLoadSample(
        normalForceNewtons,
        normalForceNewtons * gripCoefficient,
        baseWheelLoadNewtons > 1e-9 ? normalForceNewtons / baseWheelLoadNewtons : 0.0);
  }

  private static double[] normalizeNonNegative(
      double frontLeft,
      double frontRight,
      double rearLeft,
      double rearRight,
      double targetTotal) {
    double[] loads = {
      Math.max(0.0, frontLeft),
      Math.max(0.0, frontRight),
      Math.max(0.0, rearLeft),
      Math.max(0.0, rearRight)
    };
    double sum = loads[0] + loads[1] + loads[2] + loads[3];
    if (sum <= 1e-9) {
      double equalLoad = targetTotal / 4.0;
      return new double[] {equalLoad, equalLoad, equalLoad, equalLoad};
    }
    double scale = targetTotal / sum;
    for (int i = 0; i < loads.length; i++) {
      loads[i] *= scale;
    }
    return loads;
  }
}
