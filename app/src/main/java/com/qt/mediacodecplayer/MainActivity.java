package com.qt.mediacodecplayer;

import android.content.Intent;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    public void onClick(View view){
        switch(view.getId()){
            case R.id.button:
                startActivity(new Intent(MainActivity.this,VideoPlayerActivity.class));
                break;

        }

    }
}