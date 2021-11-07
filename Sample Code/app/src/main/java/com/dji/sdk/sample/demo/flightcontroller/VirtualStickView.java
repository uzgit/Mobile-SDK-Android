package com.dji.sdk.sample.demo.flightcontroller;

import android.app.Service;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.SeekBar;

import androidx.annotation.NonNull;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.demo.gimbal.MoveGimbalWithSpeedView;
import com.dji.sdk.sample.internal.OnScreenJoystickListener;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.DialogUtils;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.sample.internal.utils.OnScreenJoystick;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.utils.VideoFeedView;
import com.dji.sdk.sample.internal.view.BaseThreeBtnView;
import com.dji.sdk.sample.internal.view.PresentableView;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.flightcontroller.simulator.InitializationData;
import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.gimbal.CapabilityKey;
import dji.common.gimbal.GimbalState;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.common.util.DJIParamMinMaxCapability;
import dji.keysdk.FlightControllerKey;
import dji.keysdk.KeyManager;
import dji.midware.data.model.P3.B;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.flightcontroller.Simulator;

import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.sdk.gimbal.Gimbal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Observable;

//TODO: Refactor needed

/**
 * Class for virtual stick.
 */
public class VirtualStickView extends RelativeLayout
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, PresentableView {

    private boolean yawControlModeFlag = true;
    private boolean rollPitchControlModeFlag = true;
    private boolean verticalControlModeFlag = true;
    private boolean horizontalCoordinateFlag = true;

    private boolean virtualSticksEnabled = false;

    private Button btnEnableVirtualStick;
    private Button btnDisableVirtualStick;
    private Button btnHorizontalCoordinate;
    private Button btnSetYawControlMode;
    private Button btnSetVerticalControlMode;
    private Button btnSetRollPitchControlMode;
    private ToggleButton btnSimulator;
    private Button btnTakeOff;
    private Button btnLand;

    private SeekBar rollSeekBar;
    private SeekBar pitchSeekBar;
    private SeekBar yawSeekBar;
    private SeekBar throttleSeekBar;
    private SeekBar gimbalPitchSeekBar;

    private TextView rollText;
    private TextView pitchText;
    private TextView yawText;
    private TextView throttleText;
    private TextView gimbalText;

    TextView textView;

    float gimbal_min;
    float gimbal_max;

    private Timer sendVirtualStickDataTimer;
    private SendVirtualStickDataTask sendVirtualStickDataTask;

    private Timer gimbalRotateTimer;
    private GimbalRotateTimerTask gimbalRotateTimerTask;
    float gimbal_pitch = 0;

    private Timer readCompanionBoardTimer;
    private ReadCompanionBoard readCompanionBoard;

    private float pitch = 0;
    private float roll = 0;
    private float yaw = 0;
    private float throttle = 0;
    private FlightControllerKey isSimulatorActived;
    private float command_pitch = 0;
    private float command_roll = 0;
    private float command_yaw = 0;
    private float command_throttle = 0;

    private String[] commands;

    private VideoFeedView primaryVideoFeedView;

    public VirtualStickView(Context context) {
        super(context);
        init(context);
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setUpListeners();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (null != sendVirtualStickDataTimer) {
            if (sendVirtualStickDataTask != null) {
                sendVirtualStickDataTask.cancel();

            }
            sendVirtualStickDataTimer.cancel();
            sendVirtualStickDataTimer.purge();
            sendVirtualStickDataTimer = null;
            sendVirtualStickDataTask = null;
        }
        tearDownListeners();
        super.onDetachedFromWindow();
    }

    private void init(Context context) {
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.view_virtual_stick, this, true);

        initAllKeys();
        initUI();
    }

    private void initAllKeys() {
        isSimulatorActived = FlightControllerKey.create(FlightControllerKey.IS_SIMULATOR_ACTIVE);
    }

    private void initUI() {
        btnEnableVirtualStick = (Button) findViewById(R.id.btn_enable_virtual_stick);
        btnDisableVirtualStick = (Button) findViewById(R.id.btn_disable_virtual_stick);
        btnHorizontalCoordinate = (Button) findViewById(R.id.btn_horizontal_coordinate);
        btnSetYawControlMode = (Button) findViewById(R.id.btn_yaw_control_mode);
        btnSetVerticalControlMode = (Button) findViewById(R.id.btn_vertical_control_mode);
        btnSetRollPitchControlMode = (Button) findViewById(R.id.btn_roll_pitch_control_mode);
        btnTakeOff = (Button) findViewById(R.id.btn_take_off);
        btnLand = (Button) findViewById(R.id.btn_land);

        btnSimulator = (ToggleButton) findViewById(R.id.btn_start_simulator);

        textView = (TextView) findViewById(R.id.textview_simulator);

        rollSeekBar = (SeekBar) findViewById(R.id.rollSeekBar);
        pitchSeekBar = (SeekBar) findViewById(R.id.pitchSeekBar);
        yawSeekBar = (SeekBar) findViewById(R.id.yawSeekBar);
        throttleSeekBar = (SeekBar) findViewById(R.id.throttleSeekBar);
        gimbalPitchSeekBar = (SeekBar) findViewById(R.id.gimbalPitchSeekBar);

        rollText = (TextView) findViewById(R.id.rollText);
        pitchText = (TextView) findViewById(R.id.pitchText);
        yawText = (TextView) findViewById(R.id.yawText);
        throttleText = (TextView) findViewById(R.id.throttleText);
        gimbalText = (TextView) findViewById(R.id.gimbalPitchText);

//        pitchSeekBar = (SeekBar) findViewById(R.id.pitchSeekBar);

        btnEnableVirtualStick.setOnClickListener(this);
        btnDisableVirtualStick.setOnClickListener(this);
        btnHorizontalCoordinate.setOnClickListener(this);
        btnSetYawControlMode.setOnClickListener(this);
        btnSetVerticalControlMode.setOnClickListener(this);
        btnSetRollPitchControlMode.setOnClickListener(this);
        btnTakeOff.setOnClickListener(this);
        btnLand.setOnClickListener(this);

        btnSimulator.setOnCheckedChangeListener(VirtualStickView.this);

        Boolean isSimulatorOn = (Boolean) KeyManager.getInstance().getValue(isSimulatorActived);
        if (isSimulatorOn != null && isSimulatorOn) {
            btnSimulator.setChecked(true);
            textView.setText("Simulator is On.");
        }

        set_default_modes();

        primaryVideoFeedView = (VideoFeedView) findViewById(R.id.video_view_primary_video_feed2);
        primaryVideoFeedView.registerLiveVideo(VideoFeeder.getInstance().getPrimaryVideoFeed(), true);

        if( null == readCompanionBoardTimer ){
            readCompanionBoard = new ReadCompanionBoard();
            readCompanionBoardTimer = new Timer();
            readCompanionBoardTimer.schedule(readCompanionBoard, 100, 100);
        }
    }

    private void set_default_modes(){

        FlightController flightController = ModuleVerificationUtil.getFlightController();
        if (flightController == null) {
            return;
        }

        flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
        flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
        flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
        flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);

        try {
            ToastUtils.setResultToToast("Set default modes.");
        }catch (Exception ex) { }
    }

    private void setUpListeners() {
        Simulator simulator = ModuleVerificationUtil.getSimulator();
        if (simulator != null) {
            simulator.setStateCallback(new SimulatorState.Callback() {
                @Override
                public void onUpdate(@NonNull final SimulatorState simulatorState) {
                    ToastUtils.setResultToText(textView,
                            "Yaw : "
                                    + simulatorState.getYaw()
                                    + ","
                                    + "X : "
                                    + simulatorState.getPositionX()
                                    + "\n"
                                    + "Y : "
                                    + simulatorState.getPositionY()
                                    + ","
                                    + "Z : "
                                    + simulatorState.getPositionZ());
                }
            });
        } else {
            ToastUtils.setResultToToast("Disconnected!");
        }

        throttleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

                float min = seekBar.getMin();
                float max = seekBar.getMax();
                float range = (max - min) / (float)2.0;
                float idle = (min + max) / (float)2.0;

                float control = (i - idle) / range;

                throttle = control;

                throttleText.setText("Throttle: " + String.valueOf( new Float(control) ));

                if (null == sendVirtualStickDataTimer) {
                    sendVirtualStickDataTask = new SendVirtualStickDataTask();
                    sendVirtualStickDataTimer = new Timer();
                    sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 100, 200);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                return;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                double idle = (seekBar.getMin() + seekBar.getMax()) / 2.0;

                seekBar.setProgress(new Integer((int)idle));
            }
        });

        yawSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

                float min = seekBar.getMin();
                float max = seekBar.getMax();
                float range = (max - min) / (float)2.0;
                float idle = (min + max) / (float)2.0;

                float control = (i - idle) / range;

                yaw = control;

                yawText.setText("Yaw: " + String.valueOf( new Float(control) ));

                if (null == sendVirtualStickDataTimer) {
                    sendVirtualStickDataTask = new SendVirtualStickDataTask();
                    sendVirtualStickDataTimer = new Timer();
                    sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 100, 200);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                return;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                double idle = (seekBar.getMin() + seekBar.getMax()) / 2.0;

                seekBar.setProgress(new Integer((int)idle));
            }
        });

        pitchSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

                float min = seekBar.getMin();
                float max = seekBar.getMax();
                float range = (max - min) / (float)2.0;
                float idle = (min + max) / (float)2.0;

                float control = (i - idle) / range;

                pitch = control;

                pitchText.setText("Pitch: " + String.valueOf( new Float(control) ));

                if (null == sendVirtualStickDataTimer) {
                    sendVirtualStickDataTask = new SendVirtualStickDataTask();
                    sendVirtualStickDataTimer = new Timer();
                    sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 100, 200);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                return;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                double idle = (seekBar.getMin() + seekBar.getMax()) / 2.0;

                seekBar.setProgress(new Integer((int)idle));
            }
        });

        rollSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

                float min = seekBar.getMin();
                float max = seekBar.getMax();
                float range = (max - min) / (float)2.0;
                float idle = (min + max) / (float)2.0;

                float control = (i - idle) / range;

                roll = control;

                rollText.setText("Roll: " + String.valueOf( new Float(control) ));

                if (null == sendVirtualStickDataTimer) {
                    sendVirtualStickDataTask = new SendVirtualStickDataTask();
                    sendVirtualStickDataTimer = new Timer();
                    sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 100, 200);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                return;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                double idle = (rollSeekBar.getMin() + rollSeekBar.getMax()) / 2.0;

                rollSeekBar.setProgress(new Integer((int)idle));
            }
        });

        gimbalPitchSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b)
            {
                float min = seekBar.getMin();
                float max = seekBar.getMax();
                float range = (max - min) / (float)2.0;
                float idle = (min + max) / (float)2.0;

//                float control = (i - idle) / range;

                gimbal_pitch = -i / (float)10.0;

                if( ModuleVerificationUtil.isGimbalModuleAvailable() )
                {
                    Gimbal gimbal = DJISampleApplication.getProductInstance().getGimbal();

                    gimbal.rotate(new Rotation.Builder().pitch(gimbal_pitch)
                            .mode(RotationMode.ABSOLUTE_ANGLE)
                            .yaw(Rotation.NO_ROTATION)
                            .roll(Rotation.NO_ROTATION)
                            .time(0)
                            .build(), new CommonCallbacks.CompletionCallback() {

                        @Override
                        public void onResult(DJIError error) {
                            if( null != error )
                            {
                                DialogUtils.showDialogBasedOnError(getContext(), error);
                            }
                        }
                    });

                    gimbal.setStateCallback(new GimbalState.Callback() {
                    @Override
                    public void onUpdate(@NonNull GimbalState gimbalState)
                    {
                        gimbalText.setText(String.valueOf(new Float(gimbalState.getAttitudeInDegrees().getPitch())));
                    }
                });
                }

//                if (null == gimbalRotateTimer) {
//                    gimbalRotateTimerTask = new GimbalRotateTimerTask();
//                    gimbalRotateTimer = new Timer();
//                    gimbalRotateTimer.schedule(gimbalRotateTimerTask, 100, 200);
//                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {
                return;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                return;
            }
        });

    }

    private void tearDownListeners() {
        Simulator simulator = ModuleVerificationUtil.getSimulator();
        if (simulator != null) {
            simulator.setStateCallback(null);
        }
    }

    private void notify_virtual_sticks_enabled(boolean enabled)
    {
        if( enabled )
        {
            btnEnableVirtualStick.setBackgroundColor(Color.GREEN);
        }
        else
        {
            btnEnableVirtualStick.setBackgroundColor(Color.RED);
        }
    }

    @Override
    public void onClick(View v) {
        FlightController flightController = ModuleVerificationUtil.getFlightController();
        if (flightController == null) {
            return;
        }
        switch (v.getId()) {
            case R.id.btn_enable_virtual_stick:

                flightController.getVirtualStickModeEnabled(new CommonCallbacks.CompletionCallbackWith<Boolean>() {
                    @Override
                    public void onSuccess(Boolean enabled)
                    {
                        flightController.setVirtualStickModeEnabled( ! enabled, new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                if( null != djiError )
                                {
                                    DialogUtils.showDialogBasedOnError(getContext(), djiError);
                                }
                            }
                        });
                    }

                    @Override
                    public void onFailure(DJIError djiError)
                    {
                        DialogUtils.showDialogBasedOnError(getContext(), djiError);
                    }
                });

                flightController.getVirtualStickModeEnabled(new CommonCallbacks.CompletionCallbackWith<Boolean>() {
                    @Override
                    public void onSuccess(Boolean enabled) {
                        notify_virtual_sticks_enabled(enabled);
                    }

                    @Override
                    public void onFailure(DJIError djiError) {
                        DialogUtils.showDialogBasedOnError(getContext(), djiError);
                    }
                });

                break;

