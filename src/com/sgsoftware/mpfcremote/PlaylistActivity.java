package com.sgsoftware.mpfcremote;

import android.os.Bundle;
import android.app.ListActivity;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.sgsoftware.mpfcremote.RemotePlayer;
import com.sgsoftware.mpfcremote.MainActivity;

public class PlaylistActivity extends ListActivity 
	implements View.OnClickListener, AdapterView.OnItemClickListener {

	String m_curDir;
	boolean m_hasParent;
	RemotePlayer.DirEntry[] m_entries;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.playlist_edit);
		getListView().setChoiceMode(ListView.CHOICE_MODE_NONE);

		((Button)findViewById(R.id.playlist_add)).setOnClickListener(this);
		((Button)findViewById(R.id.playlist_clear)).setOnClickListener(this);
		getListView().setOnItemClickListener(this);

		m_curDir = "/";
		loadDir();

	}

	@Override
	public void onClick(View target) {
		switch (target.getId()) {
		case R.id.playlist_clear: {
			MainActivity.getPlayer().clear();
			break;
		}
		case R.id.playlist_add: {
			int count = getListView().getChildCount();
			int i = 0, ei = 0;
			if (m_hasParent)
				i++;
			for ( ; i < count; i++, ei++ ) {
				CheckBox cb = (CheckBox)((ViewGroup)(getListView().getChildAt(i))).getChildAt(0);
				boolean ch = cb.isChecked();
				if (ch)
					MainActivity.getPlayer().add(m_curDir + "/" + m_entries[ei].name);
			}
			break;
		}
			
		}
	}

	@Override
	public void onItemClick(AdapterView<?> a, View v, int position, long id) {
		if (m_hasParent) {
			if (position == 0) {
				int l = m_curDir.length() - 1;
				while (m_curDir.charAt(l) == '/')
					l--;
				l = m_curDir.lastIndexOf('/', l) + 1;
				m_curDir = m_curDir.substring(0, l);
				loadDir();
				return;
			}

			position--;
		}

		if (!m_entries[position].isDir)
			return;
		
		m_curDir += m_entries[position].name;
		m_curDir += "/";
		loadDir();
	}
	
	private void loadDir() {
		getEntries();

		m_hasParent = (m_curDir != "/");

		String[] pp = new String[m_entries.length + (m_hasParent ? 1 : 0)];
		int curPos = 0;
		if (m_hasParent)
			pp[curPos++] = "..";
		for ( int i = 0; i < m_entries.length; i++ ) {
			pp[curPos++] = m_entries[i].name;
		}
		ArrayAdapter<String> adapter = 
			new ArrayAdapter<String>(this, R.layout.playlist_edit_row, R.id.row_tv, pp);
		setListAdapter(adapter);
		
		((TextView)findViewById(R.id.playlist_curdir)).setText(m_curDir);
		getListView().setSelection(0);
	}

	private void getEntries() {
		m_entries = MainActivity.getPlayer().listDir(m_curDir);
	}
}

