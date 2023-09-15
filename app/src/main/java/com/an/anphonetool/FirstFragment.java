package com.an.anphonetool;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.an.anphonetool.core.DesktopConnection;
import com.an.anphonetool.core.DesktopConnectionDelegate;
import com.an.anphonetool.core.MDNSService;
import com.an.anphonetool.core.ServiceDiscoveryCallback;
import com.an.anphonetool.core.Utility;
import com.an.anphonetool.databinding.FragmentFirstBinding;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.List;

import javax.jmdns.ServiceEvent;


class ServiceInfo {
    String name;
    Inet4Address addresses[];
}

public class FirstFragment extends Fragment implements ServiceDiscoveryCallback, DesktopConnectionDelegate {

    private final MDNSService mdnsService = new MDNSService();

    private FragmentFirstBinding binding;

    private DesktopConnection desktopConnection = null;

    private List<ServiceInfo> serviceInfoList = new ArrayList<>();

    private Context context;

    private ActivityResultLauncher<String> selectFile;

    private MediaPlayer mp;
    private Ringtone ringtone;

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

        // Release the ringtone when the activity is stopped
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
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
        try {
            mdnsService.startDiscovery(this);
        } catch (IOException e) {
            Log.d("MDNS", e.toString());
            binding.infoTextView.setText("MDNS Error " + e.toString());
        }
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
    public void onServiceFound(ServiceEvent serviceInfo) {
        Log.d("Debug", "found service: " + serviceInfo);
        Log.d("Debug", "Service port is " + serviceInfo.getInfo().getPort());

        ((MainActivity)context).runOnUiThread(() -> {
            ServiceInfo info = new ServiceInfo();
            info.name = serviceInfo.getName();
            info.addresses = serviceInfo.getInfo().getInet4Addresses();
            serviceInfoList.add(info);

            binding.infoTextView.setText("Found Service " + serviceInfo);
        });
    }

    @Override
    public void onServiceRemove(ServiceEvent serviceEvent) {
        ((MainActivity)context).runOnUiThread(() -> {
            serviceInfoList.removeIf(info -> info.name.equals(serviceEvent.getInfo().getName()));
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