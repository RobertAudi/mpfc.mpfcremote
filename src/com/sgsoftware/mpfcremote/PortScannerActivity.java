package com.sgsoftware.mpfcremote;

import java.util.ArrayList;
import android.app.ListActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

public class PortScannerActivity extends ListActivity 
	implements
		View.OnClickListener,
		AdapterView.OnItemClickListener,
		RemotePlayer.IRefreshHandler {

	class Item {
		public String title;
		public int port;

		public String toString() { return title; }
	};

	ArrayList<RemotePlayer> m_players;
	ArrayList<Item> m_listItems = new ArrayList<Item>();
	ArrayAdapter<Item> m_adapter;

	private Handler m_handler;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.scanports);

		m_handler = new Handler();

		((Button)findViewById(R.id.scanports_launch)).setOnClickListener(this);
		((TextView)findViewById(R.id.scanports_start)).setText("19792");
		((TextView)findViewById(R.id.scanports_num)).setText("10");
		getListView().setOnItemClickListener(this);

		m_adapter = new ArrayAdapter<Item>(this, R.layout.scanports_row, m_listItems);
		setListAdapter(m_adapter);

		launch();
	}

	@Override
	public void onClick(View target) {
		switch (target.getId()) {
		case R.id.scanports_launch:
			launch();
			break;
		}
	}

	@Override
	public void onItemClick(AdapterView<?> a, View v, int position, long id) {
		RemoteAddr addr = new RemoteAddr(this);
		addr.setPort(m_listItems.get(position).port);
		finish();
	}

	class PortInfo {
		public int port;
		public int idx;
	};

	private void launch() {
		clear();

		RemoteAddr addr = new RemoteAddr(this);

		int startPort = Integer.parseInt(((TextView)findViewById(R.id.scanports_start)).getText().toString());
		int numPorts = Integer.parseInt(((TextView)findViewById(R.id.scanports_num)).getText().toString());
		int endPort = startPort + numPorts - 1;

		m_players = new ArrayList<RemotePlayer>();
		for (int port = startPort; port <= endPort; ++port) {
			final int p = port;
			final PortInfo info = new PortInfo();
			info.port = port;
			info.idx = port - startPort;

			final RemotePlayer player = new RemotePlayer(this);
			m_players.add(player);

			player.connect(addr.getAddr(), port, 
					new RemotePlayer.IOnConnectedHandler() {
						@Override 
						public void onConnected() {
							player.refresh(info);
						}
					});

		}
	}

	@Override
	public void onRefresh(final Object param) {
		m_handler.post(new Runnable() {
			@Override
			public synchronized void run() {
				final PortInfo info = (PortInfo)param;

				RemotePlayer p = m_players.get(info.idx);
				String s = null;
				if (p.getPlayList().size() > 0)
					s = p.getPlayList().get(0).name + " etc";
				else
					s = "Empty playlist";

				Item item = new Item();
				item.title = String.format("[%d] %s", info.port, s);
				item.port = info.port;
				m_listItems.add(item);

				m_adapter.notifyDataSetChanged();
			}
		});
	}

	private void clear() {
		if (m_players != null) {
			for (int i = 0; i < m_players.size(); ++i)
				m_players.get(i).destroy();
		}
		m_listItems.clear();
	}
}
