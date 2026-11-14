package org.Griffins1884.frc2027.subsystems.vision;


import org.Griffins1884.frc2027.subsystems.swerve.SwerveSubsystem;
import org.photonvision.PhotonCamera;
import org.photonvision.simulation.PhotonCameraSim;
import org.photonvision.simulation.SimCameraProperties;
import org.photonvision.simulation.VisionSystemSim;
import org.wpilib.fields.Field;
import org.wpilib.fields.Fields;


import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.system.RobotController;


public class VisionIOSim extends AprilTagVisionIOLimelight {

    //Vision Simulation:
    private static VisionSystemSim visionSim;
    private final PhotonCamera camera;
    private final PhotonCameraSim cameraSim;

    public VisionIOSim(CameraConstants cameraConstants, SwerveSubsystem drive){
        if (visionSim == null){
            visionSim = new VisionSystemSim("main");
            Field tagLayout = Field.loadField(Fields.DEFAULT_FIELD);
            visionSim.addAprilTags(tagLayout);
        }

        // Just does all the same things as the APrilTagVisionIOLimelight.java
        super(cameraConstants, drive);

        camera = new PhotonCamera(cameraConstants.cameraName());
        
        SimCameraProperties cameraProp = new SimCameraProperties();
        cameraProp.setCalibration(960, 720, Rotation2d.fromDegrees(90)); // Resolution & diagonal FOV
        cameraProp.setCalibError(0.25, 0.08); // Average & std dev pixel noise
        cameraProp.setFPS(30);
        cameraProp.setAvgLatencyMs(35);
        cameraProp.setLatencyStdDevMs(5);

        cameraSim = new PhotonCameraSim(camera, cameraProp);
        visionSim.addCamera(cameraSim, cameraConstants.robotToCamera());

    }

    @Override
    public void updateInputs(VisionIOInputs inputs) {
        inputs.standardDeviations = AprilTagVisionConstants.getLimelightStandardDeviations();
        inputs.connected = true; //Since it's a simulation, I'll assume they are connected


        visionSim.update(poseSupplier.get())

    }

    
}