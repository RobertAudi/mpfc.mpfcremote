package com.sgsoftware.mpfcremote;

import javax.net.SocketFactory;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class RemotePlayer {
	
	WriteThread m_writeThread;

	public interface IResponseHandler
	{
		public void processResponse(String s);
	}

	public interface IRefreshHandler
	{
		public void onRefresh();
	}

	public enum PlayStatus
	{
		PLAYING, STOPPED, PAUSED;

		public static PlayStatus parse(String s)
		{
			if (s.equals("playing"))
				return PLAYING;
			else if (s.equals("paused"))
				return PAUSED;
			else if (s.equals("stopped"))
				return STOPPED;
			else
				return STOPPED;
		}
	}

	public class CurSong
	{
		String title;
		int length;
		int curPos;
		int posInList;
		PlayStatus status;
	}

	public class Song
	{
		String name;
		int length;
	}

	private CurSong m_curSong;
	private ArrayList<Song> m_playList;
	private int m_totalLength;

	private IRefreshHandler m_refreshHandler;
	
	public RemotePlayer(String addr, int port,
						INotificationHandler notificationHandler,
						IRefreshHandler refreshHandler) 
	{
		m_refreshHandler = refreshHandler;
		m_writeThread = new WriteThread(addr, port, notificationHandler);
		m_writeThread.start();
	}

	public boolean isConnected()
	{
		return m_writeThread.isConnected();
	}

	public void destroy()
	{
		m_writeThread.interrupt();
	}

	synchronized public CurSong getCurSong()
	{
		return m_curSong;
	}

	synchronized public ArrayList<Song> getPlayList()
	{
		return m_playList;
	}

	synchronized public int getTotalLength()
	{
		return m_totalLength;
	}
	
	public void pause()
	{
		send("pause\n", null);
	}
	
	public void next()
	{
		send("next\n", null);
	}
	
	public void prev()
	{
		send("prev\n", null);
	}
	
	public void timeBack()
	{
		send("time_back\n", null);
	}

	public void play(int pos)
	{
		send(String.format("play %d\n", pos), null);
	}

	public void seek(int t)
	{
		send(String.format("seek %d\n", t), null);
	}
	
	public void refresh()
	{
		syncCurSong();
		syncPlaylist();
	}
	
	public void clear()
	{
		send("clear_playlist\n", null);
	}

	public void add(String name)
	{
		// Escape special symbols
		name = name.replace("*", "\\*");
		name = name.replace("?", "\\?");
		name = name.replace("[", "\\[");
		name = name.replace("]", "\\]");
		name = name.replace("~", "\\~");
		send(String.format("add \"%s\"\n", name), null);
	}

	public void removeSong(int pos)
	{
		send(String.format("remove %d\n", pos), null);
	}

	public void queueSong(int pos)
	{
		send(String.format("queue %d\n", pos), null);
	}

	public class DirEntry implements Comparable
	{
		public String name;
		public boolean isDir;
		
		public int compareTo(Object o)
		{
			return name.compareTo(((DirEntry)o).name);
		}
	}

	void listDir(String dir, IResponseHandler h)
	{
		send(String.format("list_dir \"%s\"\n", dir), h);
	}

	public void incrementCurTime(int ms) {
		if (m_curSong == null)
			return;
		m_curSong.curPos += ms/1000;
	}

	public boolean isPlaying() {
		if (m_curSong == null)
			return false;
		return (m_curSong.status == RemotePlayer.PlayStatus.PLAYING);
	}

	private void syncPlaylist()
	{
		send("get_playlist\n", new IResponseHandler() {
			public void processResponse(String s) {
				parsePlaylist(s);
				m_refreshHandler.onRefresh();
			}
		});
	}
	
	private void syncCurSong()
	{
		send("get_cur_song\n", new IResponseHandler() {
			public void processResponse(String s) {
				parseCurSong(s);
				m_refreshHandler.onRefresh();
			}
		});
	}

	synchronized private void parsePlaylist(String s)
	{
		m_playList = new ArrayList<Song>();
		m_totalLength = 0;

		try
		{
			JSONArray js = new JSONArray(new JSONTokener(s));
			for ( int i = 0; i < js.length(); i++ )
			{
				JSONObject obj = js.getJSONObject(i);
				Song song = new Song();
				song.name = obj.getString("title");
				song.length = obj.getInt("length");
				m_totalLength += song.length;
				m_playList.add(song);
			}
		}
		catch (org.json.JSONException e)
		{
		}
	}

	synchronized private void parseCurSong(String s)
	{
		try
		{
			JSONObject js = new JSONObject(new JSONTokener(s));
			m_curSong = new CurSong();
			m_curSong.title = js.getString("title");
			m_curSong.length = js.getInt("length");
			m_curSong.curPos = js.getInt("time");
			m_curSong.posInList = js.getInt("position");
			m_curSong.status = PlayStatus.parse(js.getString("play_status"));
		}
		catch (org.json.JSONException e)
		{
			m_curSong = null;
		}
	}
	
	public DirEntry[] parseListDir(String s)
	{
		try
		{
			JSONArray js = new JSONArray(new JSONTokener(s));
			DirEntry[] res = new DirEntry[js.length()];
			for ( int i = 0; i < js.length(); i++ )
			{
				JSONObject obj = js.getJSONObject(i);
				res[i] = new DirEntry();
				res[i].name = obj.getString("name");
				res[i].isDir = (obj.getString("type").equals("d"));
			}
			java.util.Arrays.sort(res);
			return res;
		}
		catch (org.json.JSONException e)
		{
			return new DirEntry[] {};
		}
	}

	private void send(String s, IResponseHandler h)
	{
		m_writeThread.addRequest(s, h);
	}


	public enum MsgType
	{
		RESPONSE, NOTIFICATION
	}

	private class ReadThread extends Thread
	{
		InputStream m_stream;
		LinkedBlockingQueue<IResponseHandler> m_respHandlers;
		INotificationHandler m_notificationHandler;
		
		private class Header
		{
			public int msgLen;
			public MsgType msgType;

			public Header(int len, MsgType type)
			{
				msgLen = len;
				msgType = type;
			}
		}

		public ReadThread(InputStream stream, INotificationHandler notificationHandler)
		{
			m_stream = stream;
			m_respHandlers = new LinkedBlockingQueue<IResponseHandler>();
			m_notificationHandler = notificationHandler;
		}

		synchronized public void addResponseHandler(IResponseHandler h)
		{
			try {
				m_respHandlers.put(h);
			}
			catch (java.lang.InterruptedException e) {
				return;
			}
		}
		
		@Override
		public void run()
		{
			while (true)
			{
				try
				{
					readMsg();
				}
				catch (java.io.IOException e)
				{
					break;
				}
				catch (java.lang.InterruptedException e)
				{
					break;
				}
			}
		}

		private void readMsg()
			throws java.io.IOException, java.lang.InterruptedException
		{
			Header h = readHeader();
			if (h == null)
				return;

			byte[] bs = new byte[h.msgLen];
			if (!readExact(bs))
				return;

			if (h.msgType == MsgType.RESPONSE)
			{
				IResponseHandler handler = m_respHandlers.poll();
				if (handler == null)
					return;
				handler.processResponse(new String(bs));
			}
			else if (h.msgType == MsgType.NOTIFICATION)
			{
				m_notificationHandler.processNotification(new String(bs));
			}
		}

		private Header readHeader()
			throws java.io.IOException
		{
			// Read 'Msg-Length: '
			if (!readExactString("Msg-Length: "))
				return null;

			// Read length itself
			int len = 0;
			for ( ;; )
			{
				int b = m_stream.read();
				if (b >= '0' && b <= '9')
				{
					len *= 10;
					len += (b - '0');
				}
				else if (b == '\n')
					break;
				else
					return null;
			}

			// Read 'Msg-Type: '
			if (!readExactString("Msg-Type: "))
				return null;

			// Read message type
			int b = m_stream.read();
			m_stream.read();
			if (b == 'r')
				return new Header(len, MsgType.RESPONSE);
			else if (b == 'n')
				return new Header(len, MsgType.NOTIFICATION);
			else
				return null;
		}

		private boolean readExactString(String s)
			throws java.io.IOException
		{
			byte[] bs = new byte[s.length()];
			if (!readExact(bs))
				return false;
			String rs = new String(bs);
			if (!rs.equals(s))
				return false;
			return true;
		}

		private boolean readExact(byte[] bs)
			throws java.io.IOException
		{
			int len = bs.length;
			int off = 0;
			while (len > 0)
			{
				int r = m_stream.read(bs, off, len);
				if (r < 0)
					return false;
				len -= r;
				off += r;
			}
			return true;
		}
	}

	private class WriteThread extends Thread
	{
		public class Request
		{
			public String msg;
			public IResponseHandler responseHandler;
		}
		LinkedBlockingQueue<Request> m_reqQueue;

		boolean m_connected;
		Socket m_sock;
		OutputStream m_output;
		INotificationHandler m_notificationHandler;

		ReadThread m_readThread;

		String m_addr;
		int m_port;
		
		public WriteThread(String addr, int port, INotificationHandler handler)
		{
			m_addr = addr;
			m_port = port;

			m_reqQueue = new LinkedBlockingQueue<Request>();
			m_connected = false;
			m_notificationHandler = handler;
		}

		public boolean isConnected() {
			return m_connected;
		}

		synchronized public void addRequest(String msg, IResponseHandler h)
		{
			Request r = new Request();
			r.msg = msg;
			r.responseHandler = h;
			try {
				m_reqQueue.put(r);
			}
			catch (java.lang.InterruptedException e) {
				return;
			}
		}

		@Override
		public void run()
		{
			// Try connect to the server
			InputStream inputStream;
			try {
				SocketFactory sockFactory = SocketFactory.getDefault();
				m_sock = sockFactory.createSocket(m_addr, m_port);
				m_output = m_sock.getOutputStream();
				inputStream = m_sock.getInputStream();
			}
			catch (java.net.UnknownHostException e) {
				return;
			}
			catch (java.io.IOException e) {
				return;
			}
			m_connected = true;

			// Spawn reader thread
			m_readThread = new ReadThread(inputStream, m_notificationHandler);
			m_readThread.start();
		
			// Handle requests
			while (true)
			{
				try
				{
					Request r = m_reqQueue.poll(3600, java.util.concurrent.TimeUnit.SECONDS);
					if (r == null)
						continue;

					try
					{
						m_output.write(r.msg.getBytes());
						if (r.responseHandler != null)
							m_readThread.addResponseHandler(r.responseHandler);
					}
					catch (java.io.IOException e)
					{
						continue;
					}
				}
				catch (java.lang.InterruptedException e)
				{
					break;
				}
			}

			m_readThread.interrupt();
			try {
				m_output.write("bye\n".getBytes());
				m_sock.close();
			}
			catch (java.io.IOException e)
			{ }

		}

	}
}
