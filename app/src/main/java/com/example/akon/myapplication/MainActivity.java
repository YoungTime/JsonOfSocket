package com.example.akon.myapplication;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private Gson gson;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.iv_show);

        gson = new Gson();
        getJson();

    }

    private void getJson(){

        Observable.unsafeCreate(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                // 调用其 onStart() 方法，初始化数据或者做一些准备
                subscriber.onStart();
                try {
                    // 创建 Socket，需要传入两个参数，第一个是 IP 地址，第二个是端口号
                    // 这里我们的 IP 地址是本地服务器的，所有小伙伴们可以自己写一个简单的服务器程序
                    Socket socket = new Socket("192.168.1.105",8000);

                    // 获取输出流
                    OutputStream outputStream = socket.getOutputStream();
                    // 创建一个 OutputStreamWriter 来写输出流
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                    // 这里我们使用 BufferWriter 来写，更加方便
                    BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);
                    // 服务器需要验证客户端身份，客服端发送大小为 5 的 byte 数组
                    byte[] buff = new byte[5];
                    // 服务器规定 buff[3]=0x20，buff[4]='\0'
                    buff[3] = 0x20;
                    buff[4] = '\0';
                    // 写入数据
                    bufferedWriter.write(new String(buff));
                    bufferedWriter.flush();

                    // 获取输入流，和输出流一样，我们也要使用 InputStreamReader 和 BufferedReader
                    InputStream inputStream = socket.getInputStream();
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                    // 服务器端会在每一个 json 字符串完成后发送一个 \t，所有我们使用 readLine()
                    String line = null;
                    while ((line = bufferedReader.readLine())!=null){
                        // 这里我们先打印获取的 json 字符串
                        System.out.println(line);
                        // 调用 onNext 方法，当被观察者状态变化（即发送数据）
                        // 则进行事件操作
                        subscriber.onNext(line);
                    }

                    // 调用 onCompleted() 方法
                    subscriber.onCompleted();


                }catch (IOException e){
                    e.printStackTrace();
                }

            }
        })
                /*
                    RxJava 支持线程调度，能将操作切换到其它线程
                    Schedulers.immediate()：当前线程
                    Schedulers.newThread()：新线程
                    Schedulers.io() ：I/O 操作的线程（线程无限的内部线程池）
                    Schedulers.computation() ：计算线程（大小为 CPU 数的内部线程池）
                    AndroidSchedulers.mainThread()：Android 的主线程
                 */
                // 这里将被观察者（即 Socket 通信）放到子线程
                .subscribeOn(Schedulers.io())
                // 将观察者（即响应事件）切换到计算线程
                .observeOn(Schedulers.computation())
                /*
                      RxJava 支持类型转换
                      就是将事件序列中的对象或整个序列进行加工处理，转换成不同的事件或事件序列
                      可以使用 map() 和 flatMap() 进行类型转换
                      感兴趣的同学可以去看看
                 */

                // 因为 onNext() 进行事件响应时是传入的 String 类型的 json 字符串
                // map() 方法实现了一个 Funcl 的匿名内部类
                // 其构造方法有两个参数，第一个参数代表要转换的，第二个代表转换成的
                // 所以这里我们先将 String 转换成 ImageBean 类型
                .map(new Func1<String, ImageBean>() {
                    @Override
                    public ImageBean call(String s) {
                        // 使用 Gson 将 json 字符串转换为 Bean 的对象
                        ImageBean bean = gson.fromJson(s,ImageBean.class);
                        return bean;
                    }
                })
                // 将观察者切换到 Android 主线程
                .observeOn(AndroidSchedulers.mainThread())
                .map(new Func1<ImageBean, ImageBean>() {
                    @Override
                    public ImageBean call(ImageBean imageBean) {
                        // 因为我们这里主要做一些图片解析
                        // 所有如果需要我们可以在这里对 Json 字符串中基本类型进行操作
                        return imageBean;
                    }
                })
                .observeOn(Schedulers.computation())
                // 因为我们的 Json 字符串比较复杂，摄像头图片的数据在
                // ImageBean 的内部类 UsbCamDataBean 中，所以我们先进行类型转换
                .map(new Func1<ImageBean, ImageBean.UsbCamDataBean>() {
                    @Override
                    public ImageBean.UsbCamDataBean call(ImageBean imageBean) {
                        // 因为我们可能不止一个摄像头，所以用集合保存的
                        // get(0) 获取第一个摄像头的数据，小伙伴们不用管
                        return imageBean.getUsb_cam_data().get(0);
                    }
                })
                // 我们的图片使用的是 Base64 编码，所以这里先将它的数据转换成 byte 数组
                .map(new Func1<ImageBean.UsbCamDataBean, byte[]>() {
                    @Override
                    public byte[] call(ImageBean.UsbCamDataBean usbCamDataBean) {
                        byte[] data = Base64.decode(usbCamDataBean.getData(),Base64.DEFAULT);
                        return data;
                    }
                })
                // 再将其转换为 Bitmap
                .map(new Func1<byte[], Bitmap>() {
                    @Override
                    public Bitmap call(byte[] bytes) {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                        return bitmap;
                    }
                })
                // 然后在 Android 主线程进行事件订阅
                // 完成其 onStart()、onCompleted()、onError()、onNext() 方法的实现
                // 这四个方法不用多讲，大家看名字应该就知道是干嘛的
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Bitmap>() {
                    @Override
                    public void onStart() {
                        super.onStart();
                        Toast.makeText(MainActivity.this,"解析開始",Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCompleted() {
                        Toast.makeText(MainActivity.this,"解析結束",Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Throwable e) {
                        // 进行发生错误后的操作
                        // 比如判断异常类型进行相关操作
                    }

                    @Override
                    public void onNext(Bitmap bitmap) {
                        // 加载图片
                        imageView.setImageBitmap(bitmap);
                    }
                });

    }
}
