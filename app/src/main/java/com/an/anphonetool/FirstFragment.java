package com.an.anphonetool;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MEDIA_PROJECTION_SERVICE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.an.anphonetool.core.DesktopConnection;
import com.an.anphonetool.core.DesktopConnectionDelegate;
import com.an.anphonetool.core.MDNSService;
import com.an.anphonetool.core.ServiceDiscoveryCallback;
import com.an.anphonetool.core.Utility;
import com.an.anphonetool.databinding.FragmentFirstBinding;
import com.an.anphonetool.service.MirrorHandler;
import com.an.anphonetool.service.MirrorHandlerDelegate;
import com.an.anphonetool.service.MirrorTask;
import com.google.protobuf.ByteString;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import javax.jmdns.ServiceEvent;


class ServiceInfo {
    String name;
    InetAddress addresses[];
}

public class FirstFragment extends Fragment
        implements ServiceDiscoveryCallback, DesktopConnectionDelegate, MirrorHandlerDelegate {

    private MDNSService mdnsService;

    private FragmentFirstBinding binding;

    private DesktopConnection desktopConnection = null;

    private List<ServiceInfo> serviceInfoList = new ArrayList<>();

    private Context context;

    private ActivityResultLauncher<String> selectFile;

    private MediaPlayer mp;
//    private Socket mirrorSocket;

    private SocketChannel mirrorSocketChannel;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;

    private MirrorHandler mirrorHandler;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    private void playRingtone() {
        try {
            if (mp == null) {
               mp = MediaPlayer.create(requireContext(), R.raw.mixkit_old_telephone_ring_1357);
            }

            mp.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mp != null) {
            mp.stop();
        }
    }


    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        selectFile =  registerForActivityResult(new ActivityResultContracts.GetContent(),
                (Uri result) -> {
                    Log.d("AN", result.toString());
                    if (desktopConnection != null) {
                        desktopConnection.sendFile(result, requireActivity());
                    } else {
                        Toast.makeText(requireContext(), "Not connected", Toast.LENGTH_SHORT).show();
                    }
                });

        binding = FragmentFirstBinding.inflate(inflater, container, false);

        mdnsService = new MDNSService(requireActivity());
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);



        binding.buttonFirst.setOnClickListener((button) -> {
            playRingtone();
        });

        binding.ScanAndConnect.setOnClickListener((button) -> {
            if (!serviceInfoList.isEmpty()) {
                // currently just connect to first one
                ServiceInfo info = serviceInfoList.get(0);
                desktopConnection = new DesktopConnection(info.addresses[0]);
                desktopConnection.setDelegate(this);
                desktopConnection.start();
            }
        });

        binding.SendMessage.setOnClickListener((button) -> {
            DesktopMessageOuterClass.DesktopMessage message =
                    DesktopMessageOuterClass.DesktopMessage.newBuilder()
                            .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageNone)
                            .setData(ByteString.copyFrom("some custom data".getBytes()))
                            .build();
            trySendDesktopMessage(message);
        });

        binding.arrowUp.setOnClickListener((button) -> {
            DesktopMessageOuterClass.DesktopMessage message =
                    DesktopMessageOuterClass.DesktopMessage.newBuilder()
                            .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageArrowKey)
                            .setData(ByteString.copyFrom(Utility.intToBytes(DesktopMessageOuterClass.ArrowKey.kArrowKeyUp.getNumber())))
                            .build();
            trySendDesktopMessage(message);
        });

        binding.arrowDown.setOnClickListener((button) -> {
            DesktopMessageOuterClass.DesktopMessage message =
                    DesktopMessageOuterClass.DesktopMessage.newBuilder()
                            .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageArrowKey)
                            .setData(ByteString.copyFrom(Utility.intToBytes(DesktopMessageOuterClass.ArrowKey.kArrowKeyDown.getNumber())))
                            .build();
            trySendDesktopMessage(message);
        });

        binding.arrowLeft.setOnClickListener((button) -> {
            DesktopMessageOuterClass.DesktopMessage message =
                    DesktopMessageOuterClass.DesktopMessage.newBuilder()
                            .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageArrowKey)
                            .setData(ByteString.copyFrom(Utility.intToBytes(DesktopMessageOuterClass.ArrowKey.kArrowKeyLeft.getNumber())))
                            .build();
            trySendDesktopMessage(message);
        });

        binding.arrowRight.setOnClickListener((button) -> {
            DesktopMessageOuterClass.DesktopMessage message =
                    DesktopMessageOuterClass.DesktopMessage.newBuilder()
                            .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageArrowKey)
                            .setData(ByteString.copyFrom(Utility.intToBytes(DesktopMessageOuterClass.ArrowKey.kArrowKeyRight.getNumber())))
                            .build();
            trySendDesktopMessage(message);
        });

        binding.sendText.setOnClickListener((button) -> {
            String text = binding.editText.getText().toString();
            if (text.isEmpty()) {
                return;
            }
            DesktopMessageOuterClass.DesktopMessage message =
                    DesktopMessageOuterClass.DesktopMessage.newBuilder()
                            .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageSendText)
                            .setData(ByteString.copyFrom(text.getBytes()))
                            .build();
            trySendDesktopMessage(message);
        });

        binding.sendFile.setOnClickListener((button) -> {
            selectFile.launch("*/*");
        });

        binding.mirrorScreen.setOnClickListener((button) -> {
            mediaProjectionManager = (MediaProjectionManager) requireContext().getSystemService(MEDIA_PROJECTION_SERVICE);

            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            //将请求码作为标识一起发送，调用该接口，需有返回方法
            startActivityForResult(captureIntent, 131313);

        });
    }


    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            //服务连接成功，需要通过Binder获取服务，达到Activity和Service通信的目的
            //获取Binder
            //通过Binder获取Service
            MirrorHandler.LocalBinder binder = (MirrorHandler.LocalBinder) iBinder;

            MirrorHandler mirrorHandler = binder.getService();

            mirrorHandler.setDelegate(FirstFragment.this);

            DisplayMetrics metrics = new DisplayMetrics();
            requireActivity().getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            mirrorHandler.prepare(mediaProjection, metrics.widthPixels, metrics.heightPixels);

            try {
                mirrorHandler.start(mirrorSocketChannel);
            } catch (IOException e) {
                Toast.makeText(requireContext(), e.toString(), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            try {
                mirrorSocketChannel.close();
                Log.d("AN", "Mirror disconnected");
            } catch (IOException ignored) {

            }
            mirrorSocketChannel = null;
            //连接失败
            Toast.makeText(FirstFragment.this.requireActivity(),"录屏服务未连接成功，请重试！",Toast.LENGTH_SHORT).show();
        }

    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == 131313 && resultCode == RESULT_OK){
            if (resultCode == RESULT_OK) {

                if (mirrorHandler != null) {
                    mirrorHandler.stop();
                }

                //录屏请求成功，使用工具MediaProjection录屏
                //从发送获得的数据和结果中获取该工具
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                //将该工具给Service，并一起传过去需要录制的屏幕范围的参数

                try {
                    mirrorSocketChannel = SocketChannel.open();
                    mirrorSocketChannel.configureBlocking(false);

                    mirrorSocketChannel.connect(new InetSocketAddress(serviceInfoList.get(0).addresses[0], 13133));

                    Intent intent = new Intent(requireActivity(), MirrorHandler.class);
                    requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

                } catch (IOException e) {
                    Toast.makeText(FirstFragment.this.requireActivity(),"录屏服务未连接成功，请重试！",Toast.LENGTH_SHORT).show();
                }


            } else {
                Toast.makeText(requireContext(), "You have to grant", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onMirrorHandlerStop() {
        ((MainActivity)context).runOnUiThread(() -> {
            requireActivity().unbindService(serviceConnection);
        });
    }

    private void trySendDesktopMessage(DesktopMessageOuterClass.DesktopMessage message) {
        if (desktopConnection != null) {
            desktopConnection.sendMessage(message);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

    }

    @Override
    public void onResume() {
        super.onResume();
//        try {
//            mdnsService.setWifiAddress(requireActivity());
            mdnsService.startDiscovery(this);
//        } catch (IOException e) {
//            Log.d("MDNS", e.toString());
//            binding.infoTextView.setText("MDNS Error " + e.toString());
//        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mdnsService.stopDiscovery();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onServiceFound(NsdServiceInfo serviceInfo) {
        Log.d("Debug", "found service: " + serviceInfo);
        Log.d("Debug", "Service port is " + serviceInfo.getPort());

        ((MainActivity)context).runOnUiThread(() -> {
            ServiceInfo info = new ServiceInfo();
            info.name = serviceInfo.getServiceName();
            info.addresses = new Inet4Address[1];
            info.addresses[0] = serviceInfo.getHost();
            serviceInfoList.add(info);

            binding.infoTextView.setText("Found Service " + serviceInfo);
        });
    }

    @Override
    public void onServiceRemove(NsdServiceInfo serviceEvent) {
        ((MainActivity)context).runOnUiThread(() -> {
            serviceInfoList.removeIf(info -> info.name.equals(serviceEvent.getServiceName()));
            binding.infoTextView.setText("Service removed" + serviceEvent);
        });
    }

    @Override
    public void onConnect(boolean success) {
        ((MainActivity)context).runOnUiThread(() -> {
            if (success) {
                binding.infoTextView.setText("connect success");
            } else {
                binding.infoTextView.setText("connect fail");
            }
        });
    }

    @Override
    public void onError(Exception e) {
        ((MainActivity)context).runOnUiThread(() -> {
            binding.infoTextView.setText("Desktop connection Error " + e.toString());
        });
    }

    @Override
    public void onFileSendProgress(double progress, double byteRate) {
        ((MainActivity)context).runOnUiThread(() -> {
            binding.infoTextView.setText("Send File progress " + progress +" Speed " + byteRate / 1024.0 / 1024.0 + " MB/S");
        });
    }
    @Override
    public void onFileSendComplete() {
        ((MainActivity)context).runOnUiThread(() -> {
            binding.infoTextView.setText("Send File complete");
        });
    }

    @Override
    public void toast(String msg) {
        ((MainActivity)context).runOnUiThread(() -> {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRing() {
        ((MainActivity)context).runOnUiThread(this::playRingtone);
    }
}