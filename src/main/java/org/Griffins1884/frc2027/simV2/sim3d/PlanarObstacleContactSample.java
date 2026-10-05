package org.Griffins1884.frc2027.simV2.sim3d;

import org.wpilib.math.geometry.Translation2d;

/** Planar hard-contact sample against a vertically extruded blocker. */
public record PlanarObstacleContactSample(
    TerrainFeature feature,
    Translation2d outwardNormal,
    double penetrationMeters,
    double obstacleHeightMeters) {}
