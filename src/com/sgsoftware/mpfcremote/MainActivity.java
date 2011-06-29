package com.sgsoftware.mpfcremote;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;

public class MainActivity extends Activity implements View.OnClickListener {
	private RemotePlayer m_player;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

		((Button)findViewById(R.id.pauseBtn)).setOnClickListener(this);

		tryConnect();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menu_settings) {
			Intent intent = new Intent()
				.setClass(this, com.sgsoftware.mpfcremote.PrefActivity.class);
			this.startActivityForResult(intent, 0);
		}
		else if (item.getItemId() == R.id.menu_reconnect) {
			tryConnect();
		}
		return true;
	}
	
	@Override
	public void onActivityResult(int reqCode, int resCode, Intent data) {
		super.onActivityResult(reqCode, resCode, data);
	}

	@Override
	public void onClick(View target) {
		switch (target.getId()) {
		case R.id.pauseBtn:
			m_player.pause();
			break;
		}
	}

	private void tryConnect() {
		m_player = null;

		SharedPreferences prefs = getSharedPreferences("com.sgsoftware.mpfcremote_preferences", 0);
		String remoteAddr = prefs.getString("RemoteAddr", "");
		String remotePort = prefs.getString("RemotePort", "19792");

		try {
			m_player = new RemotePlayer(remoteAddr, Integer.parseInt(remotePort));
		}
		catch (java.net.UnknownHostException e) {
			m_player = null;
		}
		catch (java.io.IOException e) {
			m_player = null;
		}

		// Enable/disable controls
		boolean enabled = (m_player != null);
		findViewById(R.id.nextBtn).setEnabled(enabled);
		findViewById(R.id.pauseBtn).setEnabled(enabled);
		findViewById(R.id.prevBtn).setEnabled(enabled);
		findViewById(R.id.stopBtn).setEnabled(enabled);
		if (enabled) {
			((TextView)findViewById(R.id.curSongTextView)).setText("Current song");
		}
		else {
			((TextView)findViewById(R.id.curSongTextView)).setText("Not connected");
		}
	}
}
