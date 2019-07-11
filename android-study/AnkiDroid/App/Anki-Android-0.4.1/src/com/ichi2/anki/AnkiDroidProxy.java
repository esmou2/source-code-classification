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

package com.ichi2.anki;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;

import android.util.Log;

import com.ichi2.utils.StringUtils;

public class AnkiDroidProxy {

	private static final String TAG = "AnkidroidSharedDecks";

	private static final int R_ID = 0;
	private static final int R_USERNAME = 1;
	private static final int R_TITLE = 2;
	private static final int R_DESCRIPTION = 3;
	private static final int R_TAGS = 4;
	private static final int R_VERSION = 5;
	private static final int R_FACTS = 6;
	private static final int R_SIZE = 7;
	private static final int R_COUNT = 8;
	private static final int R_MODIFIED = 9;
	private static final int R_FNAME = 10;
	
	private static final int CHUNK_SIZE = 32768;
	
	private static List<SharedDeck> sharedDecks;
	
	public static List<SharedDeck> getSharedDecks() throws Exception {
		
		try {
			if(sharedDecks == null)
			{
				sharedDecks = new ArrayList<SharedDeck>();
				
				HttpGet httpGet = new HttpGet("http://anki.ichi2.net/file/search");
		    	httpGet.setHeader("Accept-Encoding", "identity");
		    	httpGet.setHeader("Host", "anki.ichi2.net");
		    	DefaultHttpClient defaultHttpClient = new DefaultHttpClient();
	
		    	HttpResponse httpResponse = defaultHttpClient.execute(httpGet);
				String response = StringUtils.convertStreamToString(httpResponse.getEntity().getContent());
				//Log.i(TAG, "Content = " + response);
				sharedDecks.addAll(handleResult(response));
			}
		} catch (Exception e)
		{
			sharedDecks = null;
			throw new Exception();
		}
		
		return sharedDecks;
	}
	
    private static List<SharedDeck> handleResult(String result) throws JSONException
    {
    	List<SharedDeck> sharedDecks = new ArrayList<SharedDeck>();
    	
		JSONArray jsonSharedDecks = new JSONArray(result);
		
		if(jsonSharedDecks != null)
		{
			//Log.i(TAG, "Number of shared decks = " + jsonSharedDecks.length());
			
			for(int i = 0; i < jsonSharedDecks.length(); i++)
			{
				JSONArray jsonSharedDeck = jsonSharedDecks.getJSONArray(i);
				
				SharedDeck sharedDeck = new SharedDeck();
				sharedDeck.setId(jsonSharedDeck.getInt(R_ID));
				sharedDeck.setUsername(jsonSharedDeck.getString(R_USERNAME));
				sharedDeck.setTitle(jsonSharedDeck.getString(R_TITLE));
				sharedDeck.setDescription(jsonSharedDeck.getString(R_DESCRIPTION));
				sharedDeck.setTags(jsonSharedDeck.getString(R_TAGS));
				sharedDeck.setVersion(jsonSharedDeck.getInt(R_VERSION));
				sharedDeck.setFacts(jsonSharedDeck.getInt(R_FACTS));
				sharedDeck.setSize(jsonSharedDeck.getInt(R_SIZE));
				sharedDeck.setCount(jsonSharedDeck.getInt(R_COUNT));
				sharedDeck.setModified(jsonSharedDeck.getDouble(R_MODIFIED));
				sharedDeck.setFileName(jsonSharedDeck.getString(R_FNAME));
				
				//sharedDeck.prettyLog();
				
				sharedDecks.add(sharedDeck);
			}
		}
		
		return sharedDecks;
    }

	public static String downloadSharedDeck(SharedDeck sharedDeck, String deckPath) throws ClientProtocolException, IOException {
    	Log.i(TAG, "Downloading deck " + sharedDeck.getId());
    	
		HttpGet httpGet = new HttpGet("http://anki.ichi2.net/file/get?id=" + sharedDeck.getId());
		httpGet.setHeader("Accept-Encoding", "identity");
		httpGet.setHeader("Host", "anki.ichi2.net");
		httpGet.setHeader("Connection", "close");
		DefaultHttpClient httpClient = new DefaultHttpClient();
		HttpResponse httpResponse = httpClient.execute(httpGet);
		Log.i(TAG, "Connection finished!");
		InputStream is = httpResponse.getEntity().getContent();
		String deckFilename = handleFile(is, sharedDeck, deckPath);
		is.close();
		
		return deckFilename;
	}
	
	private static String handleFile(InputStream source, SharedDeck sharedDeck, String deckPath) throws IOException
	{
		String deckFilename = "";
		
		ZipInputStream zipInputStream = null;
		if(sharedDeck.getFileName().endsWith(".zip"))
		{
			zipInputStream = new ZipInputStream(source);
			
			String title = sharedDeck.getTitle();
			title = title.replace("^", "");
			title = title.substring(0, java.lang.Math.min(title.length(), 40));
			
			if(new File(deckPath + "/" + title + ".anki").exists())
				title += System.currentTimeMillis();
			
			String partialDeckPath = deckPath + "/" + title;
			deckFilename = partialDeckPath + ".anki";
			
			ZipEntry zipEntry = null;
			while((zipEntry = zipInputStream.getNextEntry()) != null)
			{
				//Log.i(TAG, "zipEntry = " + zipEntry.getName());
				
				if("shared.anki".equalsIgnoreCase(zipEntry.getName()))
				{
					writeToFile(zipInputStream, deckFilename);
				}
				else if(zipEntry.getName().startsWith("shared.media/", 0))
				{
					//Log.i(TAG, "Folder created = " + new File(AnkiDroidApp.getStorageDirectory() + title + ".media/").mkdir());
					//Log.i(TAG, "Destination = " + AnkiDroidApp.getStorageDirectory() + "/" + title + ".media/" + zipEntry.getName().replace("shared.media/", ""));
					writeToFile(zipInputStream, partialDeckPath + ".media/" + zipEntry.getName().replace("shared.media/", ""));
				}
			}
			zipInputStream.close();
		}
		
		return deckFilename;
	}
	
	/**
	 * Utility method to write to a file.
	 */
	private static boolean writeToFile(InputStream source, String destination)
	{
		//Log.i(TAG, "writeToFile = " + destination);
		try
		{
			//Log.i(TAG, "createNewFile");
			new File(destination).createNewFile();
			//Log.i(TAG, "New file created");
	
			OutputStream output = new FileOutputStream(destination);
			
			// Transfer bytes, from source to destination.
			byte[] buf = new byte[CHUNK_SIZE];
			int len;
			if(source == null) Log.i(TAG, "source is null!");
			while ((len = source.read(buf)) > 0)
			{
				//Log.i(TAG, "Writing to file...");
				output.write(buf, 0, len);
			}
			
			//source.close();
			output.close();
			//Log.i(TAG, "Write finished!");

		} catch (Exception e) {
			// Most probably the SD card is not mounted on the Android.
			// Tell the user to turn off USB storage, which will automatically
			// mount it on Android.
			//Log.i(TAG, "IOException e = " + e.getMessage());
			return false;
		}
		return true;
	}
}
