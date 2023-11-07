package com.an.anphonetool;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MEDIA_PROJECTION_SERVICE;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.an.anphonetool.core.DesktopConnection;
import com.an.anphonetool.core.DesktopConnectionDelegate;
import com.an.anphonetool.databinding.HomeBinding;
import com.an.anphonetool.databinding.LoginBinding;
import com.an.anphonetool.service.MirrorHandler;
import com.an.anphonetool.service.MirrorHandlerDelegate;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class HomeFrag extends Fragment implements DesktopConnectionDelegate, MirrorHandlerDelegate {

    HomeBinding binding;

    private Context context;

    DesktopConnection desktopConnection;

    private SocketChannel mirrorSocketChannel;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private MirrorHandler mirrorHandler;
    private ActivityResultLauncher<String> selectFile;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            //服务连接成功，需要通过Binder获取服务，达到Activity和Service通信的目的
            //获取Binder
            //通过Binder获取Service
            MirrorHandler.LocalBinder binder = (MirrorHandler.LocalBinder) iBinder;

            MirrorHandler mirrorHandler = binder.getService();

            mirrorHandler.setDelegate(HomeFrag.this);

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
            Toast.makeText(HomeFrag.this.requireActivity(),"录屏服务未连接成功，请重试！",Toast.LENGTH_SHORT).show();
        }

    };

    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = HomeBinding.inflate(inflater, container, false);
        desktopConnection = ANApplication.getInstance().desktopConnection;

        selectFile =  registerForActivityResult(new ActivityResultContracts.GetContent(),
                (Uri result) -> {
                    Log.d("AN", result.toString());
                    if (desktopConnection != null) {
                        desktopConnection.sendFile(result, requireActivity());
                    } else {
                        Toast.makeText(requireContext(), "Not connected", Toast.LENGTH_SHORT).show();
                    }
                });

        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.PPT.setOnClickListener(aView -> {
            NavHostFragment.findNavController(HomeFrag.this).navigate(R.id.action_Home_to_SlideShow);
        });

        binding.sendText.setOnClickListener(aView -> {
            openDialog();
        });

        binding.mirrorScreen.setOnClickListener(aView -> {
            mediaProjectionManager = (MediaProjectionManager) requireContext().getSystemService(MEDIA_PROJECTION_SERVICE);

            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            //将请求码作为标识一起发送，调用该接口，需有返回方法
            startActivityForResult(captureIntent, 131313);
        });

        binding.sendFile.setOnClickListener(aView -> {
            selectFile.launch("*/*");
        });

        binding.disconnectButton.setOnClickListener(aView -> {
            onConnect(false);
        });
    }

    void openDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("发送文字");

        final EditText input = new EditText(requireContext());
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String text = input.getText().toString();
                if (text.isEmpty()) {
                    return;
                }
                DesktopMessageOuterClass.DesktopMessage message =
                        DesktopMessageOuterClass.DesktopMessage.newBuilder()
                                .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageSendText)
                                .setData(ByteString.copyFrom(text.getBytes()))
                                .build();
                desktopConnection.sendMessage(message);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        desktopConnection.setDelegate(this);
    }

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

                    mirrorSocketChannel.connect(new InetSocketAddress(desktopConnection.getAddress(), 13133));

                    Intent intent = new Intent(requireActivity(), MirrorHandler.class);
                    requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

                } catch (IOException e) {
                    Toast.makeText(HomeFrag.this.requireActivity(),"录屏服务未连接成功，请重试！",Toast.LENGTH_SHORT).show();
                }


            } else {
                Toast.makeText(requireContext(), "You have to grant", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onConnect(boolean success) {
        if (!success)  {
            ((MainActivity)context).runOnUiThread(() -> {
                NavController navController =
                        NavHostFragment.findNavController(HomeFrag.this);
                if (navController.getCurrentDestination().getId() == R.id.Home)  {
                    navController.navigate(R.id.action_SecondFragment_to_FirstFragment);
                } else if (navController.getCurrentDestination().getId() == R.id.SlideShow) {
                    navController.navigate(R.id.action_SlideShow_to_Login);
                }
            });

            desktopConnection.stop();
            desktopConnection.reset();
        }
    }

    @Override
    public void onError(Exception e) {

    }

    @Override
    public void onFileSendProgress(double progress, double byteRate) {

    }

    @Override
    public void onFileSendComplete() {

    }

    @Override
    public void toast(String msg) {

    }

    @Override
    public void onRing() {

    }

    @Override
    public void onMirrorHandlerStop() {
        ((MainActivity)context).runOnUiThread(() -> {
            requireActivity().unbindService(serviceConnection);
        });
    }
}
