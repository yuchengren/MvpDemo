package com.yuchengren.demo.app.body.login;

import com.ycr.kernel.http.IResult;
import com.ycr.kernel.task.AbstractTask;
import com.ycr.kernel.union.http.HttpHelper;
import com.ycr.module.framework.presenter.BaseActivityPresenter;
import com.ycr.module.framework.task.ApiTask;
import com.yuchengren.demo.app.body.BodyApis;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by yuchengren on 2018/12/11.
 */
public class LoginPresenter extends BaseActivityPresenter<ILoginContract.IView> implements ILoginContract.IPresenter {

	LoginManger loginManger = new LoginManger();

	public LoginPresenter(ILoginContract.IView mvpView) {
		super(mvpView);
	}

	@Override
	public ILoginContract.IView getEmptyView() {
		return ILoginContract.emptyView;
	}

	@Override
	public void login(final String loginName, final String password) {
		submitTask(new ApiTask<UserEntity>() {

			@Override
			public void onBeforeCall(@NotNull AbstractTask<IResult<UserEntity>> task) {
				super.onBeforeCall(task);
				//show进度条
			}

			@Override
			public void onFinishCall(@NotNull IResult<UserEntity> result) {
				super.onFinishCall(result);
				//关闭进度条
			}

			@Override
			public IResult<UserEntity> doInBackground() {
				return loginManger.login(loginName,password);
			}

			@Override
			public void onSuccess(@NotNull IResult<UserEntity> result) {
				super.onSuccess(result);
				getView().onLoginSuccess();
			}

			@Override
			public void onFailure(@NotNull IResult<UserEntity> result) {
				super.onFailure(result);
				getView().onLoginFail();
			}
		});
	}
}
