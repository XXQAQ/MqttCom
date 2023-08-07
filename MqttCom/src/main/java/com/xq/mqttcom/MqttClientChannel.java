package com.xq.mqttcom;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttClientChannel implements MqttClientCom.OnMessageArrived, MqttClientCom.OnConnectionLost {

    public static final int QOS_AT_MOST_ONCE = 0;
    public static final int QOS_AT_LEAST_ONCE = 1;
    public static final int QOS_ONLY_ONCE = 2;

    final MqttAndroidClient client;

    public MqttClientChannel(MqttAndroidClient client) {
        this.client = client;
    }

    void close(){
        client.close();
        client.unregisterResources();
        try {
            client.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private OnDisconnectedListener onDisconnectedListener;

    public void setOnDisconnectedListener(OnDisconnectedListener onDisconnectedListener) {
        this.onDisconnectedListener = onDisconnectedListener;
    }

    private OnReceiveMessageListener onReceiveMessageListener;

    public void setOnReceiveMessageListener(OnReceiveMessageListener onReceiveMessageListener) {
        this.onReceiveMessageListener = onReceiveMessageListener;
    }

    @Override
    public void onConnectionLost(Throwable throwable) {
        try {
            onDisconnectedListener.onDisconnected();
        } catch (NullPointerException e){

        }
    }

    @Override
    public void onMessageArrived(String topic, MqttMessage message) {
        try {
            onReceiveMessageListener.onReceiveMessage(message.getId(),topic,message.getPayload());
        } catch (NullPointerException e){

        }
    }

    public void subscribe(String[] topic, int[] qos, final OnActionCallback callback) {
        try {
            client.subscribe(topic, qos, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    callback.onSuccess();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable throwable) {
                    callback.onError(throwable == null?"mqtt by disconnected":throwable.getMessage(),"");
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage(),"");
        }
    }

    private int msgId = 0;
    public void send(String topic, int qos, byte[] bytes, final OnActionCallback callback){
        try {
            MqttMessage message = new MqttMessage();
            message.setQos(qos);
            message.setId(msgId++);
            message.setPayload(bytes);
            client.publish(topic, message, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    callback.onSuccess();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable throwable) {
                    callback.onError(throwable == null?"mqtt by disconnected":throwable.getMessage(),"");
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage(),"");
        }
    }

    public interface OnReceiveMessageListener{
        void onReceiveMessage(int msgId, String topic, byte[] bytes);
    }

    public interface OnActionCallback{

        void onSuccess();

        void onError(String info, String code);
    }

    public interface OnDisconnectedListener {

        void onDisconnected();

    }

}
