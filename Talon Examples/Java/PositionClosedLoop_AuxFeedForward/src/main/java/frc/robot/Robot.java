/**
 * Phoenix Software License Agreement
 *
 * Copyright (C) Cross The Road Electronics.  All rights
 * reserved.
 * 
 * Cross The Road Electronics (CTRE) licenses to you the right to 
 * use, publish, and distribute copies of CRF (Cross The Road) firmware files (*.crf) and 
 * Phoenix Software API Libraries ONLY when in use with CTR Electronics hardware products
 * as well as the FRC roboRIO when in use in FRC Competition.
 * 
 * THE SOFTWARE AND DOCUMENTATION ARE PROVIDED "AS IS" WITHOUT
 * WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING WITHOUT
 * LIMITATION, ANY WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, TITLE AND NON-INFRINGEMENT. IN NO EVENT SHALL
 * CROSS THE ROAD ELECTRONICS BE LIABLE FOR ANY INCIDENTAL, SPECIAL, 
 * INDIRECT OR CONSEQUENTIAL DAMAGES, LOST PROFITS OR LOST DATA, COST OF
 * PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY OR SERVICES, ANY CLAIMS
 * BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY DEFENSE
 * THEREOF), ANY CLAIMS FOR INDEMNITY OR CONTRIBUTION, OR OTHER
 * SIMILAR COSTS, WHETHER ASSERTED ON THE BASIS OF CONTRACT, TORT
 * (INCLUDING NEGLIGENCE), BREACH OF WARRANTY, OR OTHERWISE
 */

/**
 * Description:
 * The PositionClosedLoop_AuxFeedForward example demonstrates the new Talon/Victor auxiliary
 * and remote features usedto perform complex closed loops. This example has the robot performing 
 * Position Closed Loop with an arbitrary Feed Forward to request more or less output.
 * 
 * This example uses:
 * - 2x Quad Encoders, One on both sides of robot for Primary Closed Loop on Position
 * A Talon/Victor calculates the distance by taking the sum of both sensors and dividing it by 2.
 * 
 * This example has two modes of operation, which can be switched between with Button 2.
 * 1.) Arcade Drive
 * 2.) Position Closed Loop with Quadrature Encoders and Feed Forward
 * 
 * Controls:
 * Button 1: When pressed, zero heading. Set current Quadrature Encoders' positions to 0.
 * Button 2: When pressed, toggle between Arcade Drive and Position Closed Loop with Feed Forward
 * Left Joystick Y-Axis:
 * 	+ Arcade Drive: Drive robot forward and reverse
 * 	+ Position Closed Loop: Servo robot forward and reverse [-6, 6] wheel rotations
 * Right Joystick X-Axis:
 * 	+ Arcade Drive: Turn robot right and left
 * 	+ Position Closed Loop: Request more or less output on Closed Loop [-10, 10] %
 * 
 * Gains for Position Closed Loop may need to be adjusted in Constants.java
 * 
 * Supported Version:
 * - Talon SRX: 4.00
 * - Victor SPX: 4.00
 * - Pigeon IMU: 4.00
 * - CANifier: 4.00
 */
package frc.robot;

import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.Joystick;

import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.RemoteSensorSource;
import com.ctre.phoenix.motorcontrol.SensorTerm;
import com.ctre.phoenix.motorcontrol.StatusFrame;
import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.DemandType;
import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.can.TalonSRX;

public class Robot extends TimedRobot {
	/** Hardware */
	TalonSRX _leftMaster = new TalonSRX(2);
	TalonSRX _rightMaster = new TalonSRX(1);
	Joystick _gamepad = new Joystick(0);
	
	/** Latched values to detect on-press events for buttons and POV */
	boolean[] _btns = new boolean[Constants.kNumButtonsPlusOne];
	boolean[] btns = new boolean[Constants.kNumButtonsPlusOne];
	
	/** Tracking variables */
	boolean _firstCall = false;
	boolean _state = false;

	@Override
	public void robotInit() {
		/* Not used in this project */
	}
	
