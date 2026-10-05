package org.Griffins1884.frc2027.simV2.sim3d;

import org.wpilib.math.geometry.Translation2d;

/**
 * Optional GriffinSim-side extension for field models that can provide hard 2D blocker contacts.
 *
 * <p>This is intended for rigid-body backends that need lateral collision normals and penetration
 * depth against hubs, trench returns, towers, or similar field structures extruded vertically from
 * the carpet.
 */
public interface PlanarObstacleContactModel {
  PlanarObstacleContactSample[] samplePlanarObstacleContacts(
      Translation2d point, double contactHeightMeters);
}
