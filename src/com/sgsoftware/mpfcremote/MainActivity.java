package com.sgsoftware.mpfcremote;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.AdapterView;
import android.widget.SeekBar;
import android.os.Handler;
import android.graphics.Color;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

public class MainActivity extends Activity 
	implements
		View.OnClickListener,
		AdapterView.OnItemClickListener,
		SeekBar.OnSeekBarChangeListener,
		INotificationHandler {
	private static RemotePlayer m_player;
	
	private Handler m_handler;
	
	private boolean m_notificationsDisabled;

	private static int TIME_UPDATE_INTERVAL = 1000;

	private Receiver m_receiver;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        m_notificationsDisabled = false;
        
        m_handler = new Handler();

		((Button)findViewById(R.id.pauseBtn)).setOnClickListener(this);
		((Button)findViewById(R.id.nextBtn)).setOnClickListener(this);
		((Button)findViewById(R.id.prevBtn)).setOnClickListener(this);
		((Button)findViewById(R.id.backBtn)).setOnClickListener(this);
		((Button)findViewById(R.id.centerBtn)).setOnClickListener(this);
		((SeekBar)findViewById(R.id.seekBar)).setOnSeekBarChangeListener(this);

		ListView lv = (ListView)findViewById(R.id.playListView);
		lv.setOnItemClickListener(this);
		registerForContextMenu(lv);

		m_receiver = this.new Receiver();
		registerReceiver(m_receiver, new IntentFilter(Intent.ACTION_SCREEN_ON));

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
		else if (item.getItemId() == R.id.menu_playlist) {
			m_notificationsDisabled = true;
			Intent intent = new Intent()
				.setClass(this, com.sgsoftware.mpfcremote.PlaylistActivity.class);
			this.startActivityForResult(intent, 0);
			m_notificationsDisabled = false;
		}
		else if (item.getItemId() == R.id.menu_reconnect) {
			tryConnect();
		}
		else if (item.getItemId() == R.id.menu_refresh) {
			refreshAll();
		}
		return true;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			                        ContextMenuInfo info) {
		super.onCreateContextMenu(menu, v, info);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.playlist_context, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
		switch (item.getItemId()) {
			case R.id.remove_song:
				m_player.removeSong(info.position);
				return true;
			case R.id.queue_song:
				m_player.queueSong(info.position);
				return true;
		}
		return super.onContextItemSelected(item);
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
		case R.id.centerBtn:
		{
			RemotePlayer.CurSong curSong = m_player.getCurSong();
			if (curSong != null) {
				ListView playList = (ListView)findViewById(R.id.playListView);
				int scrollPos = curSong.posInList;
				scrollPos -= (playList.getLastVisiblePosition() - 
						playList.getFirstVisiblePosition() + 1) / 2;
				if (scrollPos < 0)
					scrollPos = 0;
				playList.setSelectionFromTop(scrollPos, 0);
			}
			break;
		}
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (fromUser && m_player != null) {
			m_player.seek(progress);
		}
	}
	
	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}
	
	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}
	
	@Override
	public void onItemClick(AdapterView<?> a, View v, int position, long id) {
		if (m_player != null)
			m_player.play(position);
	}

	private void updateCurTimeView() {
		RemotePlayer.CurSong curSong = m_player.getCurSong();
		((TextView)findViewById(R.id.curTimeTextView)).setText(curSong == null ? "" :
			String.format("%d:%02d / %d:%02d", curSong.curPos / 60, curSong.curPos % 60,
				curSong.length / 60, curSong.length % 60));
		((SeekBar)findViewById(R.id.seekBar)).setProgress(
			curSong == null ? 0 : curSong.curPos);
	}
	
	private void refresh() {
		m_handler.removeCallbacks(m_updateTimeTask);

		// Remember play list scrolling position
		ListView playList = (ListView)findViewById(R.id.playListView);
		int scrollPos = playList.getFirstVisiblePosition();
		View scrollV = playList.getChildAt(0);
		int scrollTop = (scrollV == null) ? 0 : scrollV.getTop();

		// Enable/disable controls
		boolean enabled = (m_player != null);
		findViewById(R.id.nextBtn).setEnabled(enabled);
		findViewById(R.id.pauseBtn).setEnabled(enabled);
		findViewById(R.id.prevBtn).setEnabled(enabled);
		findViewById(R.id.backBtn).setEnabled(enabled);
		findViewById(R.id.centerBtn).setEnabled(enabled);
		findViewById(R.id.seekBar).setEnabled(enabled);
		if (enabled) {
			int totalLength = m_player.getTotalLength();
			((TextView)findViewById(R.id.totalLength)).setText(
				String.format("Total length: %d:%02d",
					totalLength / 60, totalLength % 60));

			RemotePlayer.CurSong curSong = m_player.getCurSong();
			((TextView)findViewById(R.id.curSongTextView)).setText(curSong == null ? "" :
				String.format("%d. %s", curSong.posInList + 1, curSong.title));

			((SeekBar)findViewById(R.id.seekBar)).setMax(
				curSong == null ? 0 : curSong.length);

			updateCurTimeView();
			m_handler.postDelayed(m_updateTimeTask, TIME_UPDATE_INTERVAL);

			playList.setAdapter(new MyAdapter(m_player));
		}
		else {
			((TextView)findViewById(R.id.curSongTextView)).setText("Not connected");
			((TextView)findViewById(R.id.curTimeTextView)).setText("");
			((TextView)findViewById(R.id.totalLength)).setText("");
			((SeekBar)findViewById(R.id.seekBar)).setMax(0);
			playList.setAdapter(null);
		}

		// Restore scroll position
		playList.setSelectionFromTop(scrollPos, scrollTop);
	}

	private void refreshAll() {
		if (m_player != null)
			m_player.refresh();
		refresh();
	}
	
	private void tryConnect() {
		if (m_player != null) {
			m_player.destroy();
			m_player = null;
		}

		SharedPreferences prefs = getSharedPreferences("com.sgsoftware.mpfcremote_preferences", 0);
		String remoteAddr = prefs.getString("RemoteAddr", "");
		String remotePort = prefs.getString("RemotePort", "19792");

		try {
			m_player = new RemotePlayer(remoteAddr, Integer.parseInt(remotePort), this);
		}
		catch (java.net.UnknownHostException e) {
			m_player = null;
		}
		catch (java.io.IOException e) {
			m_player = null;
		}

		refresh();
	}

	@Override
	protected void onStop() {
		super.onStop();
		m_handler.removeCallbacks(m_updateTimeTask);
	}

	@Override
	protected void onRestart() {
		super.onRestart();
		refreshAll();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (m_player != null) {
			m_player.destroy();
			m_player = null;
		}
	}

	public void processNotification(String msg) {
		if (m_notificationsDisabled)
			return;
		
		m_handler.post(new Runnable() {
			@Override
			public void run() {
				refreshAll();
			}
		});
	}

	class Receiver extends BroadcastReceiver {
		public void onReceive(Context ctx, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				refreshAll();
			}
		}
	}

	private Runnable m_updateTimeTask = new Runnable() {
		public void run() {
			if (m_player == null)
				return;

			RemotePlayer.CurSong curSong = m_player.getCurSong();
			if (curSong == null)
				return;
			if (curSong.status != RemotePlayer.PlayStatus.PLAYING)
				return;

			m_player.incrementCurTime(TIME_UPDATE_INTERVAL);
			updateCurTimeView();

			m_handler.postDelayed(this, TIME_UPDATE_INTERVAL);
		}
	};

	public static RemotePlayer getPlayer() {
		return m_player;
	}

	private class MyAdapter extends BaseAdapter {
		private RemotePlayer m_player;

		public MyAdapter(RemotePlayer player) {
			m_player = player;
		}

		@Override
		public int getCount() {
			return m_player.getPlayList().size();
		}

		@Override
		public long getItemId(int pos) {
			return pos;
		}

		@Override
		public Object getItem(int pos) {
			return null;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater =
				(LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			ViewGroup vg = (ViewGroup)inflater.inflate(
					R.layout.playlistrow, parent, false);

			TextView tv_title = (TextView)vg.findViewById(R.id.playListItemTitle);
			TextView tv_len = (TextView)vg.findViewById(R.id.playListItemLength);

			RemotePlayer.Song s = m_player.getPlayList().get(position);
			tv_title.setText(String.format("%d. %s", position + 1, s.name));
			tv_len.setText(String.format("%d:%02d", s.length / 60, s.length % 60));

			int col = (m_player.getCurSong() != null && 
					   position == m_player.getCurSong().posInList) ?
					Color.RED : Color.LTGRAY;
			tv_title.setTextColor(col);
			tv_len.setTextColor(col);
			return vg;
		}
	}
}
