package com.sgsoftware.mpfcremote;

import android.content.SharedPreferences;

public class RemoteAddr {
	SharedPreferences m_prefs;
	String m_addr;
	int m_port;

	public RemoteAddr(android.content.Context ctx) {
		m_prefs = ctx.getSharedPreferences("com.sgsoftware.mpfcremote_preferences", 0);
		m_addr = m_prefs.getString("RemoteAddr", "");
		m_port = Integer.parseInt(m_prefs.getString("RemotePort", "19792"));
	}

	public String getAddr() { return m_addr; }
	public int getPort() { return m_port; }

	public void setPort(int p) {
		SharedPreferences.Editor editor = m_prefs.edit();
		editor.putString("RemotePort", Integer.toString(p));
		editor.commit();
	}
}