	@Override
	public void teleopInit(){
		/* Disable all motors */
		_rightMaster.set(ControlMode.PercentOutput, 0);
        _leftMaster.set(ControlMode.PercentOutput,  0);
        
		/* Factory Default all hardware to prevent unexpected behavior */
        _rightMaster.configFactoryDefault();
        _leftMaster.configFactoryDefault();
		
		/* Set neutral modes */
		_leftMaster.setNeutralMode(NeutralMode.Brake);
		_rightMaster.setNeutralMode(NeutralMode.Brake);
		
		/** Feedback Sensor Configuration */
		
		/* Configure the left Talon's selected sensor as local QuadEncoder */
		_leftMaster.configSelectedFeedbackSensor(	FeedbackDevice.QuadEncoder,				// Local Feedback Source
													Constants.PID_PRIMARY,					// PID Slot for Source [0, 1]
													Constants.kTimeoutMs);					// Configuration Timeout

		/* Configure the Remote Talon's selected sensor as a remote sensor for the right Talon */
		_rightMaster.configRemoteFeedbackFilter(_leftMaster.getDeviceID(),					// Device ID of Source
												RemoteSensorSource.TalonSRX_SelectedSensor,	// Remote Feedback Source
												Constants.REMOTE_0,							// Source number [0, 1]
												Constants.kTimeoutMs);						// Configuration Timeout
		
		/* Setup Sum signal to be used for Distance */
		_rightMaster.configSensorTerm(SensorTerm.Sum0, FeedbackDevice.RemoteSensor0, Constants.kTimeoutMs);				// Feedback Device of Remote Talon
		_rightMaster.configSensorTerm(SensorTerm.Sum1, FeedbackDevice.CTRE_MagEncoder_Relative, Constants.kTimeoutMs);	// Quadrature Encoder of current Talon
		
		/* Configure Sum [Sum of both QuadEncoders] to be used for Primary PID Index */
		_rightMaster.configSelectedFeedbackSensor(	FeedbackDevice.SensorSum, 
													Constants.PID_PRIMARY,
													Constants.kTimeoutMs);
		
		/* Configure output and sensor direction */
		_leftMaster.setInverted(false);
		_leftMaster.setSensorPhase(true);
		_rightMaster.setInverted(true);
		_rightMaster.setSensorPhase(true);
		
		/* Set status frame periods to ensure we don't have stale data */
		_rightMaster.setStatusFramePeriod(StatusFrame.Status_12_Feedback1, 20, Constants.kTimeoutMs);
		_rightMaster.setStatusFramePeriod(StatusFrame.Status_13_Base_PIDF0, 20, Constants.kTimeoutMs);
		_leftMaster.setStatusFramePeriod(StatusFrame.Status_2_Feedback0, 5, Constants.kTimeoutMs);

		/* Configure neutral deadband */
		_rightMaster.configNeutralDeadband(Constants.kNeutralDeadband, Constants.kTimeoutMs);
		_leftMaster.configNeutralDeadband(Constants.kNeutralDeadband, Constants.kTimeoutMs);

		/**
		 * Max out the peak output (for all modes).  
		 * However you can limit the output of a given PID object with configClosedLoopPeakOutput().
		 */
		_leftMaster.configPeakOutputForward(+1.0, Constants.kTimeoutMs);
        _leftMaster.configPeakOutputReverse(-1.0, Constants.kTimeoutMs);
		_rightMaster.configPeakOutputForward(+1.0, Constants.kTimeoutMs);
		_rightMaster.configPeakOutputReverse(-1.0, Constants.kTimeoutMs);

		/* FPID Gains for distance servo */
		_rightMaster.config_kP(Constants.kSlot_Distanc, Constants.kGains_Distanc.kP, Constants.kTimeoutMs);
		_rightMaster.config_kI(Constants.kSlot_Distanc, Constants.kGains_Distanc.kI, Constants.kTimeoutMs);
		_rightMaster.config_kD(Constants.kSlot_Distanc, Constants.kGains_Distanc.kD, Constants.kTimeoutMs);
		_rightMaster.config_kF(Constants.kSlot_Distanc, Constants.kGains_Distanc.kF, Constants.kTimeoutMs);
		_rightMaster.config_IntegralZone(Constants.kSlot_Distanc, Constants.kGains_Distanc.kIzone, Constants.kTimeoutMs);
		_rightMaster.configClosedLoopPeakOutput(Constants.kSlot_Distanc, Constants.kGains_Distanc.kPeakOutput, Constants.kTimeoutMs);
		_rightMaster.configAllowableClosedloopError(Constants.kSlot_Distanc, 0, Constants.kTimeoutMs);

		/**
		 * 1ms per loop.  PID loop can be slowed down if need be.
		 * For example,
		 * - if sensor updates are too slow
		 * - sensor deltas are very small per update, so derivative error never gets large enough to be useful.
		 * - sensor movement is very slow causing the derivative error to be near zero.
		 */
        int closedLoopTimeMs = 1;
        _rightMaster.configClosedLoopPeriod(0, closedLoopTimeMs, Constants.kTimeoutMs);
        _rightMaster.configClosedLoopPeriod(1, closedLoopTimeMs, Constants.kTimeoutMs);
		
		/* Initialize */
		_firstCall = true;
		_state = false;
		zeroSensors();
	}
	
