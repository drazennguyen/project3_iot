package uart.terminal.androidstudio.com.myapplication;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.util.List;

import java.util.concurrent.Executors;
import static java.lang.Integer.valueOf;



//https://github.com/rcties/PrinterPlusCOMM
//https://github.com/mik3y/usb-serial-for-android
public class MainActivity extends AppCompatActivity  implements SerialInputOutputManager.Listener {
    private static final String ACTION_USB_PERMISSION = "com.android.recipes.USB_PERMISSION";

    public StringBuilder dataString = new StringBuilder();
    String tempString = "";
    String lightString = "";

    MqttHelper mqttHelper;


    TextView txtOut;
    UsbSerialPort port;
    EditText editText;
    ImageButton sendBtn;
    private RadioGroup radioBaudrateGroup, radioDatabitsGroup, radioStopbitsGroup, radioParitybitsGroup;
    private RadioButton radioBaudrateButton, radioDatabitsButton, radioStopbitsButton, radioParitybitsButton;
    private Button btnSubmit;

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        addListenerOnButton();

        txtOut = findViewById(R.id.txtOut);
        editText = findViewById(R.id.send_text);
        sendBtn = findViewById(R.id.send_btn);

        startMqtt();

        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    byte[] data = ((editText.getText().toString())+"\n").getBytes();
                    SpannableStringBuilder spn = new SpannableStringBuilder();
                    spn.append("send " + data.length + " bytes\n");
                    spn.append(bytesToHex(data)+"\n");
                    spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                    port.write(data, 2000);
                    txtOut.append(spn);
                    txtOut.append("Sent Text: " + (editText.getText().toString()) + "\n");
                } catch (IOException e) {

                }
            }
        });


        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);

        if (availableDrivers.isEmpty()) {
            Log.d("UART", "UART is not available");
            txtOut.setText("UART is not available \n");
        }else {
            Log.d("UART", "UART is available");
            txtOut.setText("UART is available \n");

            UsbSerialDriver driver = availableDrivers.get(0);
            UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
            if (connection == null) {
                manager.requestPermission(driver.getDevice(), PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0));
            } else {
                // Most devices have just one port (port 0)
                port = driver.getPorts().get(0);
                try {
                    port.open(connection);
                    port.setParameters(115200  , 8, 1, 0);

                    SerialInputOutputManager usbIoManager = new SerialInputOutputManager(port, this);
                    Executors.newSingleThreadExecutor().submit(usbIoManager);
                } catch (Exception e) {
                    txtOut.setText("Sending a message is fail");
                }

            }
        }
    }

    public void addListenerOnButton() {

        radioBaudrateGroup = (RadioGroup) findViewById(R.id.radioBaudrate);
        radioDatabitsGroup = (RadioGroup) findViewById(R.id.radioDatabits);
        radioStopbitsGroup = (RadioGroup) findViewById(R.id.radioStopbits);
        radioParitybitsGroup = (RadioGroup) findViewById(R.id.radioParitybits);
        btnSubmit = (Button) findViewById(R.id.btnSubmit);

        btnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // get selected radio button from radioGroup
                int selectedId1 = radioBaudrateGroup.getCheckedRadioButtonId();
                int selectedId2 = radioDatabitsGroup.getCheckedRadioButtonId();
                int selectedId3 = radioStopbitsGroup.getCheckedRadioButtonId();
                int selectedId4 = radioParitybitsGroup.getCheckedRadioButtonId();

                // find the radiobutton by returned id
                radioBaudrateButton = (RadioButton) findViewById(selectedId1);
                radioDatabitsButton = (RadioButton) findViewById(selectedId2);
                radioStopbitsButton = (RadioButton) findViewById(selectedId3);
                radioParitybitsButton = (RadioButton) findViewById(selectedId4);
                txtOut.append("Set baudrate to " + radioBaudrateButton.getText().toString()
                        + ", Databits to " + radioDatabitsButton.getText().toString()
                        + ", Stopbits to " + radioStopbitsButton.getText().toString()
                        + " and Paritybits to " + radioParitybitsButton.getText().toString() + "\n");
                try {
                    port.setParameters(valueOf(radioBaudrateButton.getText().toString()),
                            valueOf(radioDatabitsButton.getText().toString()),
                            valueOf(radioStopbitsButton.getText().toString()),
                            valueOf(radioParitybitsButton.getText().toString()));
                } catch (IOException e) {
                    txtOut.append("Error!");
                }
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            port.close();
        }catch (Exception e) {}
    }

    @Override
    public void onNewData(final byte[] data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                receive(data);
            }
        });
    }

    @Override
    public void onRunError(Exception e) {

    }

    private void receive(byte[] data) {
        txtOut.append("Receive " + data.length + " bytes, data: ");
        String output = new String(data);
        txtOut.append(output+ "\n");
        dataString.append(output);

        int countTemp = StringUtils.countMatches(dataString, "#");
        for (int i = 0; i < countTemp; i++) {
            int end_index = dataString.indexOf("#");
            checkDataTemp(dataString, end_index);
        }

        int countLight = StringUtils.countMatches(dataString, "*");
        for (int i = 0; i < countLight; i++) {
            int end_index = dataString.indexOf("*");
            checkDataLight(dataString, end_index);
        }
    }

    public void checkDataTemp(StringBuilder str, int end_index){
        String cmd = str.substring(0, end_index+1);
        if ( (cmd.contains("$TEMP1,")) && (StringUtils.countMatches(cmd, "$") == 1) ){
            txtOut.append("Temperature: " + cmd + "\n\n");
            int index = str.indexOf(",");
            tempString = str.substring(index+1, end_index);
            dataString.delete(0, end_index+1);
            sendDataToThingSpeak(tempString, lightString);
            end_index = 0;
            //There is an Error!!!
        } else {
            txtOut.append("Error Data: " + cmd + "\n\n");
            dataString.delete(0, end_index+1);
            end_index = 0;
        }
    }

    public void checkDataLight(StringBuilder str, int end_index){
        String cmd = str.substring(0, end_index+1);
        if ( (cmd.contains("$LIGHT2,")) && (StringUtils.countMatches(cmd, "$") == 1)){
            txtOut.append("Light Density: " + cmd + "\n\n");
            int index = cmd.indexOf(",");
            lightString = str.substring(index+1, end_index);
            dataString.delete(0, end_index+1);
            sendDataToThingSpeak(tempString, lightString);
            end_index = 0;
        } else {
            txtOut.append("Error Data: " + cmd + "\n\n");
            dataString.delete(0, end_index+1);
            end_index = 0;
        }
    }

    // Key = Z5JJJTY5A43AFKPM

    private void sendDataToThingSpeak(final String temp, final String light) {
        if (!temp.equals("") && !light.equals("")) {
            OkHttpClient okHttpClient = new OkHttpClient();
            Request.Builder builder = new Request.Builder();
            Request request = builder.url("https://api.thingspeak.com/update?api_key=Z5JJJTY5A43AFKPM&field1=" + temp + "&field2=" + light).build();
            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    Log.d("TEST_BK", "FailRequest" + e.toString());
                    txtOut.append("Sent Temp data = " + temp + " and Light data = " + light +" to ThingSpeak fail!\n\n");
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    String jsonString = response.body().string();
                    Log.d("TEST_BK", "*" + jsonString);
                    txtOut.append("Sent Temp data = " + temp + " and Light data = " + light +" to ThingSpeak successfully!\n\n");
                }
            });
            lightString = "";
            tempString = "";
        }
    }

    private void startMqtt() {
        mqttHelper = new MqttHelper(getApplicationContext());
        MqttHelper.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean b, String s) {
                Log.w("Mqtt", "Connect complete"+ s );
            }

            @Override
            public void connectionLost(Throwable throwable) {
                Log.w("Mqtt", "Connection lost" );
            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
//                Log.w("Debug", mqttMessage.toString());
//                dataReceived.setText(mqttMessage.toString());
                txtOut.append(mqttMessage.toString()+"\n");
                try {
                    byte[] data = ((mqttMessage.toString()) + "\n").getBytes();
                    SpannableStringBuilder spn = new SpannableStringBuilder();
                    spn.append( "send" +data.length + "byte\n\n" );
                    spn.setSpan( new ForegroundColorSpan( getResources().getColor( R.color.colorAccent ) ), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE );
                    port.write(data, 2000);
                    txtOut.append(spn);
                } catch (IOException e){
                    txtOut.append( "Error" );
                }

            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
//                Log.w("Mqtt", "Delivery complete" );
            }
        });
//        Log.w("Mqtt", "will publish");
    }
}
