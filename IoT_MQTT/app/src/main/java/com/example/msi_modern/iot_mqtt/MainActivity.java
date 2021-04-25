package com.example.msi_modern.iot_mqtt;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SerialInputOutputManager.Listener {

    MQTTService mqttService;

    private Button buttonOn;
    private Button buttonOff;
    private TextView ledStatus;

    UsbSerialPort port;
    private static final String ACTION_USB_PERMISSION = "com.android.recipes.USB_PERMISSION";
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";

    private void initUSBPort(){
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);

        if (availableDrivers.isEmpty()) {
            Log.d("UART", "UART is not available");

        }else {
            Log.d("UART", "UART is available");

            UsbSerialDriver driver = availableDrivers.get(0);
            UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
            if (connection == null) {

                PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
                manager.requestPermission(driver.getDevice(), usbPermissionIntent);

                manager.requestPermission(driver.getDevice(), PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0));

                return;
            } else {

                port = driver.getPorts().get(0);
                try {
                    Log.d("UART", "openned succesful");
                    port.open(connection);
                    port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                    //port.write("ABC#".getBytes(), 1000);

                    SerialInputOutputManager usbIoManager = new SerialInputOutputManager(port, this);
                    Executors.newSingleThreadExecutor().submit(usbIoManager);

                } catch (Exception e) {
                    Log.d("UART", "There is error");
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestDataFromAdafruit("1", "2");

        buttonOn = findViewById(R.id.button_on);
        buttonOff = findViewById(R.id.button_off);
        ledStatus = findViewById(R.id.led);

        mqttService = new MQTTService(this);
        mqttService.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

            }

            @Override
            public void connectionLost(Throwable cause) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

                Log.d(topic, message.toString());

                if (message.toString().equals("1")) {
                    ledStatus.setText("ON");
                    ledStatus.setBackgroundColor(Color.rgb(3, 255, 3));
                    try {
                        port.write("1".getBytes(), 1000);
                    } catch (Exception e) {
                        Log.d("UART", "CANNOT SEND DATA TO DEVICE");
                    }
                } else {
                    ledStatus.setText("OFF");
                    ledStatus.setBackgroundColor(Color.rgb(255, 3, 3));
                    try {
                        port.write("0".getBytes(), 1000);
                    } catch (Exception e) {
                        Log.d("UART", "CANNOT SEND DATA TO DEVICE");
                    }
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        buttonOn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendDataMQTT(Integer.toString(1));
                ledStatus.setBackgroundColor(Color.rgb(3, 255, 3));
                ledStatus.setText("ON");
                try {
                    port.write("1".getBytes(), 1000);
                } catch (Exception e) {
                    Log.d("UART", "CANNOT SEND DATA TO DEVICE");
                }
            }
        });

        buttonOff.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendDataMQTT(Integer.toString(0));
                ledStatus.setBackgroundColor(Color.rgb(255, 3, 3));
                ledStatus.setText("OFF");
                try {
                    port.write("0".getBytes(), 1000);
                } catch (Exception e) {
                    Log.d("UART", "CANNOT SEND DATA TO DEVICE");
                }
            }
        });

        initUSBPort();
    }

    private void sendDataMQTT(String data){
        MqttMessage msg = new MqttMessage();
        msg.setId(1234);
        msg.setQos(0);
        msg.setRetained(true);

        byte[] b = data.getBytes(Charset.forName("UTF-8"));
        msg.setPayload(b);

        Log.d("ABC","Publish :" + msg);
        try {
            mqttService.mqttAndroidClient.publish("[your-feed-name]", msg);
        }catch (MqttException e) {
            Log.d("MQTT", "sendDataMQTT: cannot send message");
        }
    }

    private String buffer = "";
    @Override
    public void onNewData(byte[] data) {
        buffer += new String(data);
        if (buffer.contains("#") && buffer.contains("!")) {
            int index_soc = buffer.indexOf("#");
            int index_eof = buffer.indexOf("!");
            String cmd = buffer.substring(index_soc + 1, index_eof);
            buffer = "";
            if (cmd.equals("1")) {
                ledStatus.setBackgroundColor(Color.rgb(3, 255, 3));
                ledStatus.setText("ON");
                sendDataMQTT("1");
            } else if (cmd.equals("0")) {
                ledStatus.setBackgroundColor(Color.rgb(255, 3, 3));
                ledStatus.setText("OFF");
                sendDataMQTT("0");
            }
        }
    }

    @Override
    public void onRunError(Exception e) {

    }

    private void requestDataFromAdafruit(String ID, String value){
        OkHttpClient okHttpClient = new OkHttpClient();
        Request.Builder builder = new Request.Builder();
        //String apiURL = "https://api.thingspeak.com/update?api_key=0324U6WNIBX28W4G&field" + ID + "=" + value;
        //String apiURL = "https://api.thingspeak.com/update?api_key=0324U6WNIBX28W4G&field1=" + ID + "&field2=" + value;
        String apiURL = "https://io.adafruit.com/api/v2/quang3103/feeds/iot-test";
        final Request request = builder.url(apiURL).build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {

            }

            @Override
            public void onResponse(Response response) throws IOException {
                int code = response.code();
                Log.d("Server JSON", String.valueOf(code));
                String message = response.body().string();
                int idx = message.indexOf("last_value");
                Character value = message.charAt(idx+"last_value".length()+3);
                //Log.d("Last Value", value.toString());
                if (value == '1') {
                    try {
                        ledStatus.setBackgroundColor(Color.rgb(3, 255, 3));
                        ledStatus.setText("ON");
                        port.write("1".getBytes(), 1000);
                    } catch (Exception e) {
                        Log.d("UART", "CANNOT SEND DATA TO DEVICE");
                    }
                }
                if (value == '0') {
                    try {
                        ledStatus.setBackgroundColor(Color.rgb(255, 3, 3));
                        ledStatus.setText("OFF");
                        port.write("0".getBytes(), 1000);
                    } catch (Exception e) {
                        Log.d("UART", "CANNOT SEND DATA TO DEVICE");
                    }
                }
            }
        });
    }
}
