package org.Griffins1884.frc2027.simV2.sim3d;

import org.wpilib.math.geometry.Rotation3d;

/** Simulated IMU orientation and angular-rate sample. */
public record SimImuSample(
    Rotation3d orientation, AngularVelocity3d angularVelocityRadPerSec) {
  public double yawRateRadPerSec() {
    return angularVelocityRadPerSec.yawRateRadPerSec();
  }

  public double pitchRateRadPerSec() {
    return angularVelocityRadPerSec.pitchRateRadPerSec();
  }

  public double rollRateRadPerSec() {
    return angularVelocityRadPerSec.rollRateRadPerSec();
  }
}
