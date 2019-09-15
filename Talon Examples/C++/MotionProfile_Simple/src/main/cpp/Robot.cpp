#include "Robot.h"
#include "MotionProfile.h"
#include "Instrum.h"

void Robot::RobotInit() 
{
    /* Construct global variables being used */
    _master = new TalonSRX(0);
    _joy = new frc::Joystick(0);
    _bufferedStream = new BufferedTrajectoryPointStream();

    _plotThread = new PlotThread(_master);

    /* Initialize buffer with MotionProfile */
    InitBuffer(kMotionProfile, kMotionProfileSz);
    _state = 0;


    _configuration = new MotionProfileConfiguration();

    _master->ConfigAllSettings(*_configuration);
    _master->SetSensorPhase(true); //Flip this if you need to for your robot
    _master->SetInverted(false);   //Flip this if you need to for your robot
}

void Robot::AutonomousInit() {}
void Robot::AutonomousPeriodic() {}

void Robot::TeleopInit() {}
void Robot::TeleopPeriodic() 
{
    /* get joystick button and stick */
    bool bPrintValues = _joy->GetRawButton(2);
    bool bFireMp = _joy->GetRawButton(1);
    double axis = _joy->GetRawAxis(1);

    /* if button is up, just drive the motor in PercentOutput */
    if (bFireMp == false) {
        _state = 0;
    }

    switch (_state) {
        /* drive master talon normally */
        case 0:
            _master->Set(ControlMode::PercentOutput, axis);
            if (bFireMp == true) {
                /* go to MP logic */
                _state = 1;
            }
            break;

        /* fire the MP, and stop calling set() since that will cancel the MP */
        case 1:
            /* wait for 10 points to buffer in firmware, then transition to MP */
            _master->StartMotionProfile(*_bufferedStream, 10, ControlMode::MotionProfile);
            _state = 2;
            Instrum::PrintLine("MP started");
            break;

        /* wait for MP to finish */
        case 2:
            if (_master->IsMotionProfileFinished()) {
                Instrum::PrintLine("MP finished");
                _state = 3;
            }
            break;

        /* MP is finished, nothing to do */
        case 3:
            break;
    }

    /* print MP values */
    Instrum::Loop(bPrintValues, _master);
}

void Robot::TestInit() {}
void Robot::TestPeriodic() {}


void Robot::InitBuffer(const double profile[][3], int totalCnt)
{
    bool forward = true; // set to false to drive in opposite direction of profile (not really needed
                         // since you can use negative numbers in profile).

    TrajectoryPoint point; // temp for for loop, since unused params are initialized
                           // automatically, you can alloc just one

    /* clear the buffer, in case it was used elsewhere */
    _bufferedStream->Clear();

    /* Insert every point into buffer, no limit on size */
    for (int i = 0; i < totalCnt; ++i) {

        double direction = forward ? +1 : -1;
        double positionRot = profile[i][0];
        double velocityRPM = profile[i][1];
        int durationMilliseconds = (int) profile[i][2];

        /* for each point, fill our structure and pass it to API */
        point.timeDur = durationMilliseconds;
        point.position = direction * positionRot * 4096; // Convert Revolutions to
                                                         // Units
        point.velocity = direction * velocityRPM * 4096 / 600.0; // Convert RPM to
                                                                 // Units/100ms
        point.auxiliaryPos = 0;
        point.auxiliaryVel = 0;
        point.profileSlotSelect0 = 0; /* which set of gains would you like to use [0,3]? */
        point.profileSlotSelect1 = 0; /* which set of gains would you like to use [0,3]? */
        point.zeroPos = (i == 0); /* set this to true on the first point */
        point.isLastPoint = ((i + 1) == totalCnt); /* set this to true on the last point */
        point.arbFeedFwd = 0; /* you can add a constant offset to add to PID[0] output here */

        _bufferedStream->Write(point);
    }
}

#ifndef RUNNING_FRC_TESTS
int main() { return frc::StartRobot<Robot>(); }
#endif
