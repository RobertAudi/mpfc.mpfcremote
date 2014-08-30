package com.sgsoftware.mpfcremote;

import java.util.BitSet;
import java.util.Stack;

import android.os.Bundle;
import android.app.ListActivity;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.CompoundButton;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.AdapterView;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.sgsoftware.mpfcremote.RemotePlayer;
import com.sgsoftware.mpfcremote.MainActivity;

public class PlaylistActivity extends ListActivity 
	implements View.OnClickListener, AdapterView.OnItemClickListener {

	String m_curDir;
	boolean m_hasParent;
	RemotePlayer.DirEntry[] m_entries;
	RemotePlayer m_player;

	private Handler m_handler;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        m_handler = new Handler();

		setContentView(R.layout.playlist_edit);
		getListView().setChoiceMode(ListView.CHOICE_MODE_NONE);

		((Button)findViewById(R.id.playlist_add)).setOnClickListener(this);
		((Button)findViewById(R.id.playlist_clear)).setOnClickListener(this);
		((Button)findViewById(R.id.playlist_close)).setOnClickListener(this);
		((Button)findViewById(R.id.playlist_up)).setOnClickListener(this);
		getListView().setOnItemClickListener(this);

		m_scrollPositions = new Stack<ScrollPos>();

		m_curDir = "/";
		m_entries = new RemotePlayer.DirEntry[] {};
		refreshGui();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (m_player != null) {
			m_player.destroy();
			m_player = null;
		}

		RemoteAddr addr = new RemoteAddr(this);

		m_player = new RemotePlayer(addr.getAddr(), addr.getPort(),
				null, null, null);
		m_player.refresh(null);

		loadDir(false);

		refreshGui();
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (m_player != null) {
			m_player.destroy();
			m_player = null;
		}
	}

	private void refreshGui() {
		MyAdapter adapter = new MyAdapter(m_entries);
		setListAdapter(adapter);
		
		((TextView)findViewById(R.id.playlist_curdir)).setText(m_curDir);
		getListView().setSelection(0);
	}

	@Override
	public void onClick(View target) {
		switch (target.getId()) {
		case R.id.playlist_clear: {
			m_player.clear();
			break;
		}
		case R.id.playlist_close: {
			finish();
			break;
		}
		case R.id.playlist_add: {
		    BitSet bs = ((MyAdapter)getListAdapter()).getSelected();
			int count = bs.length();
			for ( int i = 0; i < count; i++ ) {
				if (bs.get(i))
					m_player.add(m_curDir + "/" + m_entries[i].name);
			}
			break;
		}
		case R.id.playlist_up: {
			if (!m_hasParent)
				return;

			int l = m_curDir.length() - 1;
			while (m_curDir.charAt(l) == '/')
				l--;
			l = m_curDir.lastIndexOf('/', l) + 1;
			m_curDir = m_curDir.substring(0, l);
			loadDir(true);
			break;
		}
		}
	}

	@Override
	public void onItemClick(AdapterView<?> a, View v, int position, long id) {
		if (!m_entries[position].isDir)
			return;
		
		rememberScrollPos();
		m_curDir += m_entries[position].name;
		m_curDir += "/";
		loadDir(false);
	}
	
	private void loadDir(final boolean shouldRestorePos) {
		m_player.listDir(m_curDir, new RemotePlayer.IResponseHandler() {
			public void processResponse(String s) {
				m_entries = m_player.parseListDir(s);
				m_hasParent = !m_curDir.equals("/");

				m_handler.post(new Runnable() {
					@Override
					public void run() {
						refreshGui();
						if (shouldRestorePos)
							restoreScrollPos();
					}
				});
			}

		});
	}

	private class ScrollPos {
		int pos;
		int top;
	};
	private Stack<ScrollPos> m_scrollPositions;

	private void rememberScrollPos() {
		ListView lv = getListView();
		ScrollPos sp = new ScrollPos();
		sp.pos = lv.getFirstVisiblePosition();
		View v = lv.getChildAt(0);
		sp.top = (v == null) ? 0 : v.getTop();
		m_scrollPositions.push(sp);
	}

	private void restoreScrollPos() {
		ScrollPos sp = m_scrollPositions.pop();
		getListView().setSelectionFromTop(sp.pos, sp.top);
	}

	private class MyAdapter extends BaseAdapter
			implements CompoundButton.OnCheckedChangeListener {
		private RemotePlayer.DirEntry[] m_entries;

		private BitSet m_selected;

		public MyAdapter(RemotePlayer.DirEntry[] entries) {
			m_entries = entries;
			m_selected = new BitSet(m_entries.length);
		}

		public BitSet getSelected() { return m_selected; }

		@Override
		public int getCount() {
			return m_entries.length;
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
			LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			ViewGroup vg = (ViewGroup)inflater.inflate(
					R.layout.playlist_edit_row, parent, false);
			TextView tv = (TextView)vg.findViewById(R.id.row_tv);
			CheckBox cb = (CheckBox)vg.findViewById(R.id.row_chbox);

			tv.setText(position == -1 ? ".." : m_entries[position].name);

			if (position >= 0 && m_selected.get(position))
				cb.setChecked(true);
			cb.setTag(new Integer(position));
			cb.setOnCheckedChangeListener(this);
			return vg;
		}
		
		@Override
		public void onCheckedChanged(CompoundButton b, boolean isChecked) {
			int pos = ((Integer)b.getTag()).intValue();
			if (pos >= 0)
				m_selected.set(pos, isChecked);
		}
	}
}

