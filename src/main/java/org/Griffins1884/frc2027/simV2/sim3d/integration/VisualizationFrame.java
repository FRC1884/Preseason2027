package org.Griffins1884.frc2027.simV2.sim3d.integration;

import org.wpilib.math.geometry.Pose3d;
import org.Griffins1884.frc2027.simV2.sim3d.ChassisState3d;
import org.Griffins1884.frc2027.simV2.sim3d.SimImuSample;
import org.Griffins1884.frc2027.simV2.sim3d.SwerveTractionState;
import org.Griffins1884.frc2027.simV2.sim3d.TerrainContactSample;

/** Snapshot for visualization and telemetry publishers. */
public record VisualizationFrame(
    Pose3d robotPose,
    ChassisState3d chassisState3d,
    SimImuSample imuSample,
    TerrainContactSample terrainContactSample,
    SwerveTractionState tractionState,
    FieldMarkerSample[] fieldMarkers) {}
