package com.badlogic.mygame;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.mygame.MyGame;
import com.bodlogic.mygame.CaptureCrash;

public class AndroidLauncher extends AndroidApplication {
	@Override
	protected void onCreate (Bundle savedInstanceState) {
        Thread.setDefaultUncaughtExceptionHandler(new CaptureCrash(getApplicationContext()));

		super.onCreate(savedInstanceState);
		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useGL30 = true;
		initialize(new MyGame(), config);
	}
}
