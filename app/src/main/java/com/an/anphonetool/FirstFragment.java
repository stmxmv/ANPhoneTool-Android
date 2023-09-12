package com.an.anphonetool;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.an.anphonetool.core.MDNSService;
import com.an.anphonetool.core.ServiceDiscoveryCallback;
import com.an.anphonetool.databinding.FragmentFirstBinding;

import java.io.IOException;

import javax.jmdns.ServiceEvent;

public class FirstFragment extends Fragment implements ServiceDiscoveryCallback {

    private final MDNSService mdnsService = new MDNSService();

    private FragmentFirstBinding binding;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);



        binding.buttonFirst.setOnClickListener((button) -> {

        });
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            mdnsService.startDiscovery(this);
        } catch (IOException e) {
            Log.d("MDNS", e.toString());
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
    }
}