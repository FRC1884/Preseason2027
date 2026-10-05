package org.Griffins1884.frc2027.simV2.sim3d.integration;

import java.util.function.DoubleSupplier;
import org.Griffins1884.frc2027.simV2.sim3d.ChassisMassProperties;
import org.Griffins1884.frc2027.simV2.sim3d.ChassisFootprint;
import org.Griffins1884.frc2027.simV2.sim3d.DriveSimulationAdapter;
import org.Griffins1884.frc2027.simV2.sim3d.TerrainAwareSwerveSimulation;
import org.Griffins1884.frc2027.simV2.sim3d.TerrainContactModel;
import org.Griffins1884.frc2027.simV2.sim3d.TerrainModel;
import org.Griffins1884.frc2027.simV2.sim3d.maple.MapleSwerveDriveBackend;
import org.Griffins1884.frc2027.simV2.sim3d.native6dof.NativeSixDofSwerveConfig;
import org.Griffins1884.frc2027.simV2.sim3d.native6dof.NativeSixDofSwerveSimulation;
import org.Griffins1884.frc2027.simV2.simulation.drivesims.SwerveDriveSimulation;

/**
 * Construction helpers for choosing a GriffinSim backend behind the stable adapter interface.
 *
 * <p>This is the intended seam for SIM-only Maple-vs-native backend selection. Robot repos should
 * store the returned {@link DriveSimulationAdapter} rather than depending on a backend-specific
 * simulation class wherever possible.
 */
public final class DriveSimulationFactories {
  private DriveSimulationFactories() {}

  public static DriveSimulationAdapter mapleTerrainAware(
      SwerveDriveSimulation mapleSimulation, TerrainModel terrainModel) {
    return new TerrainAwareSwerveSimulation(mapleSimulation, terrainModel);
  }

  public static DriveSimulationAdapter mapleTerrainAware(
      SwerveDriveSimulation mapleSimulation,
      TerrainContactModel terrainContactModel,
      ChassisFootprint chassisFootprint,
      ChassisMassProperties chassisMassProperties) {
    return new TerrainAwareSwerveSimulation(
        new MapleSwerveDriveBackend(mapleSimulation),
        terrainContactModel,
        chassisFootprint,
        chassisMassProperties);
  }

  public static DriveSimulationAdapter mapleTerrainAware(
      SwerveDriveSimulation mapleSimulation,
      TerrainContactModel terrainContactModel,
      ChassisFootprint chassisFootprint,
      ChassisMassProperties chassisMassProperties,
      DoubleSupplier clockSecondsSupplier) {
    return new TerrainAwareSwerveSimulation(
        new MapleSwerveDriveBackend(mapleSimulation),
        terrainContactModel,
        chassisFootprint,
        chassisMassProperties,
        clockSecondsSupplier);
  }

  public static DriveSimulationAdapter nativeSixDof(
      NativeSixDofSwerveConfig config, TerrainModel terrainModel) {
    return new NativeSixDofSwerveSimulation(config, terrainModel);
  }

  public static DriveSimulationAdapter nativeSixDof(
      NativeSixDofSwerveConfig config,
      TerrainModel terrainModel,
      DoubleSupplier clockSecondsSupplier) {
    return new NativeSixDofSwerveSimulation(config, terrainModel, clockSecondsSupplier);
  }

  public static DriveSimulationAdapter nativeSixDof(
      NativeSixDofSwerveConfig config,
      TerrainContactModel terrainContactModel,
      DoubleSupplier clockSecondsSupplier) {
    return new NativeSixDofSwerveSimulation(config, terrainContactModel, clockSecondsSupplier);
  }
}
