package com.yuchengren.mvp.app.ui.activity.Test;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.yuchengren.mvp.R;
import com.yuchengren.mvp.app.ui.fragment.base.BaseFragment;

/**
 * Created by yuchengren on 2018/7/11.
 */
public class TestFragment extends BaseFragment {

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_test, container, false);
		return view;
	}
}