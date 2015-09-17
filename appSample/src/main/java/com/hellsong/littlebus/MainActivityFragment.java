package com.hellsong.littlebus;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.hellsong.littlebuslibrary.OnEvent;
import com.helsong.littlebuslibrary.LittleBus;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment {
    public static final String TAG = MainActivityFragment.class.getSimpleName();

    public MainActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        LittleBus.register(this);
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onResume() {
        LittleBus.post(1);
        LittleBus.post(new Object());
        super.onResume();
    }

    @Override
    public void onDestroy() {
        LittleBus.unregister(this);
        super.onDestroy();
    }

    @OnEvent(isRunOnBackGroundThread = true)
    public void onEvent(Object event) {
        Log.d(TAG, "OnEvent()" + event.toString());
    }

    @OnEvent(isRunOnBackGroundThread = false)
    public void FuckEvent(Integer event) {
        Log.d(TAG, "FuckEvent()" + event.toString());
    }
}
