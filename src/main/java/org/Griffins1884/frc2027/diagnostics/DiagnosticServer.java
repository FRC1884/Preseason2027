package org.Griffins1884.frc2027.diagnostics;

import com.ctre.phoenix6.CANBus;
import com.ctre.phoenix6.hardware.TalonFX;
import org.Griffins1884.frc2027.CanIDConstants;
import org.json.simple.JSONArray;

import java.util.*;

public class DiagnosticServer {
    public JSONArray allMotorData(){
        Map<String, TalonFX> data = new HashMap<>();

        Map<String, Integer> canIDs = new HashMap<>();


        data.put("Intake Pivot 1", DiagnosticCommands.initTalonFX(CanIDConstants.INTAKE_PIVOT_IDS[0],CanIDConstants.INTAKE_PIVOT_BUS));
        data.put("Intake Pivot 2", DiagnosticCommands.initTalonFX(CanIDConstants.INTAKE_PIVOT_IDS[1],CanIDConstants.INTAKE_PIVOT_BUS));

        data.put("Intake Rollers", DiagnosticCommands.initTalonFX(CanIDConstants.INTAKE_IDS[0],CanIDConstants.INTAKE_BUS));

        data.put("Turret", DiagnosticCommands.initTalonFX(CanIDConstants.TURRET_ID,CanIDConstants.TURRET_BUS));

        data.put("Shooter 1", DiagnosticCommands.initTalonFX(CanIDConstants.SHOOTER_IDS[0],CanIDConstants.SHOOTER_BUS));
        data.put("Shooter 2", DiagnosticCommands.initTalonFX(CanIDConstants.SHOOTER_IDS[1],CanIDConstants.SHOOTER_BUS));

        data.put("Shooter Pivot", DiagnosticCommands.initTalonFX(CanIDConstants.SHOOTER_PIVOT_IDS[0],CanIDConstants.SHOOTER_BUS));

        data.put("Indexer", DiagnosticCommands.initTalonFX(CanIDConstants.INDEXER_IDS[0],CanIDConstants.INDEXER_BUS));

        data.put("Swerve FLR", DiagnosticCommands.initTalonFX(CanIDConstants.FLR_ID, new CANBus(CanIDConstants.SWERVE)));
        data.put("Swerve FRR", DiagnosticCommands.initTalonFX(CanIDConstants.FRR_ID, new CANBus(CanIDConstants.SWERVE)));
        data.put("Swerve BLR", DiagnosticCommands.initTalonFX(CanIDConstants.BLR_ID, new CANBus(CanIDConstants.SWERVE)));
        data.put("Swerve BRR", DiagnosticCommands.initTalonFX(CanIDConstants.BRR_ID, new CANBus(CanIDConstants.SWERVE)));

        data.put("Swerve FLD", DiagnosticCommands.initTalonFX(CanIDConstants.FLD_ID, new CANBus(CanIDConstants.SWERVE)));
        data.put("Swerve FRD", DiagnosticCommands.initTalonFX(CanIDConstants.FRD_ID, new CANBus(CanIDConstants.SWERVE)));
        data.put("Swerve BLD", DiagnosticCommands.initTalonFX(CanIDConstants.BLR_ID, new CANBus(CanIDConstants.SWERVE)));
        data.put("Swerve BRD", DiagnosticCommands.initTalonFX(CanIDConstants.BLR_ID, new CANBus(CanIDConstants.SWERVE)));

        data.forEach((name, motor) -> {
            canIDs.put(name, motor.getDeviceID());
        });

        return new JSONArray(canIDs.entrySet());
    }


}
