/***************************************************************************************
* Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
*                                                                                      *
* This program is free software; you can redistribute it and/or modify it under        *
* the terms of the GNU General Public License as published by the Free Software        *
* Foundation; either version 3 of the License, or (at your option) any later           *
* version.                                                                             *
*                                                                                      *
* This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
* PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
*                                                                                      *
* You should have received a copy of the GNU General Public License along with         *
* this program.  If not, see <http://www.gnu.org/licenses/>.                           *
****************************************************************************************/

package com.ichi2.async;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.util.Log;

import com.ichi2.anki.AnkiDroidProxy;
import com.ichi2.anki.SharedDeck;

public class Connection extends AsyncTask<Connection.Payload, Object, Connection.Payload>
{
    public static final String TAG = "Connection";
    public static Context context;
    
    public static final int TASK_TYPE_GET_SHARED_DECKS = 0;
    public static final int TASK_TYPE_DOWNLOAD_SHARED_DECK = 1;

	private static Connection instance;
	private TaskListener listener;
	
	private static Connection launchConnectionTask(TaskListener listener, Payload data)
	{
		if(!isOnline())
		{
			data.success = false;
			listener.onDisconnected();
			return null;
		}
		try
		{
			if ((instance != null) && (instance.getStatus() != AsyncTask.Status.FINISHED))
				instance.get();
		} catch (Exception e)
		{
			e.printStackTrace();
		}

		instance = new Connection();
		instance.listener = listener;

		return (Connection)instance.execute(data);
	}
	
	   /*
     * Runs on GUI thread
     */
    protected void onPreExecute() 
    {
    	listener.onPreExecute();
    }

    /*
     * Runs on GUI thread
     */
    public void onPostExecute(Payload data)
    {
    	listener.onPostExecute(data);
    }

    /*
     * Runs on GUI thread
     */
    public void onProgressUpdate(Object... values)
    {
    	listener.onProgressUpdate(values);
    }
    
	public static Connection getSharedDecks(TaskListener listener, Payload data)
	{
		data.taskType = TASK_TYPE_GET_SHARED_DECKS;
		return launchConnectionTask(listener, data);
	}
	
	public static Connection downloadSharedDeck(TaskListener listener, Payload data)
	{
		data.taskType = TASK_TYPE_DOWNLOAD_SHARED_DECK;
		return launchConnectionTask(listener, data);
	}
	
	@Override
	protected Payload doInBackground(Payload... params) {
		Payload data = params[0];
		
		switch(data.taskType)
		{
			case TASK_TYPE_GET_SHARED_DECKS:
				return doInBackgroundGetSharedDecks(data);

			case TASK_TYPE_DOWNLOAD_SHARED_DECK:
				return doInBackgroundDownloadSharedDeck(data);
				
			default:
				return null;
		}
	}

	private Payload doInBackgroundGetSharedDecks(Payload data)
	{
		try {
			data.result = AnkiDroidProxy.getSharedDecks();
		} catch (Exception e) {
			data.success = false;
			data.exception = e;
			Log.e(TAG, "Error getting shared decks = " + e.getMessage());
			e.printStackTrace();
		}
		return data;
	}

	private Payload doInBackgroundDownloadSharedDeck(Payload data)
	{
		try {
			data.result = AnkiDroidProxy.downloadSharedDeck((SharedDeck)data.data[0], (String)data.data[1]);
		} catch (Exception e) {
			data.success = false;
			data.exception = e;
			Log.e(TAG, "Error downloading shared deck = " + e.getMessage());
			e.printStackTrace();
		}
		return data;
	}
	
	public static boolean isOnline() 
	{
		 ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		 
		 if(cm.getActiveNetworkInfo() != null)
			 return cm.getActiveNetworkInfo().isConnectedOrConnecting();
		 else return false;
	}

	public static void setContext(Context applicationContext)
	{
		context = applicationContext;
	}
	
	public static interface TaskListener
	{
		public void onPreExecute();

		public void onProgressUpdate(Object... values);
		
		public void onPostExecute(Payload data);
		
		public void onDisconnected();
	}
	
    public static class Payload
    {
        public int taskType;
        public Object[] data;
        public Object result;
        public boolean success;
        public int errorType;
        public Exception exception;

        public Payload(Object[] data) {
        	this.data = data;
        	this.success = true;
        }
        
        public Payload(int taskType, Object[] data) {
            this.taskType = taskType;
            this.data = data;
            this.success = true;
        }
    }
}

