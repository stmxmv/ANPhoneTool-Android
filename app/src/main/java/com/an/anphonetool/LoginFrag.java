package com.an.anphonetool;

import android.content.Context;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.an.anphonetool.core.DesktopConnectionDelegate;
import com.an.anphonetool.core.MDNSService;
import com.an.anphonetool.core.ServiceDiscoveryCallback;
import com.an.anphonetool.databinding.DiscoverDesktopItemBinding;
import com.an.anphonetool.databinding.FragmentSecondBinding;
import com.an.anphonetool.databinding.LoginBinding;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

class ServiceInfo {
    String name;
    InetAddress addresses[];
}

public class LoginFrag extends Fragment implements ServiceDiscoveryCallback {
    LoginBinding binding;
    private MDNSService mdnsService;

    private List<ServiceInfo> serviceInfoList = new ArrayList<>();

    private Context context;

    class DiscoveredDesktopListAdapter extends RecyclerView.Adapter<DiscoveredDesktopListAdapter.ViewHolder> {

        List<ServiceInfo> noticeList;

        DiscoveredDesktopListAdapter(List<ServiceInfo> list) {
            noticeList = list;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            public DiscoverDesktopItemBinding binding;
            public ViewHolder(@NonNull View itemView, DiscoverDesktopItemBinding aBinding, Context context) {
                super(itemView);
                binding = aBinding;
            }
        }

        @NonNull
        @Override
        public DiscoveredDesktopListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            DiscoverDesktopItemBinding binding = DiscoverDesktopItemBinding.inflate(
                    LayoutInflater.from(parent.getContext()),
                    parent,
                    false);
            return new DiscoveredDesktopListAdapter.ViewHolder(binding.getRoot(), binding, parent.getContext());
        }

        @Override
        public void onBindViewHolder(@NonNull DiscoveredDesktopListAdapter.ViewHolder holder, int position) {
            ServiceInfo record = noticeList.get(position);
            holder.binding.name.setText(record.name);
            holder.binding.company.setText(record.addresses[0].getHostAddress());

            holder.binding.getRoot().setOnClickListener((view -> {
                ANApplication.getInstance().desktopConnection.setAddress(record.addresses[0]);
                ANApplication.getInstance().desktopConnection.setDelegate(new DesktopConnectionDelegate() {
                    @Override
                    public void onConnect(boolean success) {
                        if (success)  {
                            ((MainActivity)context).runOnUiThread(() -> {
                                NavController navController =
                                NavHostFragment.findNavController(LoginFrag.this);
                                if (navController.getCurrentDestination().getId() == R.id.Login)  {
                                    navController.navigate(R.id.action_FirstFragment_to_SecondFragment);
                                }
                            });
                        }
                    }
                    @Override
                    public void onError(Exception e) {}
                    @Override
                    public void onFileSendProgress(double progress, double byteRate) {}
                    @Override
                    public void onFileSendComplete() {}
                    @Override
                    public void toast(String msg) {}
                    @Override
                    public void onRing() {}
                });
                ANApplication.getInstance().desktopConnection.start();
            }));
//            holder.binding.dose.setText(record.dose);
//            holder.binding.time.setText(record.time);
//            holder.binding.place.setText(record.place);
        }

        @Override
        public int getItemCount() {
            return noticeList.size();
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = LoginBinding.inflate(inflater, container, false);
        mdnsService = new MDNSService(requireActivity());

        DiscoveredDesktopListAdapter adapter = new DiscoveredDesktopListAdapter(serviceInfoList);
        binding.recyclerView.setAdapter(adapter);

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireActivity());
        binding.recyclerView.setLayoutManager(layoutManager);

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        mdnsService.startDiscovery(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mdnsService.stopDiscovery();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


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

            binding.recyclerView.getAdapter().notifyItemInserted(serviceInfoList.size() - 1);

//            binding.infoTextView.setText("Found Service " + serviceInfo);
        });
    }

    @Override
    public void onServiceRemove(NsdServiceInfo serviceEvent) {
        ((MainActivity)context).runOnUiThread(() -> {

            int i = 0;
            for (; i < serviceInfoList.size(); ++i) {
                if (serviceInfoList.get(i).name.equals(serviceEvent.getServiceName()))
                {
                    serviceInfoList.remove(i);
                    binding.recyclerView.getAdapter().notifyItemRemoved(i);
                }
            }
//            serviceInfoList.removeIf(info -> info.name.equals(serviceEvent.getServiceName()));
//            binding.recyclerView.getAdapter().notifyDataSetChanged();

//            binding.infoTextView.setText("Service removed" + serviceEvent);
        });
    }
}

