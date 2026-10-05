package org.Griffins1884.frc2027.simV2.sim3d.native6dof;

import org.wpilib.math.geometry.Translation2d;
import java.util.Arrays;
import java.util.Objects;
import org.Griffins1884.frc2027.simV2.sim3d.ChassisFootprint;
import org.Griffins1884.frc2027.simV2.sim3d.ChassisMassProperties;

/**
 * Configuration for GriffinSim's native rigid-body swerve path.
 *
 * <p>This models a rigid chassis with four steerable wheel contact points. Because the target robot
 * is a rigid swerve drivetrain, this config intentionally models contact compliance rather than
 * suspension travel.
 */
public final class NativeSixDofSwerveConfig {
  private final ChassisFootprint chassisFootprint;
  private final ChassisMassProperties chassisMassProperties;
  private final Translation2d[] moduleLocationsMeters;
  private final double rollMomentOfInertiaKgMetersSq;
  private final double pitchMomentOfInertiaKgMetersSq;
  private final double yawMomentOfInertiaKgMetersSq;
  private final double wheelContactStiffnessNewtonsPerMeter;
  private final double wheelContactDampingNewtonsPerMeterPerSec;
  private final double bodyContactStiffnessNewtonsPerMeter;
  private final double bodyContactDampingNewtonsPerMeterPerSec;
  private final double longitudinalVelocityGainNewtonsPerMeterPerSec;
  private final double lateralVelocityGainNewtonsPerMeterPerSec;
  private final double maxDriveForcePerWheelNewtons;
  private final double maxSteerRateRadPerSec;
  private final double lateralGripFraction;

  public NativeSixDofSwerveConfig(
      ChassisFootprint chassisFootprint,
      ChassisMassProperties chassisMassProperties,
      Translation2d[] moduleLocationsMeters,
      double rollMomentOfInertiaKgMetersSq,
      double pitchMomentOfInertiaKgMetersSq,
      double yawMomentOfInertiaKgMetersSq,
      double wheelContactStiffnessNewtonsPerMeter,
      double wheelContactDampingNewtonsPerMeterPerSec,
      double bodyContactStiffnessNewtonsPerMeter,
      double bodyContactDampingNewtonsPerMeterPerSec,
      double longitudinalVelocityGainNewtonsPerMeterPerSec,
      double lateralVelocityGainNewtonsPerMeterPerSec,
      double maxDriveForcePerWheelNewtons,
      double maxSteerRateRadPerSec,
      double lateralGripFraction) {
    this.chassisFootprint = Objects.requireNonNull(chassisFootprint);
    this.chassisMassProperties = Objects.requireNonNull(chassisMassProperties);
    this.moduleLocationsMeters = Objects.requireNonNull(moduleLocationsMeters).clone();
    if (this.moduleLocationsMeters.length != 4) {
      throw new IllegalArgumentException("Native 6DOF backend currently expects four swerve modules");
    }
    this.rollMomentOfInertiaKgMetersSq = positive(rollMomentOfInertiaKgMetersSq, "roll inertia");
    this.pitchMomentOfInertiaKgMetersSq = positive(pitchMomentOfInertiaKgMetersSq, "pitch inertia");
    this.yawMomentOfInertiaKgMetersSq = positive(yawMomentOfInertiaKgMetersSq, "yaw inertia");
    this.wheelContactStiffnessNewtonsPerMeter =
        positive(wheelContactStiffnessNewtonsPerMeter, "wheel stiffness");
    this.wheelContactDampingNewtonsPerMeterPerSec =
        positive(wheelContactDampingNewtonsPerMeterPerSec, "wheel damping");
    this.bodyContactStiffnessNewtonsPerMeter =
        positive(bodyContactStiffnessNewtonsPerMeter, "body stiffness");
    this.bodyContactDampingNewtonsPerMeterPerSec =
        positive(bodyContactDampingNewtonsPerMeterPerSec, "body damping");
    this.longitudinalVelocityGainNewtonsPerMeterPerSec =
        positive(longitudinalVelocityGainNewtonsPerMeterPerSec, "longitudinal gain");
    this.lateralVelocityGainNewtonsPerMeterPerSec =
        positive(lateralVelocityGainNewtonsPerMeterPerSec, "lateral gain");
    this.maxDriveForcePerWheelNewtons =
        positive(maxDriveForcePerWheelNewtons, "max drive force");
    this.maxSteerRateRadPerSec = positive(maxSteerRateRadPerSec, "max steer rate");
    this.lateralGripFraction = clamp(lateralGripFraction, 0.0, 1.0);
  }

