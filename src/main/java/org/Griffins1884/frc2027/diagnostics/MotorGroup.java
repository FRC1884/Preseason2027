package org.Griffins1884.frc2027.diagnostics;

import com.ctre.phoenix6.CANBus;
import com.ctre.phoenix6.hardware.TalonFX;
import java.util.HashMap;
import java.util.Map;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.ParallelCommandGroup;

public class MotorGroup {
  private Map<TalonFX, Boolean> motorGroup = new HashMap<>();

  public MotorGroup(int[] canIDs, boolean[] activeMotors, CANBus bus) {
    for (int i = 0; i < canIDs.length && i < activeMotors.length; i++) {
      motorGroup.put(DiagnosticCommands.initTalonFX(canIDs[i], bus), activeMotors[i]);
    }
  }

  public void runCommandGroupVoltage(double voltage) {
    ParallelCommandGroup group = new ParallelCommandGroup();

    motorGroup.forEach((motor, shouldRun) -> {
      if (shouldRun) {
        Command command = Commands.runOnce(() -> motor.setVoltage(voltage));
        group.addCommands(command);
      }
    });

    group.execute();
  }

  public void runCommandGroupThrottle(double throttle) {
    ParallelCommandGroup group = new ParallelCommandGroup();

    motorGroup.forEach((motor, shouldRun) -> {
      if (shouldRun) {
        Command command = Commands.runOnce(() -> motor.setThrottle(throttle));
        group.addCommands(command);
      }
    });

    group.execute();
  }
}
