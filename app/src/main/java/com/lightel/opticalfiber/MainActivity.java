package com.lightel.opticalfiber;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);

		setContentView(R.layout.activity_main);
		Button mDI1000 = findViewById(R.id.di1000);
		Button mDI2000 = findViewById(R.id.di2000);
		Button mDI3000 = findViewById(R.id.di3000);
		Button mDI5000 = findViewById(R.id.di5000);
		Button mUndefined= findViewById(R.id.undefined);
		Button mSettings= findViewById(R.id.settings);

		mDI1000.setOnClickListener(this);
		mDI2000.setOnClickListener(this);
		mDI3000.setOnClickListener(this);
		mDI5000.setOnClickListener(this);
		mUndefined.setOnClickListener(this);
		mSettings.setOnClickListener(this);
		mDI2000.performClick();
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.di1000:
			case R.id.di2000:
				startActivity(new Intent(this, InspectorActivity.class));
				break;
			case R.id.di3000:
			case R.id.di5000:
			case R.id.undefined:
			case R.id.settings:
				break;
		}
	}
}
