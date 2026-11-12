package org.Griffins1884.frc2027.subsystems.vision;

import lombok.Getter;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.networktables.DoubleSubscriber;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.system.RobotController;

/** IO implementation for a Limelight camera on a game element detection pipeline. */
public class GamePieceVisionIOLimelight implements VisionIO {
  private static final long FLUSH_PERIOD_NANOS = 100_000_000L;

  private final DoubleSubscriber latencySubscriber;
  private final DoubleSubscriber txSubscriber;
  private final DoubleSubscriber tySubscriber;
  @Getter private final CameraConstants cameraConstants;
  private long lastFlushNanos = Long.MIN_VALUE;

  /**
   * Creates a new GamePieceVisionIOLimelight.
   *
   * @param cameraConstants The constants associated with this camera.
   */
  public GamePieceVisionIOLimelight(CameraConstants cameraConstants) {
    this.cameraConstants = cameraConstants;

    var table = NetworkTableInstance.getDefault().getTable(cameraConstants.cameraName());
    latencySubscriber = table.getDoubleTopic("tl").subscribe(0.0);
    txSubscriber = table.getDoubleTopic("tx").subscribe(0.0);
    tySubscriber = table.getDoubleTopic("ty").subscribe(0.0);

    table
        .getDoubleArrayTopic("camerapose_robotspace_set")
        .publish()
        .accept(
            new double[] {
              cameraConstants.robotToCamera().getX(),
              cameraConstants.robotToCamera().getY(),
              cameraConstants.robotToCamera().getZ(),
              cameraConstants.robotToCamera().getRotation().getX(),
              cameraConstants.robotToCamera().getRotation().getY(),
              cameraConstants.robotToCamera().getRotation().getZ()
            });
  }

  @Override
  public void updateInputs(VisionIO.VisionIOInputs inputs) {
    // Update connection status based on whether an update has been seen in the last
    // 250ms
    long nowNanos = RobotController.getTime();
    long lastChangeNanos = latencySubscriber.getLastChange();

    inputs.connected =
        lastChangeNanos > 0
            && nowNanos >= lastChangeNanos
            && nowNanos - lastChangeNanos < 250_000_000L;
    // Update target observation
    inputs.latestTargetObservation =
        new TargetObservation(
            Rotation2d.fromDegrees(txSubscriber.get()), Rotation2d.fromDegrees(tySubscriber.get()));

    if (lastFlushNanos == Long.MIN_VALUE || nowNanos - lastFlushNanos >= FLUSH_PERIOD_NANOS) {
      NetworkTableInstance.getDefault().flush();
      lastFlushNanos = nowNanos;
    }
  }
}
