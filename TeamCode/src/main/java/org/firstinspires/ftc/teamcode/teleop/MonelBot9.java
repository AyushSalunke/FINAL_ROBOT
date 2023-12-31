package org.firstinspires.ftc.teamcode.teleop;

import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.qualcomm.hardware.bosch.BHI260IMU;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.AnalogInput;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DigitalChannel;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;
import org.firstinspires.ftc.teamcode.angle_pid.PIDConstants;
import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;
import org.firstinspires.ftc.teamcode.drive.TwoWheelTrackingLocalizer;
import org.firstinspires.ftc.teamcode.drive.advanced.PoseStorage;
import org.firstinspires.ftc.teamcode.subsystems.Arm;
import org.firstinspires.ftc.teamcode.subsystems.ArmV2;
import org.firstinspires.ftc.teamcode.subsystems.DriveTrain;
import org.firstinspires.ftc.teamcode.subsystems.Drone;
import org.firstinspires.ftc.teamcode.subsystems.Hanger;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.Slider;
import org.firstinspires.ftc.teamcode.trajectorysequence.TrajectorySequence;

import java.util.List;

@TeleOp(group = "Robot Main")
@Config
@Disabled
public class MonelBot9 extends LinearOpMode {
    SampleMecanumDrive drive = null;
    DriveTrain drivetrain = null;
    Slider slider = null;
    ArmV2 arm = null;
    Hanger hanger = null;
    Intake intake = null;
    Drone drone = null;
    public static DcMotorEx leftFront, leftRear, rightFront, rightRear;
    ElapsedTime inputTimer, outputTimer, angle_timer;
    public static double
            armServoOnePos, armServoTwoPos, wristServoPos, deliveryServoPos, armSliderServoPos;
    public static double
            gripperServoPos, intakeArmServoPos, intakeWristServoPos, crankServoPos;
    public static int levelZero = 0, levelOne = 200, levelTwo = 400, levelThree = 500;
    boolean
            armToggle = false, deliveryToggleOne = false, deliveryToggleTwo = false, intakeToggle = false, crankToggle = false, driveToggle = false, angleToggle1 = false, angleToggle2=false,stackFlag = false;;
    public static int intakeCounter, outtakeCounter,sliderCounter =0;
    public static double
            lifter_posL = 0, lifter_posR = 0, error_lifter, error_diff, error_int, error_lifterR, error_diffR, error_intR, errorprev, errorprevR, output_lifter, output_lifterR, output_power, target, dropVal;

    public static double kp = 4, ki, kd = 1.7;
    double Kp = PIDConstants.Kp, Ki = PIDConstants.Ki, Kd = PIDConstants.Kd;
    private double lastError = 0, integralSum = 0;;
    private BHI260IMU imu;
    public enum IntakeState {
        INTAKE_START,
        INTAKE_EXTEND,
        INTAKE_GRIP,
        INTAKE_RETRACT,
        INTAKE_INPUT,
        INTAKE_FINAL
    };
    public enum OuttakeState{
        OUTTAKE_START,
        OUTTAKE_PUSH,
        OUTTAKE_OPEN,
        OUTTAKE_OUTPUT,
        OUTTAKE_SLIDER,
        OUTTAKE_FINAL
    };
    IntakeState inputState = IntakeState.INTAKE_START;
    OuttakeState outputState = OuttakeState.OUTTAKE_START;

