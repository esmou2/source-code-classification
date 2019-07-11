/****************************************************************************************
* Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
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

import java.util.Iterator;
import java.util.LinkedList;

import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Loading in the background, so that AnkiDroid does not look like frozen.
 */
public class DeckTask extends AsyncTask<DeckTask.TaskData, DeckTask.TaskData, DeckTask.TaskData>
{

	/**
	 * Tag for logging messages
	 */
	private static final String TAG = "AnkiDroid";

	public static final int TASK_TYPE_LOAD_DECK = 0;
	public static final int TASK_TYPE_ANSWER_CARD = 1;
	public static final int TASK_TYPE_SUSPEND_CARD = 2;
    public static final int TASK_TYPE_UPDATE_FACT = 3;

	private static DeckTask instance;
	private static DeckTask oldInstance;

	int type;
	TaskListener listener;

	public static DeckTask launchDeckTask(int type, TaskListener listener, TaskData... params)
	{
		oldInstance = instance;
		
		instance = new DeckTask();
		instance.listener = listener;
		instance.type = type;

		return (DeckTask) instance.execute(params);
	}
	
	/**
	 * Block the current thread until the currently running DeckTask instance 
	 * (if any) has finished.
	 */
	public static void waitToFinish()
	{
		try
		{
			if ((instance != null) && (instance.getStatus() != AsyncTask.Status.FINISHED))
				instance.get();
		} catch (Exception e)
		{
			return;
		}
	}

	@Override
	protected TaskData doInBackground(TaskData... params)
	{
		// Wait for previous thread (if any) to finish before continuing
		try
		{
			if ((oldInstance != null) && (oldInstance.getStatus() != AsyncTask.Status.FINISHED))
				oldInstance.get();
		} catch (Exception e)
		{
			Log.e(TAG, "doInBackground - Got exception while waiting for thread to finish: " + e.getMessage());
		}
		
		switch (type)
		{
		case TASK_TYPE_LOAD_DECK:
			return doInBackgroundLoadDeck(params);
		case TASK_TYPE_ANSWER_CARD:
			return doInBackgroundAnswerCard(params);
		case TASK_TYPE_SUSPEND_CARD:
			return doInBackgroundSuspendCard(params);
        case TASK_TYPE_UPDATE_FACT:
            return doInBackgroundUpdateFact(params);
		default:
			return null;
		}
	}

	@Override
	protected void onPreExecute()
	{
		listener.onPreExecute();
	}

	@Override
	protected void onProgressUpdate(TaskData... values)
	{
		listener.onProgressUpdate(values);
	}

	@Override
	protected void onPostExecute(TaskData result)
	{
		listener.onPostExecute(result);
	}

    private TaskData doInBackgroundUpdateFact(TaskData[] params) {

// Save the fact
        Deck deck = params[0].getDeck();
        Card editCard = params[0].getCard();
        Fact editFact = editCard.fact;
        editFact.toDb();
        LinkedList<Card> saveCards = editFact.getUpdatedRelatedCards();
        
        Iterator<Card> iter = saveCards.iterator();
        while (iter.hasNext())
        {
            Card modifyCard = iter.next();
            deck.updateCard(modifyCard);
        }
        // Find all cards based on this fact and update them with the updateCard method.

        publishProgress(new TaskData(deck.getCurrentCard()));
      
        return null;
    }

    

	private TaskData doInBackgroundAnswerCard(TaskData... params)
	{
		long start, start2;
		Deck deck = params[0].getDeck();
		Card oldCard = params[0].getCard();
		int ease = params[0].getInt();
		Card newCard;
		
		start2 = System.currentTimeMillis();
		
		AnkiDb.database.beginTransaction();
		try 
		{
			if (oldCard != null)
			{
				start = System.currentTimeMillis();
				deck.answerCard(oldCard, ease);
				Log.v(TAG, "doInBackgroundAnswerCard - Answered card in " + (System.currentTimeMillis() - start) + " ms.");
			}
	
			start = System.currentTimeMillis();
			newCard = deck.getCard();
			Log.v(TAG, "doInBackgroundAnswerCard - Loaded new card in " + (System.currentTimeMillis() - start) + " ms.");
			publishProgress(new TaskData(newCard));
			
			AnkiDb.database.setTransactionSuccessful();
		} finally 
		{
			AnkiDb.database.endTransaction();
		}
		
		Log.w(TAG, "doInBackgroundAnswerCard - DB operations in " + (System.currentTimeMillis() - start2) + " ms.");

		return null;
	}

	private TaskData doInBackgroundLoadDeck(TaskData... params)
	{
		String deckFilename = params[0].getString();
		Log.i(TAG, "doInBackgroundLoadDeck - deckFilename = " + deckFilename);

		Log.i(TAG, "loadDeck - SD card mounted and existent file -> Loading deck...");
		try
		{
			// Open the right deck.
			Deck deck = Deck.openDeck(deckFilename);
			// Start by getting the first card and displaying it.
			Card card = deck.getCard();
			Log.i(TAG, "Deck loaded!");

			return new TaskData(Ankidroid.DECK_LOADED, deck, card);
		} catch (SQLException e)
		{
			Log.i(TAG, "The database " + deckFilename + " could not be opened = " + e.getMessage());
			return new TaskData(Ankidroid.DECK_NOT_LOADED);
		} catch (CursorIndexOutOfBoundsException e)
		{
			Log.i(TAG, "The deck has no cards = " + e.getMessage());;
			return new TaskData(Ankidroid.DECK_EMPTY);
		}
	}
	
	private TaskData doInBackgroundSuspendCard(TaskData... params)
	{
		long start, stop;
		Deck deck = params[0].getDeck();
		Card oldCard = params[0].getCard();
		Card newCard;

		AnkiDb.database.beginTransaction();
		try 
		{
			if (oldCard != null)
			{
				start = System.currentTimeMillis();
				deck.suspendCard(oldCard.id);
				stop = System.currentTimeMillis();
				Log.v(TAG, "doInBackgroundSuspendCard - Suspended card in " + (stop - start) + " ms.");
			}
	
			start = System.currentTimeMillis();
			newCard = deck.getCard();
			stop = System.currentTimeMillis();
			Log.v(TAG, "doInBackgroundSuspendCard - Loaded new card in " + (stop - start) + " ms.");
			publishProgress(new TaskData(newCard));
			AnkiDb.database.setTransactionSuccessful();
		} finally 
		{
			AnkiDb.database.endTransaction();
		}
		
		return null;
	}

	public static interface TaskListener
	{
		public void onPreExecute();

		public void onPostExecute(TaskData result);

		public void onProgressUpdate(TaskData... values);
	}

	public static class TaskData
	{
		private Deck deck;
		private Card card;
		private int integer;
		private String msg;

		public TaskData(int value, Deck deck, Card card)
		{
			this(value);
			this.deck = deck;
			this.card = card;
		}

		public TaskData(Card card)
		{
			this.card = card;
		}

		public TaskData(int value)
		{
			this.integer = value;
		}

		public TaskData(String msg)
		{
			this.msg = msg;
		}

		public Deck getDeck()
		{
			return deck;
		}

		public Card getCard()
		{
			return card;
		}

		public int getInt()
		{
			return integer;
		}

		public String getString()
		{
			return msg;
		}

	}

}
