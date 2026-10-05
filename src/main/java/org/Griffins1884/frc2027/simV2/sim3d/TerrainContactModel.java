package org.Griffins1884.frc2027.simV2.sim3d;

import org.wpilib.math.geometry.Pose2d;

/** Terrain model with explicit clearance/contact semantics for a robot footprint. */
public interface TerrainContactModel extends TerrainModel {
  TerrainContactSample sampleContact(Pose2d robotPose, ChassisFootprint chassisFootprint);
}
