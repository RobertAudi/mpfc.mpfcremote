package com.sgsoftware.mpfcremote;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
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
import android.widget.CheckBox;
import android.os.Handler;
import android.graphics.Color;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.telephony.TelephonyManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.DialogFragment;
import android.content.DialogInterface;
import android.app.Dialog;
import android.app.AlertDialog;

public class MainActivity extends FragmentActivity 
	implements
		AdapterView.OnItemClickListener,
		SeekBar.OnSeekBarChangeListener,
		INotificationHandler,
        RemotePlayer.IRefreshHandler {
	private RemotePlayer m_player;
	
	private Handler m_handler;
	
	private boolean m_notificationsDisabled;

	private static int TIME_UPDATE_INTERVAL = 1000;

	private Receiver m_receiver;

	// The scroll position to be restored in onRestore
	class ScrollPos {
		int pos;
		int top;

		ScrollPos(int p, int t) {
			this.pos = p;
			this.top = t;
		}
	};
	private ScrollPos m_scrollPos;

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		savedInstanceState.putInt("scrollPos", m_scrollPos.pos);
		savedInstanceState.putInt("scrollTop", m_scrollPos.top);
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        m_notificationsDisabled = false;
        
        m_handler = new Handler();

		((SeekBar)findViewById(R.id.seekBar)).setOnSeekBarChangeListener(this);

		ListView lv = (ListView)findViewById(R.id.playListView);
		lv.setOnItemClickListener(this);
		registerForContextMenu(lv);

		m_receiver = this.new Receiver();
		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
		registerReceiver(m_receiver, filter);

		if (savedInstanceState != null) {
			m_scrollPos = new ScrollPos(savedInstanceState.getInt("scrollPos"),
					                    savedInstanceState.getInt("scrollTop"));
		}
    }

    @Override
	public void onResume() {
		super.onResume();

		tryConnect(m_scrollPos);
	}

    @Override
	public void onPause() {
		super.onPause();

		m_scrollPos = getScrollPos();

		m_handler.removeCallbacks(m_updateTimeTask);
		if (m_player != null) {
			m_player.destroy();
			m_player = null;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings: {
			Intent intent = new Intent()
				.setClass(this, com.sgsoftware.mpfcremote.PrefActivity.class);
			this.startActivityForResult(intent, 0);
			break;
		}
		case R.id.menu_playlist: {
			m_notificationsDisabled = true;
			Intent intent = new Intent()
				.setClass(this, com.sgsoftware.mpfcremote.PlaylistActivity.class);
			this.startActivityForResult(intent, 0);
			m_notificationsDisabled = false;
			break;
		}
		case R.id.menu_scanports: {
			m_notificationsDisabled = true;
			Intent intent = new Intent()
				.setClass(this, com.sgsoftware.mpfcremote.PortScannerActivity.class);
			this.startActivityForResult(intent, 0);
			m_notificationsDisabled = false;
			break;
		}
		case R.id.menu_volume: {
			DialogFragment frag = new VolumeDialogFragment(m_player);
			FragmentManager m = getSupportFragmentManager();
			frag.show(m, "volume");
			break;
		}
		case R.id.menu_reconnect:
			tryConnect(null);
			break;
		case R.id.menu_refresh:
			refreshAll(null);
			break;
		case R.id.menu_pause:
		case R.id.menu_play:
			m_player.pause();
			break;
		case R.id.menu_next:
			m_player.next();
			break;
		case R.id.menu_prev:
			m_player.prev();
			break;
		case R.id.menu_back:
			m_player.timeBack();
			break;
		case R.id.menu_center: {
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
	public boolean onPrepareOptionsMenu(Menu m) {
		boolean enabled = isConnected();
		boolean playing = isPlaying();
		m.findItem(R.id.menu_next).setEnabled(enabled);
		m.findItem(R.id.menu_pause).setEnabled(enabled);
		m.findItem(R.id.menu_pause).setVisible(playing);
		m.findItem(R.id.menu_play).setEnabled(enabled);
		m.findItem(R.id.menu_play).setVisible(!playing);
		m.findItem(R.id.menu_prev).setEnabled(enabled);
		m.findItem(R.id.menu_back).setEnabled(enabled);
		m.findItem(R.id.menu_center).setEnabled(enabled);
		return true;
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
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (fromUser && isConnected()) {
			m_player.seek((long)progress * 1000000);
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
		if (isConnected())
			m_player.play(position);
	}

	private void updateCurTimeView() {
		RemotePlayer.CurSong curSong = m_player.getCurSong();
		int msPos = curSong == null ? 0 : (int)(curSong.curPos / 1000000);
		int sPos = msPos / 1000;
		int sLen = curSong == null ? 0 : (int)(curSong.length / 1000000000);
		((TextView)findViewById(R.id.curTimeTextView)).setText(curSong == null ? "" :
			String.format("%d:%02d / %d:%02d", sPos / 60, sPos % 60, sLen / 60, sLen % 60));
		((SeekBar)findViewById(R.id.seekBar)).setProgress(
			curSong == null ? 0 : msPos);
	}

	private ScrollPos getScrollPos() {
		ListView playList = (ListView)findViewById(R.id.playListView);
		if (playList == null)
			return null;

		int scrollPos = playList.getFirstVisiblePosition();
		View scrollV = playList.getChildAt(0);
		int scrollTop = (scrollV == null) ? 0 : scrollV.getTop();
		return new ScrollPos(scrollPos, scrollTop);
	}
	
	private void refreshGui(Object param) {
		m_handler.removeCallbacks(m_updateTimeTask);

		// Remember play list scrolling position
		ListView playList = (ListView)findViewById(R.id.playListView);
		int scrollPos = playList.getFirstVisiblePosition();
		View scrollV = playList.getChildAt(0);
		int scrollTop = (scrollV == null) ? 0 : scrollV.getTop();

		// Get scroll position from the param if it was passed
		if (param != null) {
			ScrollPos sp = (ScrollPos)param;
			scrollPos = sp.pos;
			scrollTop = sp.top;
		}

		// Enable/disable controls
		boolean enabled = isConnected();
		findViewById(R.id.seekBar).setEnabled(enabled);
		invalidateOptionsMenu();
		if (enabled) {
			int totalLength = (int)(m_player.getTotalLength() / 1000000000);
			((TextView)findViewById(R.id.totalLength)).setText(
				String.format("Total length: %d:%02d",
					totalLength / 60, totalLength % 60));

			RemotePlayer.CurSong curSong = m_player.getCurSong();
			((TextView)findViewById(R.id.curSongTextView)).setText(curSong == null ? "" :
				String.format("%d. %s", curSong.posInList + 1, curSong.title));

			((SeekBar)findViewById(R.id.seekBar)).setMax(
				curSong == null ? 0 : (int)(curSong.length / 1000000));

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

	public void onRefresh(final Object param) {
		m_handler.post(new Runnable() {
			@Override
			public void run() {
				refreshGui(param);
			}
		});
	}

	private void refreshAll(Object refreshParam) {
		if (isConnected())
			m_player.refresh(refreshParam);
	}
	
	private void tryConnect(final Object refreshParam) {
		if (m_player != null) {
			m_player.destroy();
			m_player = null;
		}

		RemoteAddr addr = new RemoteAddr(this);
		m_player = new RemotePlayer(addr.getAddr(), addr.getPort(), this, this,
				new RemotePlayer.IOnConnectedHandler() {
					@Override 
					public void onConnected() {
						refreshAll(refreshParam);
					}
				});
	}

	private boolean isConnected() {
		return (m_player != null && m_player.isConnected());
	}

	private boolean isPlaying() {
		return (isConnected() && m_player.isPlaying());
	}

	public void processNotification(String msg) {
		if (m_notificationsDisabled)
			return;
		
		m_handler.post(new Runnable() {
			@Override
			public void run() {
				refreshAll(null);
			}
		});
	}

	class Receiver extends BroadcastReceiver {
		public void onReceive(Context ctx, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Intent.ACTION_SCREEN_ON)) {
				refreshAll(null);
			}
			else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
				String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
				if (state.equals(TelephonyManager.EXTRA_STATE_RINGING) ||
						state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
					refreshAll(null);
					if (isPlaying())
						m_player.pause();
				}
			}
		}
	}

	private Runnable m_updateTimeTask = new Runnable() {
		public void run() {
			if (!isPlaying())
				return;

			m_player.incrementCurTime(TIME_UPDATE_INTERVAL);
			updateCurTimeView();

			m_handler.postDelayed(this, TIME_UPDATE_INTERVAL);
		}
	};

	private class MyAdapter extends BaseAdapter {
		private RemotePlayer m_player;

		public MyAdapter(RemotePlayer player) {
			m_player = player;
		}

		@Override
		public int getCount() {
			return (m_player.getPlayList() == null ? 0 : m_player.getPlayList().size());
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
			int len = (int)(s.length / 1000000000);
			tv_len.setText(String.format("%d:%02d", len / 60, len % 60));

			int col = (m_player.getCurSong() != null && 
					   position == m_player.getCurSong().posInList) ?
					Color.RED : Color.LTGRAY;
			tv_title.setTextColor(col);
			tv_len.setTextColor(col);
			return vg;
		}
	}

	private class VolumeDialogFragment extends DialogFragment
		implements
			SeekBar.OnSeekBarChangeListener, View.OnClickListener {

		private RemotePlayer m_player;
		private SeekBar sb_vol;
		private CheckBox cb_def;
		private double m_origVol;

		public VolumeDialogFragment(RemotePlayer player) {
			m_player = player;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			LayoutInflater inflater = getActivity().getLayoutInflater();
			ViewGroup vg = (ViewGroup)inflater.inflate(R.layout.volume, null);

			sb_vol = (SeekBar)vg.findViewById(R.id.volume_seekBar);
			cb_def = (CheckBox)vg.findViewById(R.id.volume_setDefault);

			m_origVol = m_player.getVolume();

			sb_vol.setOnSeekBarChangeListener(this);
			cb_def.setOnClickListener(this);
			sb_vol.setProgress((int)(m_origVol * 1000000));
			cb_def.setChecked(m_player.getVolume() == 1);

			builder.setView(vg)
				   .setPositiveButton("OK", new DialogInterface.OnClickListener() {
					   public void onClick(DialogInterface dialog, int id) {
					   }
				   })
				   .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					   public void onClick(DialogInterface dialog, int id) {
					   		m_player.setVolume(m_origVol);
					   }
				   });
			return builder.create();
		}

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			if (fromUser && isConnected()) {
				double v = (double)progress / 1000000;
				m_player.setVolume(v);
				cb_def.setChecked(v == 1);
			}
		}
		
		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
		}
		
		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
		}
	
		@Override
		public void onClick(View view) {
			if (view == cb_def) {
				double v = 1;
				sb_vol.setProgress((int)(v * 1000000));
				m_player.setVolume(v);
			}
		}
	
	}
}
