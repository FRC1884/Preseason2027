package org.Griffins1884.frc2027.subsystems.intakeAlgae;

import org.Griffins1884.frc2027.util.LoggedTunableNumber;
import org.wpilib.math.system.DCMotor;

public final class IntakeAlgaeConstants {
  public static final int CURRENT_LIMIT_AMPS = 40;
  public static final boolean[] INVERTED = {false, true};
  public static final boolean BRAKE = false;
  public static final double REDUCTION = 0.0;

  public static final LoggedTunableNumber FORWARD_VOLTS =
      new LoggedTunableNumber("IntakeAlgae/Forward", 4);
  public static final LoggedTunableNumber REVERSE_VOLTS =
      new LoggedTunableNumber("IntakeAlgae/Reverse", -4);

  public static final double MAX_VOLTAGE = 12.0;

  public static final DCMotor INTAKE_MOTORS = DCMotor.getKrakenX60Foc(2);
}