  public static NativeSixDofSwerveConfig rigidSwerve(
      ChassisFootprint chassisFootprint,
      ChassisMassProperties chassisMassProperties,
      double wheelBaseMeters,
      double trackWidthMeters) {
    double massKg = chassisMassProperties.massKg();
    double heightMeters = chassisFootprint.heightMeters();
    double rollInertia = massKg * ((trackWidthMeters * trackWidthMeters) + (heightMeters * heightMeters)) / 12.0;
    double pitchInertia = massKg * ((wheelBaseMeters * wheelBaseMeters) + (heightMeters * heightMeters)) / 12.0;
    double yawInertia = massKg * ((wheelBaseMeters * wheelBaseMeters) + (trackWidthMeters * trackWidthMeters)) / 12.0;

    return new NativeSixDofSwerveConfig(
        chassisFootprint,
        chassisMassProperties,
        new Translation2d[] {
          new Translation2d(wheelBaseMeters * 0.5, trackWidthMeters * 0.5),
          new Translation2d(wheelBaseMeters * 0.5, -trackWidthMeters * 0.5),
          new Translation2d(-wheelBaseMeters * 0.5, trackWidthMeters * 0.5),
          new Translation2d(-wheelBaseMeters * 0.5, -trackWidthMeters * 0.5)
        },
        rollInertia,
        pitchInertia,
        yawInertia,
        45_000.0,
        3_500.0,
        55_000.0,
        4_500.0,
        450.0,
        350.0,
        250.0,
        Math.toRadians(720.0),
        0.85);
  }

  public ChassisFootprint chassisFootprint() {
    return chassisFootprint;
  }

  public ChassisMassProperties chassisMassProperties() {
    return chassisMassProperties;
  }

  public Translation2d[] moduleLocationsMeters() {
    return moduleLocationsMeters.clone();
  }

  public double rollMomentOfInertiaKgMetersSq() {
    return rollMomentOfInertiaKgMetersSq;
  }

  public double pitchMomentOfInertiaKgMetersSq() {
    return pitchMomentOfInertiaKgMetersSq;
  }

  public double yawMomentOfInertiaKgMetersSq() {
    return yawMomentOfInertiaKgMetersSq;
  }

  public double wheelContactStiffnessNewtonsPerMeter() {
    return wheelContactStiffnessNewtonsPerMeter;
  }

  public double wheelContactDampingNewtonsPerMeterPerSec() {
    return wheelContactDampingNewtonsPerMeterPerSec;
  }

  public double bodyContactStiffnessNewtonsPerMeter() {
    return bodyContactStiffnessNewtonsPerMeter;
  }

  public double bodyContactDampingNewtonsPerMeterPerSec() {
    return bodyContactDampingNewtonsPerMeterPerSec;
  }

  public double longitudinalVelocityGainNewtonsPerMeterPerSec() {
    return longitudinalVelocityGainNewtonsPerMeterPerSec;
  }

  public double lateralVelocityGainNewtonsPerMeterPerSec() {
    return lateralVelocityGainNewtonsPerMeterPerSec;
  }

  public double maxDriveForcePerWheelNewtons() {
    return maxDriveForcePerWheelNewtons;
  }

  public double maxSteerRateRadPerSec() {
    return maxSteerRateRadPerSec;
  }

  public double lateralGripFraction() {
    return lateralGripFraction;
  }

  @Override
  public String toString() {
    return "NativeSixDofSwerveConfig{"
        + "chassisFootprint="
        + chassisFootprint
        + ", chassisMassProperties="
        + chassisMassProperties
        + ", moduleLocationsMeters="
        + Arrays.toString(moduleLocationsMeters)
        + '}';
  }

  private static double positive(double value, String name) {
    if (value <= 0.0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
