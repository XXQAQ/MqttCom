package com.xq.mqttcom;

import android.content.Context;
import android.util.Pair;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MqttClientCom {

    private final Map<String, MqttClientChannel> mqttClientChannelMap = new HashMap<>();

    public void connect(Context context, final String url, String clientId, String userName, String password, int heartTime, Pair<String,byte[]> willTopicPair, final OnConnectListener onConnectListener){

        final Supplier<Boolean> containWithRemove = new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                if (mqttClientChannelMap.containsKey(url)){
                    synchronized (mqttClientChannelMap){
                        if (mqttClientChannelMap.containsKey(url)){
                            mqttClientChannelMap.remove(url);
                            return true;
                        }
                    }
                }
                return false;
            }
        };

        final MqttAndroidClient client = new MqttAndroidClient(context,url,clientId);

        MqttConnectOptions mOptions = new MqttConnectOptions();

        /**
         * 设置是否清空session,这里如果设置为false表示服务器会保留客户端的连接记录，
         * 这里设置为true表示每次连接到服务器都以新的身份连接
         */
        mOptions.setCleanSession(true);

        /**
         * 设置用户名
         */
        mOptions.setUserName(userName);

        /**
         * 设置密码
         */
        mOptions.setPassword(password == null ? null : password.toCharArray());

        /**
         * 设置超时时间 单位秒
         */
        mOptions.setConnectionTimeout(15);

        /**
         * 设置会话心跳时间 单位为秒 服务器会每隔1.5*20秒的时间向客户端发送个消息判断客户端是否在线，
         * 但这个方法并没有重连的机制
         */
        mOptions.setKeepAliveInterval(heartTime / 1000);

        if (willTopicPair != null){
            /**
             * setWill方法，如果项目中需要知道客户端是否掉线可以调用该方法。设置最终端口的通知消息
             */
            mOptions.setWill(willTopicPair.first, willTopicPair.second, MqttClientChannel.QOS_ONLY_ONCE, false);
        }


        /**
         * SSL 证书设置参考代码
         */
//            SSLSocketFactory sslSocketFactory = null;
//            try {
//                sslSocketFactory = sslContextFromStream(mContext.getAssets().open("server.pem")).getSocketFactory();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            mOptions.setSocketFactory(sslSocketFactory);

        final MqttClientChannel mqttClientChannel = new MqttClientChannel(client);

        synchronized (mqttClientChannelMap){
            mqttClientChannelMap.put(url,mqttClientChannel);
        }

        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable throwable) {
                //外部主动调用disconnect的情况下cause为null，此时不需要往上层回调
                if (throwable != null){
                    if (containWithRemove.get()){
                        mqttClientChannel.close();
                        mqttClientChannel.onConnectionLost(throwable);
                    }
                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                mqttClientChannel.onMessageArrived(topic,message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        try {
            client.connect(mOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    onConnectListener.onSuccess(mqttClientChannel);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable throwable) {
                    if (containWithRemove.get()){
                        client.close();
                        try {
                            client.disconnect();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        onConnectListener.onError(throwable.getMessage(),"");
                    }
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
            if (containWithRemove.get()){
                onConnectListener.onError(e.getMessage(),String.valueOf(e.getReasonCode()));
            }
        }
    }

    public boolean disconnect(String url){
        Pair<Boolean, MqttClientChannel> pair = containWithRemoveUrl(url);
        if (pair.first){
            pair.second.close();
        }
        return pair.first;
    }

    public void disconnectAll(){
        for (String url : mqttClientChannelMap.keySet()){
            disconnect(url);
        }
    }

    public boolean isConnected(String url){
        return mqttClientChannelMap.containsKey(url);
    }

    public List<String> getAllConnected(){
        return new ArrayList<>(mqttClientChannelMap.keySet());
    }

    private Pair<Boolean,MqttClientChannel> containWithRemoveUrl(String url){
        if (mqttClientChannelMap.containsKey(url)){
            synchronized (mqttClientChannelMap){
                if (mqttClientChannelMap.containsKey(url)){
                    return new Pair<>(true,mqttClientChannelMap.remove(url));
                }
            }
        }
        return new Pair<>(false,null);
    }

    public interface OnConnectListener{

        void onSuccess(MqttClientChannel mqttClientChannel);

        void onError(String info, String code);
    }

    interface OnConnectionLost{
        void onConnectionLost(Throwable throwable);
    }

    interface OnMessageArrived{
        void onMessageArrived(String topic, MqttMessage message);
    }

    interface Supplier<T> {
        T get();
    }

}
