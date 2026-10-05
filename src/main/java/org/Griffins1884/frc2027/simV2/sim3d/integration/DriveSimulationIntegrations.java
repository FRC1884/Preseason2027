package org.Griffins1884.frc2027.simV2.sim3d.integration;

import java.util.Objects;
import org.Griffins1884.frc2027.simV2.sim3d.DriveSimulationAdapter;

/** Helper factory methods for autonomous and visualization integration. */
public final class DriveSimulationIntegrations {
  private DriveSimulationIntegrations() {}

  public static HolonomicAutoHooks holonomicAutoHooks(DriveSimulationAdapter simulation) {
    Objects.requireNonNull(simulation);
    return new HolonomicAutoHooks(
        simulation::getPose2d,
        simulation::getRobotRelativeChassisSpeeds,
        simulation::resetPose,
        simulation::getChassisState3d,
        simulation::getTerrainContactSample,
        simulation::getTractionState);
  }

  public static VisualizationFrame visualizationFrame(
      DriveSimulationAdapter simulation, FieldMarkerProvider fieldMarkerProvider) {
    Objects.requireNonNull(simulation);
    FieldMarkerSample[] markers =
        fieldMarkerProvider == null ? new FieldMarkerSample[0] : fieldMarkerProvider.getFieldMarkers();
    return new VisualizationFrame(
        simulation.getPose3d(),
        simulation.getChassisState3d(),
        simulation.getImuSample(),
        simulation.getTerrainContactSample(),
        simulation.getTractionState(),
        markers);
  }
}
