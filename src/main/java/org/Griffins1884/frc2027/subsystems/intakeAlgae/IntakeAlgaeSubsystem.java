package org.Griffins1884.frc2027.subsystems.intakeAlgae;

import java.util.function.DoubleSupplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.Griffins1884.frc2027.mechanisms.RobotMechanismDefinitions;
import org.Griffins1884.frc2027.mechanisms.rollers.VoltageRollerMechanism;
import org.Griffins1884.frc2027.util.LoggedTunableNumber;

@Setter
@Getter
public class IntakeAlgaeSubsystem
    extends VoltageRollerMechanism<IntakeAlgaeSubsystem.IntakeAlgaeGoal> {
  @RequiredArgsConstructor
  @Getter
  public enum IntakeAlgaeGoal implements VoltageRollerMechanism.VoltageGoal {
    IDLING(() -> 0.0),
    FORWARD(IntakeAlgaeConstants.FORWARD_VOLTS::get),
    REVERSE(IntakeAlgaeConstants.REVERSE_VOLTS::get),
    STOW(() -> 4),
    TESTING(new LoggedTunableNumber("IntakeAlgae/Testing", 0.0));

    private final DoubleSupplier voltageSupplier;

    public DoubleSupplier getVoltageSupplier() {
      return voltageSupplier;
    }
  }

  @Setter private IntakeAlgaeGoal goal = IntakeAlgaeGoal.IDLING;

  public IntakeAlgaeSubsystem(String name, IntakeAlgaeIO io) {
    super(
        name,
        RobotMechanismDefinitions.INTAKE,
        io,
        new VoltageRollerConfig(IntakeAlgaeConstants.MAX_VOLTAGE));
  }
}