//            case R.id.btn_disable_virtual_stick:
//                flightController.setVirtualStickModeEnabled(false, new CommonCallbacks.CompletionCallback() {
//                    @Override
//                    public void onResult(DJIError djiError) {
//                        DialogUtils.showDialogBasedOnError(getContext(), djiError);
//
//                        if( null == djiError )
//                        {
//                            virtualSticksEnabled = false;
//                            notify_virtual_sticks_enabled();
//                        }
//                    }
//                });
//                break;

            case R.id.btn_roll_pitch_control_mode:
                if (rollPitchControlModeFlag) {
                    flightController.setRollPitchControlMode(RollPitchControlMode.ANGLE);
                    rollPitchControlModeFlag = false;
                } else {
                    flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
                    rollPitchControlModeFlag = true;
                }
                try {
                    ToastUtils.setResultToToast(flightController.getRollPitchControlMode().name());
                } catch (Exception ex) {
                }
                break;

            case R.id.btn_yaw_control_mode:
                if (yawControlModeFlag) {
                    flightController.setYawControlMode(YawControlMode.ANGLE);
                    yawControlModeFlag = false;
                } else {
                    flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
                    yawControlModeFlag = true;
                }
                try {
                    ToastUtils.setResultToToast(flightController.getYawControlMode().name());
                } catch (Exception ex) {
                }
                break;

            case R.id.btn_vertical_control_mode:
                if (verticalControlModeFlag) {
                    flightController.setVerticalControlMode(VerticalControlMode.POSITION);
                    verticalControlModeFlag = false;
                } else {
                    flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
                    verticalControlModeFlag = true;
                }
                try {
                    ToastUtils.setResultToToast(flightController.getVerticalControlMode().name());
                } catch (Exception ex) {
                }
                break;

            case R.id.btn_horizontal_coordinate:
                if (horizontalCoordinateFlag) {
                    flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.GROUND);
                    horizontalCoordinateFlag = false;
                } else {
                    flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
                    horizontalCoordinateFlag = true;
                }
                try {
                    ToastUtils.setResultToToast(flightController.getRollPitchCoordinateSystem().name());
                } catch (Exception ex) {
                }
                break;

            case R.id.btn_take_off:


                flightController.startTakeoff(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        DialogUtils.showDialogBasedOnError(getContext(), djiError);
                    }
                });
                break;

            case R.id.btn_land:
                flightController.startLanding(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        DialogUtils.showDialogBasedOnError(getContext(), djiError);
                    }
                });
                break;

            default:
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (compoundButton == btnSimulator) {
            onClickSimulator(b);
        }
    }

    private void onClickSimulator(boolean isChecked) {
        Simulator simulator = ModuleVerificationUtil.getSimulator();
        if (simulator == null) {
            return;
        }
        if (isChecked) {

            textView.setVisibility(VISIBLE);

            simulator.start(InitializationData.createInstance(new LocationCoordinate2D(23, 113), 10, 10),
                    new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {

                        }
                    });
        } else {

            textView.setVisibility(INVISIBLE);

            simulator.stop(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {

                }
            });
        }
    }

    @Override
    public int getDescription() {
        return R.string.flight_controller_listview_virtual_stick;
    }

    private class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {
            if (ModuleVerificationUtil.isFlightControllerAvailable()) {

                float yaw_output = 50*yaw;

                FlightControlData flightControlData = new FlightControlData(roll,pitch,yaw_output,throttle);


                DJISampleApplication.getAircraftInstance()
                        .getFlightController()
                        .sendVirtualStickFlightControlData(flightControlData,
                                new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {

                                    }
                                });
            }
        }
    }

    private class GimbalRotateTimerTask extends TimerTask {
        float pitchValue;

        GimbalRotateTimerTask() {
            super();
        }

        @Override
        public void run() {
            if (ModuleVerificationUtil.isGimbalModuleAvailable()) {
                DJISampleApplication.getProductInstance().getGimbal().
                        rotate(new Rotation.Builder().pitch(45)
                                .mode(RotationMode.ABSOLUTE_ANGLE)
                                .yaw(Rotation.NO_ROTATION)
                                .roll(Rotation.NO_ROTATION)
                                .time(0)
                                .build(), new CommonCallbacks.CompletionCallback() {

                            @Override
                            public void onResult(DJIError error) {

                            }
                        });
            }
        }
    }

    private class ReadCompanionBoard extends TimerTask {

        ReadCompanionBoard() { super(); }

//        String host = "192.168.1.21";
        String host = "192.168.1.115";
        String port = "14555";

        Socket socket = null;
        BufferedReader reader;
        PrintWriter writer;
        OutputStream outputStream;

        boolean reset = false;

        public void actuate(SeekBar seekbar, float control, float input_minimum, float input_maximum)
        {
            float output_minimum = seekbar.getMin();
            float output_maximum = seekbar.getMax();

            float progress = output_minimum + (control - input_minimum) * (output_maximum - output_minimum) / (input_maximum - input_minimum);

            seekbar.setProgress( (int)progress );
        }

        @Override
        public void run() {
            if( null == socket ) {
                try {
                    textView.setText("Connecting to companion board...");
                    socket = new Socket(host, Integer.valueOf(port));

                    outputStream = socket.getOutputStream();
                    writer = new PrintWriter(socket.getOutputStream());

                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                } catch (Exception e) {
                    e.printStackTrace();
                }

                if( ! reset )
                {
                    actuate(rollSeekBar, 0, -1, 1);
                    actuate(pitchSeekBar, 0, -1, 1);
                    actuate(yawSeekBar, 0, -1, 1);
                    actuate(throttleSeekBar, 0, -1, 1);

                    reset = true;
                }
            }
            else {

                if( ! socket.isClosed() ) {
                textView.setText("Connected!");
                    String output = String.valueOf(System.currentTimeMillis());

                    writer.write(output + "\n");
                    writer.flush();

                    String command_notifier = "default";
                    try {

                        textView.setText(command_notifier);

                        String input = reader.readLine();

                        if(input.length() > 0)
                        {
                            input = input;
                            commands = input.split(",");

                            command_roll = Float.valueOf(commands[0]);
                            command_pitch = Float.valueOf(commands[1]);
                            command_yaw = Float.valueOf(commands[2]);
                            command_throttle = Float.valueOf(commands[3]);

                            command_notifier = "received something";
//                            command_notifier = String.format("roll: %f, pitch: %f, yaw: %f, throttle: %f");
                        }
                        else
                        {
                            input = "nothing received..." + String.valueOf(System.currentTimeMillis());
                        }

                    } catch (Exception e) {
//                        textView.setText("DISCONNECTED!");

                        try {
                            socket.close();
                            socket = null;

//                            textView.setText("Disconnected from companion board!");
                        } catch (Exception e2) {
                            e2.printStackTrace();
                        }
                    }

                    command_notifier = String.format("%d: roll: %f, pitch: %f, yaw: %f, throttle: %f", System.currentTimeMillis(), command_roll, command_pitch, command_yaw, command_throttle);
                    textView.setText(command_notifier);

                    actuate(rollSeekBar, command_roll, -1, 1);
                    actuate(pitchSeekBar, command_pitch, -1, 1);
                    actuate(yawSeekBar, command_yaw, -1, 1);
                    actuate(throttleSeekBar, command_throttle, -1, 1);

                    reset = false;
                }
                else
                {
                    try {
                        socket.close();
                        socket = null;

                        textView.setText("Disconnected from companion board!");

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
//                    actuate(rollSeekBar, 0, -1, 1);
//                    actuate(pitchSeekBar, 0, -1, 1);
//                    actuate(yawSeekBar, 0, -1, 1);
//                    actuate(throttleSeekBar, 0, -1, 1);
                }
            }
        }

    }

}
