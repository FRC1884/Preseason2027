package org.Griffins1884.frc2027.subsystems.swerve;

import static org.wpilib.units.Units.RadiansPerSecond;

import org.wpilib.math.util.Units;
import org.Griffins1884.frc2027.util.SparkUtil;
import org.Griffins1884.frc2027.simV2.sim3d.TerrainAwareSwerveSimulation;
import org.Griffins1884.frc2027.simV2.sim3d.TerrainSample;

public class GyroIOSim implements GyroIO {
  private final TerrainAwareSwerveSimulation simulation;

  public GyroIOSim(TerrainAwareSwerveSimulation simulation) {
    this.simulation = simulation;
  }

  @Override
  public void updateInputs(GyroIOInputs inputs) {
    TerrainSample terrainSample = simulation.getTerrainSample();
    inputs.connected = true;
    inputs.yawPosition = simulation.getGyroSimulation().getGyroReading();
    inputs.pitchPosition =
        org.wpilib.math.geometry.Rotation2d.fromRadians(terrainSample.pitchRadians());
    inputs.rollPosition =
        org.wpilib.math.geometry.Rotation2d.fromRadians(terrainSample.rollRadians());
    inputs.yawVelocityRadPerSec =
        Units.degreesToRadians(
            simulation.getGyroSimulation().getMeasuredAngularVelocity().in(RadiansPerSecond));
    inputs.pitchVelocityRadPerSec = simulation.getPitchRateRadPerSec();
    inputs.rollVelocityRadPerSec = simulation.getRollRateRadPerSec();

    inputs.odometryYawTimestamps = SparkUtil.getSimulationOdometryTimeStamps();
    inputs.odometryYawPositions = simulation.getGyroSimulation().getCachedGyroReadings();
  }
}
