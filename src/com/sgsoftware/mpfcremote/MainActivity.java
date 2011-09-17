package com.sgsoftware.mpfcremote;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.SimpleAdapter;
import android.widget.ArrayAdapter;
import android.widget.Adapter;

public class MainActivity extends Activity implements View.OnClickListener {
	private RemotePlayer m_player;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

		((Button)findViewById(R.id.pauseBtn)).setOnClickListener(this);
		((Button)findViewById(R.id.refreshBtn)).setOnClickListener(this);
		((Button)findViewById(R.id.nextBtn)).setOnClickListener(this);
		((Button)findViewById(R.id.prevBtn)).setOnClickListener(this);
		((Button)findViewById(R.id.backBtn)).setOnClickListener(this);

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
		else if (item.getItemId() == R.id.menu_refresh) {
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
		case R.id.nextBtn:
			m_player.next();
			break;
		case R.id.prevBtn:
			m_player.prev();
			break;
		case R.id.backBtn:
			m_player.timeBack();
			break;
		case R.id.refreshBtn:
			tryConnect();
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
		findViewById(R.id.backBtn).setEnabled(enabled);
		findViewById(R.id.refreshBtn).setEnabled(true);
		if (enabled) {
			RemotePlayer.CurSong curSong = m_player.getCurSong();
			((TextView)findViewById(R.id.curSongTextView)).setText(curSong == null ? "" :
				String.format("%d. %s", curSong.posInList + 1, curSong.title));
			((TextView)findViewById(R.id.curTimeTextView)).setText(curSong == null ? "" :
				String.format("%d:%02d / %d:%02d", curSong.curPos / 60, curSong.curPos % 60,
					curSong.length / 60, curSong.length % 60));

			ListView playList = (ListView)findViewById(R.id.playListView);
			ArrayAdapter<String> adapter = 
				new ArrayAdapter<String>(this, R.layout.playlistrow, m_player.getPlayList());
			playList.setAdapter(adapter);
		}
		else {
			((TextView)findViewById(R.id.curSongTextView)).setText("Not connected");
			((ListView)findViewById(R.id.playListView)).setAdapter(null);
		}
	}
}
