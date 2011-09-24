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
			for ( int i = 0; i < count; i++ ) {
				CheckBox cb = (CheckBox)((ViewGroup)(getListView().getChildAt(i))).getChildAt(0);
				boolean ch = cb.isChecked();
				if (ch)
					MainActivity.getPlayer().add(m_curDir + "/" + m_entries[i].name);
			}
			break;
		}
			
		}
	}

	@Override
	public void onItemClick(AdapterView<?> a, View v, int position, long id) {
		if (!m_entries[position].isDir)
			return;
		
		if (m_curDir != "/")
			m_curDir += "/";
		m_curDir += m_entries[position].name;
		loadDir();
	}
	
	private void loadDir() {
		getEntries();

		String[] pp = new String[m_entries.length];
		for ( int i = 0; i < m_entries.length; i++ ) {
			pp[i] = m_entries[i].name;
		}
		ArrayAdapter<String> adapter = 
			new ArrayAdapter<String>(this, R.layout.playlist_edit_row, R.id.row_tv, pp);
		setListAdapter(adapter);
		
		((TextView)findViewById(R.id.playlist_curdir)).setText(m_curDir);
	}

	private void getEntries() {
		m_entries = MainActivity.getPlayer().listDir(m_curDir);
	}
}