    @Override
    public void runOpMode() throws InterruptedException {
        leftFront = hardwareMap.get(DcMotorEx.class, "leftFront");
        leftRear = hardwareMap.get(DcMotorEx.class, "leftRear");
        rightRear = hardwareMap.get(DcMotorEx.class, "rightRear");
        rightFront = hardwareMap.get(DcMotorEx.class, "rightFront");

        leftFront.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        rightFront.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        leftRear.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        rightRear.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        // TODO: reverse any motors using DcMotor.setDirection()
        leftFront.setDirection(DcMotorEx.Direction.FORWARD);
        rightRear.setDirection(DcMotorEx.Direction.REVERSE);
        rightFront.setDirection(DcMotorEx.Direction.REVERSE);
        leftRear.setDirection(DcMotorEx.Direction.FORWARD);

        Gamepad currentGamepad1 = new Gamepad();
        Gamepad currentGamepad2 = new Gamepad();

        Gamepad previousGamepad1 = new Gamepad();
        Gamepad previousGamepad2 = new Gamepad();

        drive = new SampleMecanumDrive(hardwareMap);
        drivetrain = new DriveTrain(hardwareMap, telemetry);
        slider = new Slider(hardwareMap, telemetry);
        arm = new ArmV2(hardwareMap, telemetry);
        hanger = new Hanger(hardwareMap, telemetry);
        intake = new Intake(hardwareMap, telemetry);
        drone =new Drone(hardwareMap, telemetry);

        inputTimer = new ElapsedTime();
        outputTimer = new ElapsedTime();
        angle_timer = new ElapsedTime();

        Pose2d startPose = new Pose2d(0, 0, Math.toRadians(180));
        drive.setPoseEstimate(startPose);

        List<LynxModule> allHubs = hardwareMap.getAll(LynxModule.class);

        for (LynxModule hub : allHubs) {
            hub.setBulkCachingMode(LynxModule.BulkCachingMode.AUTO);
        }

        // Retrieve the IMU from the hardware map
        IMU imu = hardwareMap.get(BHI260IMU.class, "imu");
        IMU.Parameters parameters = new IMU.Parameters(new RevHubOrientationOnRobot(
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.BACKWARD));
        imu.initialize(parameters);

        AnalogInput intakeArmAnalogInput = hardwareMap.get(AnalogInput.class, "intakeArmAnalogInput");
        AnalogInput intakeWristAnalogInput = hardwareMap.get(AnalogInput.class, "intakeWristAnalogInput");
        AnalogInput crankAnalogInput = hardwareMap.get(AnalogInput.class, "crankAnalogInput");
        AnalogInput wristAnalogInput = hardwareMap.get(AnalogInput.class, "wristAnalogInput");
        AnalogInput armOneAnalogInput = hardwareMap.get(AnalogInput.class, "armOneAnalogInput");
        AnalogInput armTwoAnalogInput = hardwareMap.get(AnalogInput.class, "armTwoAnalogInput");

        DigitalChannel beamBreaker = hardwareMap.get(DigitalChannel.class, "beamBreaker");
        beamBreaker.setMode(DigitalChannel.Mode.INPUT);

        TwoWheelTrackingLocalizer myLocalizer = new TwoWheelTrackingLocalizer(hardwareMap, drive);
        myLocalizer.setPoseEstimate(PoseStorage.currentPose);

        while (opModeInInit()){
            ArmV2.SetArmPosition(0.15,0.16);
            Intake.crankServo.setPosition(0.69);
            Intake.intakeArmServo.setPosition(0.5);
            Intake.intakeWristServo.setPosition(0.66);
            ArmV2.DropPixel(0.95);
            Drone.initialPos();
            Hanger.hangerServoOne.setPosition(0.75);
            Hanger.hangerServoTwo.setPosition(0.25);
            Intake.gripperServo.setPosition(1);
            ArmV2.SliderLink(0.95);
            inputTimer.reset();
            outputTimer.reset();
            intakeCounter = 0;
        }

        waitForStart();

        while (opModeIsActive()) {
            previousGamepad1.copy(currentGamepad1);
            previousGamepad2.copy(currentGamepad2);

            currentGamepad1.copy(gamepad1);
            currentGamepad2.copy(gamepad2);

            double intakeArmPosition = intakeArmAnalogInput.getVoltage() / 3.3 * 360;
            double intakeWristPosition = intakeWristAnalogInput.getVoltage() / 3.3 * 360;
            double crankPosition = crankAnalogInput.getVoltage() / 3.3 * 360;
            double wristPosition = wristAnalogInput.getVoltage() / 3.3 * 360;
            double armOnePosition = armOneAnalogInput.getVoltage() / 3.3 * 360;
            double armTwoPosition = armTwoAnalogInput.getVoltage() / 3.3 * 360;

            double y = -gamepad1.left_stick_y; // Remember, Y stick value is reversed
            double x = gamepad1.left_stick_x;
            double rx = gamepad1.right_stick_x;

            if (currentGamepad1.start && !previousGamepad1.start) {
                imu.resetYaw();
            }

            // Main teleop loop goes here

            //drivetrain ---------------------------------------------------------------------------
            double botHeading = imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.RADIANS);
            double rotX = x * Math.cos(-botHeading) - y * Math.sin(-botHeading);
            double rotY = x * Math.sin(-botHeading) + y * Math.cos(-botHeading);

            rotX = rotX * 1.1;

            double denominator = Math.max(Math.abs(rotY) + Math.abs(rotX) + Math.abs(rx), 1);
            double frontLeftPower = (rotY + rotX + rx) / denominator;
            double backLeftPower = (rotY - rotX + rx) / denominator;
            double frontRightPower = (rotY - rotX - rx) / denominator;
            double backRightPower = (rotY + rotX - rx) / denominator;

//            DriveTrain.setPower(frontLeftPower, backLeftPower, frontRightPower, backRightPower);
            leftFront.setPower(frontLeftPower);
            leftRear.setPower(backLeftPower);
            rightFront.setPower(frontRightPower);
            rightRear.setPower(backRightPower);

            myLocalizer.update();

            // Retrieve your pose
            Pose2d myPose = myLocalizer.getPoseEstimate();

            double turn0 = angleWrap(Math.toRadians(0) - botHeading);
            double turn270 = angleWrap(Math.toRadians(270) - botHeading);
            if (currentGamepad1.left_trigger > 0.5 && !(previousGamepad1.left_trigger > 0.5)){
                drive.turn(turn0);
            }
            if (currentGamepad1.right_trigger > 0.5 && !(previousGamepad1.right_trigger > 0.5)){
                drive.turn(turn270);
            }
            //--------------------------------------------------------------------------------------

            //Intake Sequence
            switch (inputState) {
                case INTAKE_START:
                    //waiting for input
                    if (currentGamepad1.left_bumper && !previousGamepad1.left_bumper && (intakeCounter == 0)) {
                        ArmV2.wristServo.setPosition(0.16);
                        ArmV2.SetArm(0.20);
                        ArmV2.SetArm(0.25);
                        ArmV2.DropPixel(0.95);
                        if (inputTimer.milliseconds() >= 800) {
                            Intake.intakeArmServo.setPosition(0.4);
                            Intake.intakeWristServo.setPosition(0.495);
                            Intake.IntakePixel(1);
                            inputTimer.reset();
                            inputState = IntakeState.INTAKE_EXTEND;
                        }
                    }
                    if (currentGamepad1.left_bumper && !previousGamepad1.left_bumper && (intakeCounter == 2)) {
                        Intake.IntakePixel(1);
                        ArmV2.SetArm(0.20);
                        ArmV2.wristServo.setPosition(0.16);
                        ArmV2.SetArm(0.25);
                        ArmV2.DropPixel(0.75);
                        if (inputTimer.milliseconds() >= 800) {
                            inputTimer.reset();
                            inputState = IntakeState.INTAKE_EXTEND;
                        }
                    }
                    break;
                case INTAKE_EXTEND:
                    Intake.CrankPosition(0.5);
                    if (inputTimer.milliseconds() >= 200) { //inputTimer.milliseconds() >= 200
                        inputTimer.reset();
                        inputState = IntakeState.INTAKE_GRIP;
                    }
                    break;
                case INTAKE_GRIP:
                    if (!intakeToggle) {
                        if (!beamBreaker.getState()) {
                            Intake.intakeArmServo.setPosition(0.4);
                            Intake.intakeWristServo.setPosition(0.48);
                            TrajectorySequence IntakePixel = drive.trajectorySequenceBuilder(startPose)
                                    .addTemporalMarker(() -> {
                                        Intake.CrankPosition(0.35);
                                    })
                                    .UNSTABLE_addTemporalMarkerOffset(0.1, () -> {
                                        Intake.IntakePixel(0.8);
                                    })
                                    .waitSeconds(0.3)
                                    .build();
                            drive.followTrajectorySequence(IntakePixel);
                            drive.update();
                            if (inputTimer.milliseconds() >= 800) { // 500 //800
                                inputTimer.reset();
                                inputState = IntakeState.INTAKE_RETRACT;
                            }
                        }
                    }
                    if (intakeToggle) {
                        if (currentGamepad1.left_bumper && !previousGamepad1.left_bumper) {
                            Intake.intakeArmServo.setPosition(0.4);
                            Intake.intakeWristServo.setPosition(0.48);
                            TrajectorySequence IntakePixel = drive.trajectorySequenceBuilder(startPose)
                                    .addTemporalMarker(() -> {
                                        Intake.CrankPosition(0.35);
                                    })
                                    .UNSTABLE_addTemporalMarkerOffset(0.1, () -> {
                                        Intake.IntakePixel(0.8);
                                    })
                                    .waitSeconds(0.3)
                                    .build();
                            drive.followTrajectorySequence(IntakePixel);
                            drive.update();
                            if (inputTimer.milliseconds() >= 800) { // 800
                                inputTimer.reset();
                                inputState = IntakeState.INTAKE_RETRACT;
                            }
                        }
                    }
                    if (beamBreaker.getState() && inputTimer.milliseconds() >= 7000) {
                        TrajectorySequence CancelIntakePixel = drive.trajectorySequenceBuilder(startPose)
                                .addTemporalMarker(() -> {
                                    Intake.intakeArmServo.setPosition(0.5);
                                    Intake.intakeWristServo.setPosition(0.66);
                                })
                                .waitSeconds(0.2)
                                .addTemporalMarker(() -> {
                                    Intake.CrankPosition(0.69);
                                })
                                .waitSeconds(0.3)
                                .addTemporalMarker(() -> {
                                    arm.setArmPos(0.15, 0.16);
                                })
                                .waitSeconds(0.3)
                                .build();
                        drive.followTrajectorySequence(CancelIntakePixel);
                        drive.update();
                        intakeCounter = 0;
                        if (inputTimer.milliseconds() > 8000) {
                            inputState = IntakeState.INTAKE_START;
                        }
                    }
                    break;
                case INTAKE_RETRACT:
                    Intake.CrankPosition(0.69);
                    if (inputTimer.milliseconds() >= 400){ //200
                        Intake.intakeArmServo.setPosition(0.4);
                        Intake.intakeWristServo.setPosition(0.5);
                        if (inputTimer.milliseconds() >= 500) { //inputTimer.milliseconds() >= 300 // 500 //800
                            inputTimer.reset();
                            inputState = IntakeState.INTAKE_INPUT;
                        }
                    }
                    break;
                case INTAKE_INPUT:
                    Intake.intakeWristServo.setPosition(0.66);
                    Intake.intakeArmServo.setPosition(0.4);
                    if (inputTimer.milliseconds() >= 400) { //inputTimer.milliseconds() >= 400  // 500 //600
                        Intake.intakeArmServo.setPosition(0.75);
                        gamepad1.rumble(100);
                        if (intakeArmPosition <= 117) {//inputTimer.milliseconds() >= 1000 //axonPosition <= 130 //inputTimer.milliseconds() >= 400 &&
                            Intake.intakeWristServo.setPosition(0.45);
                            Intake.intakeArmServo.setPosition(1);
                            Intake.crankServo.setPosition(0.69);
                            inputTimer.reset();
                            inputState = IntakeState.INTAKE_FINAL;
                        }
                    }
                    break;
                case INTAKE_FINAL:
                    if (inputTimer.milliseconds() >= 350) {
                        ArmV2.SetArmPosition(0.15, 0.16);
                        if (inputTimer.milliseconds() >= 350) { // 400
                            ArmV2.DropPixel(0.5);
                            ArmV2.SetArmPosition(0.1, 0.16);
                            output_power = lifter_pid(kp, ki, kd, -10);
                            if (output_power > 0.9) {
                                output_power = 1;
                            } else if (output_power < 0.2) {
                                output_power = 0;
                            }
                            slider.extendTo(-10, output_power);
                            if (inputTimer.milliseconds() >= 350) { //500 //600
                                output_power = lifter_pid(kp, ki, kd, 0);
                                if (output_power > 0.9) {
                                    output_power = 1;
                                } else if (output_power < 0.2) {
                                    output_power = 0;
                                }
                                slider.extendTo(0, output_power);
                            }
                            ArmV2.SetArmPosition(0.15, 0.16);
                            if (stackFlag) {
                                intakeCounter = 2;
                                stackFlag = false;
                            } else {
                                intakeCounter = 1;
                            }
                            inputTimer.reset();
                            inputState = IntakeState.INTAKE_START;
                        }
                    }
                    break;
                default:
                    inputState = IntakeState.INTAKE_START;
                    intakeCounter = 0;
            }
            //--------------------------------------------------------------------------------------

            //Outtake Sequence
            switch (outputState){
                case OUTTAKE_START:
                    //waiting for input
                    if (currentGamepad1.right_bumper && !previousGamepad1.right_bumper && (Intake.intakeArmServo.getPosition() > 0.75)){
                        outputTimer.reset();
                        outputState = OuttakeState.OUTTAKE_PUSH;
                    }
                    break;
                case OUTTAKE_PUSH:
                    Intake.intakeArmServo.setPosition(1);Intake.intakeWristServo.setPosition(0.45);Intake.crankServo.setPosition(0.69);
                    ArmV2.SetArmPosition(0.1, 0.16);
                    if (outputTimer.milliseconds() >= 200){
                        ArmV2.DropPixel(0.5);
//                        ArmV2.SetArmPosition(0.1, 0.16);
                        output_power = lifter_pid(kp, ki, kd, -10);
                        if (output_power > 0.9) {
                            output_power = 1;
                        } else if (output_power < 0.2) {
                            output_power = 0;
                        }
                        slider.extendTo(-10, output_power);
                        if (outputTimer.milliseconds() >= 400){
                            output_power = lifter_pid(kp, ki, kd, 0);
                            if (output_power > 0.9) {
                                output_power = 1;
                            } else if (output_power < 0.2) {
                                output_power = 0;
                            }
                            slider.extendTo(0, output_power);
                            ArmV2.SetArmPosition(0.15, 0.16);
                            outputTimer.reset();
                            outputState = OuttakeState.OUTTAKE_OPEN;
                        }
                    }
                    break;
                case OUTTAKE_OPEN:
                    Intake.IntakePixel(1);
                    if(outputTimer.milliseconds()>=200) {
                        Intake.intakeWristServo.setPosition(0.38);
                        if (outputTimer.milliseconds()>=400) {//400
                            Intake.intakeArmServo.setPosition(0.5);
                            Intake.intakeWristServo.setPosition(0.66);
                            outputTimer.reset();
                            outputState = OuttakeState.OUTTAKE_OUTPUT;
                        }
                    }
                    break;
                case OUTTAKE_OUTPUT:
                    ArmV2.SetArmPosition(0.5, 0.16);
                    if (outputTimer.milliseconds()>=300){
                        ArmV2.SetArmPosition(0.5, 0.68);
                    }
                    if (outputTimer.milliseconds() >= 500){
                        outputTimer.reset();
                        if (sliderCounter != 0)
                        {
                            outputState = OuttakeState.OUTTAKE_SLIDER;
                        }
                        else
                        {
                            outputState = OuttakeState.OUTTAKE_FINAL;
                        }

                    }
                    break;
                case OUTTAKE_SLIDER:
                    if (outputTimer.milliseconds()>=500){
                        ArmV2.SetArmPosition(0.5, 0.68);
                    }
                    if (outputTimer.milliseconds() >= 800){
                        if (sliderCounter == 1) {
                            output_power = lifter_pid(kp, ki, kd, levelOne);
                            if (output_power > 0.9) {
                                output_power = 1;
                            } else if (output_power < 0.2) {
                                output_power = 0;
                            }
                            slider.extendTo(levelOne, output_power);
                            sliderCounter = 0;
                        }
                        if (sliderCounter == 2) {
                            output_power = lifter_pid(kp, ki, kd, levelTwo);
                            if (output_power > 0.9) {
                                output_power = 1;
                            } else if (output_power < 0.2) {
                                output_power = 0;
                            }
                            slider.extendTo(levelTwo, output_power);
                            sliderCounter = 0;
                        }
                        if (sliderCounter == 3) {
                            output_power = lifter_pid(kp, ki, kd, levelThree);
                            if (output_power > 0.9) {
                                output_power = 1;
                            } else if (output_power < 0.2) {
                                output_power = 0;
                            }
                            slider.extendTo(levelThree, output_power);
                            sliderCounter = 0;
                        }
                        outputTimer.reset();
                        outputState = OuttakeState.OUTTAKE_FINAL;
                    }
                    break;
                case OUTTAKE_FINAL:
                    Intake.crankServo.setPosition(0.69);
                    Intake.intakeArmServo.setPosition(0.5);
                    Intake.intakeWristServo.setPosition(0.66);
                    if (outputTimer.milliseconds()>=100){
                        outputTimer.reset();
                        intakeCounter = 0;
                        outputState = OuttakeState.OUTTAKE_START;
                    }
                    break;
                default:
                    outputState = OuttakeState.OUTTAKE_START;
            }
            //--------------------------------------------------------------------------------------

            if (currentGamepad1.left_bumper && !previousGamepad1.left_bumper && (intakeCounter == 1) && (Intake.intakeArmServo.getPosition() == 1)){
                intakeCounter = 0;
                TrajectorySequence ResetIntake = drive.trajectorySequenceBuilder(startPose)
                        .addTemporalMarker(()->{Intake.IntakePixel(1);})
                        .waitSeconds(0.1)
                        .addTemporalMarker(()->{ArmV2.DropPixel(0.5);})
                        .addTemporalMarker(()->{arm.setArmPos(0.25, 0.16);})
                        .waitSeconds(0.3)
                        .addTemporalMarker(()->{Intake.intakeArmServo.setPosition(0.95);})
                        .waitSeconds(0.2)
                        .addTemporalMarker(()->{Intake.intakeWristServo.setPosition(0.38);})
                        .waitSeconds(0.4)
                        .addTemporalMarker(()->{Intake.intakeArmServo.setPosition(0.5);Intake.intakeWristServo.setPosition(0.66);})
                        .waitSeconds(0.2)
                        .addTemporalMarker(()->{arm.setArmPos(0.15, 0.16);})
                        .build();
                drive.followTrajectorySequenceAsync(ResetIntake);
                drive.update();
            }

            if (currentGamepad1.left_bumper && !previousGamepad1.left_bumper && (intakeCounter == 2) && (Intake.intakeArmServo.getPosition() == 1)){
                intakeCounter = 0;
                TrajectorySequence OneplusOne = drive.trajectorySequenceBuilder(startPose)
                        .addTemporalMarker(()->{Intake.IntakePixel(1);})
                        .waitSeconds(0.1)
                        .addTemporalMarker(()->{
                            ArmV2.DropPixel(0.75);})
                        .addTemporalMarker(()->{arm.setArmPos(0.25, 0.16);})
                        .waitSeconds(0.3)
                        .addTemporalMarker(()->{Intake.intakeArmServo.setPosition(0.95);})
                        .waitSeconds(0.2)
                        .addTemporalMarker(()->{Intake.intakeWristServo.setPosition(0.38);})
                        .waitSeconds(0.4)
                        .addTemporalMarker(()->{Intake.intakeArmServo.setPosition(0.5);Intake.intakeWristServo.setPosition(0.66);})
                        .waitSeconds(0.2)
                        .addTemporalMarker(()->{Intake.intakeArmServo.setPosition(0.4); Intake.intakeWristServo.setPosition(0.47);})
                        .waitSeconds(0.2)
                        .build();
                drive.followTrajectorySequenceAsync(OneplusOne);
                drive.update();
            }

            if(currentGamepad1.right_bumper && !previousGamepad1.right_bumper && (Intake.intakeArmServo.getPosition() < 0.75)){
                TrajectorySequence OuttakeArm = drive.trajectorySequenceBuilder(startPose)
                        .addTemporalMarker(()->{ArmV2.DropPixel(0.5);})
                        .addTemporalMarker(()->{arm.setArmPos(0.5, 0.16);})
                        .waitSeconds(0.4)
                        .addTemporalMarker(()->{arm.setArmPos(0.5, 0.66);})
                        .build();
                drive.followTrajectorySequenceAsync(OuttakeArm);
                drive.update();
            }

            if((currentGamepad1.y && !previousGamepad1.y) && (inputState!= IntakeState.INTAKE_START || outputState!= OuttakeState.OUTTAKE_START)){
                inputState = IntakeState.INTAKE_START;
                outputState = OuttakeState.OUTTAKE_START;
                TrajectorySequence ResetRobot = drive.trajectorySequenceBuilder(startPose)
                        .addTemporalMarker(()->{Intake.CrankPosition(0.38);})
                        .waitSeconds(0.3)
                        .addTemporalMarker(()->{Intake.intakeWristServo.setPosition(0.66); Intake.intakeArmServo.setPosition(0.5);})
                        .waitSeconds(0.1)
                        .addTemporalMarker(()->{Intake.IntakePixel(1);})
                        .addTemporalMarker(()->{arm.setArmPos(0.25, 0.16);})
                        .waitSeconds(0.2)
                        .addTemporalMarker(()->{ArmV2.DropPixel(0.95);})
                        .waitSeconds(0.5)
                        .addTemporalMarker(()->{})
                        .addTemporalMarker(()->{Intake.intakeWristServo.setPosition(0.65); Intake.intakeArmServo.setPosition(0.5);})
                        .addTemporalMarker(()->{Intake.IntakePixel(1);})
                        .addTemporalMarker(()->{Intake.CrankPosition(0.69);})
                        .waitSeconds(0.5)
                        .addTemporalMarker(()->{arm.setArmPos(0.15, 0.16);})
                        .waitSeconds(0.5)
                        .build();
                drive.followTrajectorySequenceAsync(ResetRobot);
                drive.update();
            }

            if(currentGamepad1.b && !previousGamepad1.b){
                //drop 1st pixel
                deliveryServoPos = 0.79;
                ArmV2.DropPixel(deliveryServoPos);
                TrajectorySequence DropPixelOne = drive.trajectorySequenceBuilder(startPose)
                        .addTemporalMarker(()->{ArmV2.DropPixel(0.79);})
                        .waitSeconds(0.3)
                        .addTemporalMarker(()->{arm.setArmPos(0.49, 0.68);}) //0.48
                        .waitSeconds(0.2)
                        .addTemporalMarker(()->{arm.setArmPos(0.5, 0.68);})
                        .build();
                drive.followTrajectorySequenceAsync(DropPixelOne);
                drive.update();
            }

            if(currentGamepad1.a && !previousGamepad1.a){
                //drop 2nd pixel
                output_power = lifter_pid(kp, ki, kd, levelZero);
                if (output_power > 0.9) {
                    output_power = 1;
                } else if (output_power < 0.2) {
                    output_power = 0;
                }
                TrajectorySequence DropPixelTwo = drive.trajectorySequenceBuilder(startPose)
                        .addTemporalMarker(()->{ArmV2.DropPixel(1);})
                        .waitSeconds(0.8) //0.3
                        .addTemporalMarker(()->{arm.setArmPos(0.3, 0.16);})
                        .waitSeconds(0.5)
                        .addTemporalMarker(()->{arm.setArmPos(0.15, 0.16);})
                        .waitSeconds(0.5)
                        .addTemporalMarker(()->{slider.extendTo(levelZero, output_power);})
                        .waitSeconds(0.2)
                        .build();
                drive.followTrajectorySequenceAsync(DropPixelTwo);
                drive.update();
            }

            if (currentGamepad1.dpad_up && !previousGamepad1.dpad_up) {

            }
            if (currentGamepad1.dpad_down && !previousGamepad1.dpad_down) {

            }
            if (currentGamepad1.x && !previousGamepad1.x){
                Drone.shootDrone();
            }
            if (currentGamepad1.dpad_right){
                Hanger.LiftRobot();
            }
            if (currentGamepad1.dpad_left){
                Hanger.PutDownRobot();
            }
            if(currentGamepad1.back && !previousGamepad1.back){
                Hanger.ExtendHanger();
            }
            if (currentGamepad2.dpad_up && !previousGamepad2.dpad_up){
                sliderCounter = 1;
            }
            if(currentGamepad2.dpad_right && !previousGamepad2.dpad_right){
                sliderCounter = 2;
            }
            if(currentGamepad2.dpad_down && !previousGamepad2.dpad_down){
                sliderCounter = 3;
            }
            if (currentGamepad2.dpad_left && !previousGamepad2.dpad_left){

            }
            if(currentGamepad2.a && !previousGamepad2.a){
//                stackFlag = true;
                Hanger.HangerDEC();
            }

            if(currentGamepad2.b && !previousGamepad2.b){
//                stackFlag = false;
                Hanger.HangerINC();
            }

            if (currentGamepad2.right_trigger>0.1 && !(previousGamepad2.right_trigger >0.1)){
                crankToggle = !crankToggle;
                if (crankToggle) {
                    TrajectorySequence openCrank = drive.trajectorySequenceBuilder(startPose)
                            .addTemporalMarker(()->{arm.setArmPos(0.25, 0.16);})
                            .waitSeconds(0.1)
                            .addTemporalMarker(()->{Intake.crankServo.setPosition(0.35);})
                            .waitSeconds(0.3)
                            .addTemporalMarker(()->{arm.setArmPos(0.15, 0.16);})
                            .waitSeconds(0.1)
                            .build();
                    drive.followTrajectorySequenceAsync(openCrank);
                    drive.update();
                }
                else
                {
                    TrajectorySequence closeCrank = drive.trajectorySequenceBuilder(startPose)
                            .addTemporalMarker(()->{arm.setArmPos(0.25, 0.16);})
                            .waitSeconds(0.1)
                            .addTemporalMarker(()->{Intake.crankServo.setPosition(0.69);})
                            .waitSeconds(0.4)
                            .addTemporalMarker(()->{arm.setArmPos(0.15, 0.16);})
                            .waitSeconds(0.1)
                            .build();
                    drive.followTrajectorySequenceAsync(closeCrank);
                    drive.update();

                }
            }

            if (currentGamepad2.start && !previousGamepad2.start){
                intakeToggle = !intakeToggle;
            }
            if (currentGamepad2.left_trigger>0.1 && !(previousGamepad2.left_trigger >0.1)){
                DriveTrain.setPower(frontLeftPower/2, backLeftPower/2, frontRightPower/2, backRightPower/2);
            }
            else {
                DriveTrain.setPower(frontLeftPower, backLeftPower, frontRightPower, backRightPower);
            }
            if (currentGamepad2.left_bumper && !previousGamepad2.left_bumper){
                gripperServoPos = 0.75;
                Intake.IntakePixel(gripperServoPos);
            }
            if (currentGamepad2.right_bumper && !previousGamepad2.right_bumper){
                gripperServoPos = 1;
                Intake.IntakePixel(gripperServoPos);
            }
            if(currentGamepad2.x && previousGamepad2.x){
                Intake.SetArmPosition(intakeArmServoPos, intakeWristServoPos);
            }
            if(currentGamepad2.y && previousGamepad2.y){
                ArmV2.SetArmPosition(armServoOnePos, wristServoPos);
            }
//            drive.update();
            telemetry.addData("x", myPose.getX());
            telemetry.addData("y", myPose.getY());
            telemetry.addData("heading", myPose.getHeading());
            telemetry.addData("Bot heading", botHeading);
            telemetry.addData("IntakeToggle", intakeToggle);
            telemetry.addData("sliderCounter", sliderCounter);
            telemetry.addData("stackFlag", stackFlag);
            telemetry.addData("IntakeCounter", intakeCounter);
            telemetry.addData("Beam Breaker State:", beamBreaker.getState());
            telemetry.addData("SliderMotorOne tick count", Slider.sliderMotorOne.getCurrentPosition());
            telemetry.addData("SliderMotorTwo tick count", Slider.sliderMotorTwo.getCurrentPosition());
            telemetry.addData("SliderMotorOne Current", Slider.sliderMotorOne.getCurrent(CurrentUnit.AMPS));
            telemetry.addData("SliderMotorTwo Current", Slider.sliderMotorTwo.getCurrent(CurrentUnit.AMPS));
            telemetry.addData("HangerMotor tick count", Hanger.hangerMotor.getCurrentPosition());
            telemetry.addData("Hanger Current", Hanger.hangerMotor.getCurrent(CurrentUnit.AMPS));

//
            telemetry.addData("armTwoPosition", armTwoPosition);
            telemetry.addData("wristPosition", wristPosition);
            telemetry.addData("intakeWristPosition", intakeWristPosition);
            telemetry.addData("armOnePosition", armOnePosition);
            telemetry.addData("crankPosition", crankPosition);
            telemetry.addData("intakeArm Position", intakeArmPosition);
            telemetry.addData("gripperServo", Intake.gripperServo.getPosition());
            telemetry.addData("intakeWristServo", Intake.intakeWristServo.getPosition());
            telemetry.addData("intakeArmServo", Intake.intakeArmServo.getPosition());
            telemetry.addData("crankServo", Intake.crankServo.getPosition());
            telemetry.addData("armServoOne", ArmV2.armServoOne.getPosition());
            telemetry.addData("armServoTwo", ArmV2.armServoTwo.getPosition());
            telemetry.addData("wristServo", ArmV2.wristServo.getPosition());
            telemetry.addData("armSlider", ArmV2.armSliderServo.getPosition());
            telemetry.addData("deliveryServo", ArmV2.deliveryServo.getPosition());

            telemetry.addData("LeftFrontCurrent", DriveTrain.leftFront.getCurrent(CurrentUnit.AMPS));
            telemetry.addData("RightFrontCurrent", DriveTrain.rightFront.getCurrent(CurrentUnit.AMPS));
            telemetry.addData("LeftRearCurrent", DriveTrain.leftRear.getCurrent(CurrentUnit.AMPS));
            telemetry.addData("RightRearCurrent", DriveTrain.rightRear.getCurrent(CurrentUnit.AMPS));
            telemetry.update();
            drive.update();
        }
    }
    public double lifter_pid(double kp_lifter, double ki_lifter, double kd_lifter, int target)
    {
        lifter_posL = Slider.sliderMotorOne.getCurrentPosition();
        lifter_posR = Slider.sliderMotorTwo.getCurrentPosition();
        error_lifter = target - lifter_posL;
        error_diff = error_lifter - errorprev;
        error_int = error_lifter + errorprev;
        output_lifter = kp_lifter*error_lifter + kd_lifter*error_diff +ki_lifter*error_int;
        error_lifterR = target - lifter_posR;
        error_diffR = error_lifterR - errorprevR;
        error_intR = error_lifterR + errorprevR;
        output_lifterR = kp_lifter*error_lifterR + kd_lifter*error_diffR +ki_lifter*error_intR;
        errorprev = error_lifter;
        errorprevR = error_lifterR;
        return Math.abs(output_lifter);
    }
    public double PIDControl(double reference, double state) {
        double error = angleWrap(reference - state);
        telemetry.addData("Error: ", error);
        integralSum += error * angle_timer.seconds();
        double derivative = (error - lastError) / (angle_timer.seconds());
        lastError = error;
        angle_timer.reset();
        double output = (error * Kp) + (derivative * Kd) + (integralSum * Ki);
        return output;
    }

    public double angleWrap(double radians){
        while(radians > Math.PI){
            radians -= 2 * Math.PI;
        }
        while(radians < -Math.PI){
            radians += 2 * Math.PI;
        }
        return radians;
    }

}