	@Override
	public void teleopPeriodic() {
		/* Gamepad processing */
		double forward = -1 * _gamepad.getY();  // positive stick => forward
		double turn = _gamepad.getTwist();      // positive stick => right
		forward = Deadband(forward);
		turn = Deadband(turn);
	
		/* Button processing for state toggle and sensor zeroing */
		getButtons(btns, _gamepad);
		if(btns[2] && !_btns[2]){
			_state = !_state; 		// Toggle State
			_firstCall = true;		// State change, do first call operation
		}else if (btns[1] && !_btns[1]) {
			zeroSensors();			// Zero Sensors
		}	
		System.arraycopy(btns, 0, _btns, 0, Constants.kNumButtonsPlusOne);
				
		if(!_state){
			if (_firstCall)
				System.out.println("This is Arcade drive.\n");
			
			_leftMaster.set(ControlMode.PercentOutput, forward, DemandType.ArbitraryFeedForward, +turn);
			_rightMaster.set(ControlMode.PercentOutput, forward, DemandType.ArbitraryFeedForward, -turn);
		}
		else{
			if (_firstCall) {
				System.out.println("This is Position Closed Loop with an Arbitrary Feed Forward.");
				System.out.println("Servo [-6, 6] rotations while having the ability to add a FeedForward with joyX.");
				zeroSensors();
				
				/* Determine which slot affects which PID */
				_rightMaster.selectProfileSlot(Constants.kSlot_Distanc, Constants.PID_PRIMARY);
			}
			
			/* Calculate targets from gamepad inputs */
			double target_sensorUnits = forward * Constants.kSensorUnitsPerRotation * Constants.kRotationsToTravel;
			double feedFwdTerm = turn * 0.10;	// Pecent to add to Closed Loop Output
			
			/* Configured for Position Closed Loop on Quad Encoders' Sum and Arbitrary FeedForward on joyX */
			_rightMaster.set(ControlMode.Position, target_sensorUnits, DemandType.ArbitraryFeedForward, feedFwdTerm);
			_leftMaster.follow(_rightMaster);
		}
		
		_firstCall = false;
	}
	
	/* Zero quadrature encoders on Talons */
	void zeroSensors() {
		_leftMaster.getSensorCollection().setQuadraturePosition(0, Constants.kTimeoutMs);
		_rightMaster.getSensorCollection().setQuadraturePosition(0, Constants.kTimeoutMs);
		System.out.println("[Quadrature Encoders] All sensors are zeroed.\n");
	}
	
	/** Deadband 5 percent, used on the gamepad */
	double Deadband(double value) {
		/* Upper deadband */
		if (value >= +0.05) 
			return value;
		
		/* Lower deadband */
		if (value <= -0.05)
			return value;
		
		/* Outside deadband */
		return 0;
	}
	
	/** Gets all buttons from gamepad */
	void getButtons(boolean[] btns, Joystick gamepad) {
		for (int i = 1; i < Constants.kNumButtonsPlusOne; ++i) {
			btns[i] = gamepad.getRawButton(i);
		}
	}
}
