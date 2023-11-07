package com.an.anphonetool;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.an.anphonetool.core.DesktopConnection;
import com.an.anphonetool.core.Utility;
import com.an.anphonetool.databinding.HomeBinding;
import com.an.anphonetool.databinding.SlideshowBinding;
import com.google.protobuf.ByteString;

public class SlideShowFrag extends Fragment {

    SlideshowBinding binding;

    private Context context;

    DesktopConnection desktopConnection;

    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = SlideshowBinding.inflate(inflater, container, false);
        desktopConnection = ANApplication.getInstance().desktopConnection;
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

        binding.buttonBack.setOnClickListener(aView -> {
            NavHostFragment.findNavController(this).navigate(R.id.action_SlideShow_to_Home);
        });

        binding.upButton1.setOnClickListener(aView -> {
            DesktopMessageOuterClass.DesktopMessage message =
                    DesktopMessageOuterClass.DesktopMessage.newBuilder()
                            .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageArrowKey)
                            .setData(ByteString.copyFrom(Utility.intToBytes(DesktopMessageOuterClass.ArrowKey.kArrowKeyUp.getNumber())))
                            .build();
            desktopConnection.sendMessage(message);
        });
        binding.upButton2.setOnClickListener(aView -> {
            DesktopMessageOuterClass.DesktopMessage message =
                    DesktopMessageOuterClass.DesktopMessage.newBuilder()
                            .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageArrowKey)
                            .setData(ByteString.copyFrom(Utility.intToBytes(DesktopMessageOuterClass.ArrowKey.kArrowKeyUp.getNumber())))
                            .build();
            desktopConnection.sendMessage(message);
        });
        binding.downButton1.setOnClickListener(aView -> {
            DesktopMessageOuterClass.DesktopMessage message =
                    DesktopMessageOuterClass.DesktopMessage.newBuilder()
                            .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageArrowKey)
                            .setData(ByteString.copyFrom(Utility.intToBytes(DesktopMessageOuterClass.ArrowKey.kArrowKeyDown.getNumber())))
                            .build();
            desktopConnection.sendMessage(message);
        });
        binding.downButton2.setOnClickListener(aView -> {
            DesktopMessageOuterClass.DesktopMessage message =
                    DesktopMessageOuterClass.DesktopMessage.newBuilder()
                            .setType(DesktopMessageOuterClass.DesktopMessageType.kDesktopMessageArrowKey)
                            .setData(ByteString.copyFrom(Utility.intToBytes(DesktopMessageOuterClass.ArrowKey.kArrowKeyDown.getNumber())))
                            .build();
            desktopConnection.sendMessage(message);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}
