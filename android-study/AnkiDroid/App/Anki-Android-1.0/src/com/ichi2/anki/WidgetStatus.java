/***************************************************************************************
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

import com.ichi2.anki.services.NotificationService;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;

/**
 * The status of the widget.
 * <p>
 * It contains the status of each of the decks.
 */
public final class WidgetStatus {

	private static boolean mediumWidget = false;
	private static boolean smallWidget = false;
	private static boolean notification = false;
	private static AsyncTask<Context,Void,Context> mUpdateDeckStatusAsyncTask;

    /** This class should not be instantiated. */
    private WidgetStatus() {}

    /** Request the widget to update its status. */
    public static void update(Context context) {
        // Only update the widget if it is enabled.
        // TODO(flerda): Split widget from notifications.
        SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
        if (preferences.getBoolean("widgetMediumEnabled", false)) {
            mediumWidget = true;
        } else {
            mediumWidget = false;
        }
        if (preferences.getBoolean("widgetSmallEnabled", false)) {
            smallWidget = true;
        } else {
            smallWidget = false;
        }
        if (Integer.parseInt(preferences.getString("minimumCardsDueForNotification", "25")) < 1000000) {
        	notification = true;
        } else {
        	notification = false;
        }
        if (mediumWidget || smallWidget || notification) {
            // Log.d(AnkiDroidApp.TAG, "WidgetStatus.update(): updating");
            mUpdateDeckStatusAsyncTask = new UpdateDeckStatusAsyncTask();
            mUpdateDeckStatusAsyncTask.execute(context);
        } else {
            // Log.d(AnkiDroidApp.TAG, "WidgetStatus.update(): not enabled");
        }
    }


    /**
     * Block the current thread until the currently running UpdateDeckStatusAsyncTask instance (if any) has finished.
     */
    public static void waitToFinish() {
        try {
            if ((mUpdateDeckStatusAsyncTask != null) && (mUpdateDeckStatusAsyncTask.getStatus() != AsyncTask.Status.FINISHED)) {
            	mUpdateDeckStatusAsyncTask.get();
            }
        } catch (Exception e) {
            return;
        }
    }


    /** Returns the status of each of the decks. */
    public static DeckStatus[] fetch(Context context) {
        return MetaDB.getWidgetStatus(context);
    }

    /** Returns the status of each of the decks. */
    public static int[] fetchSmall(Context context) {
        return MetaDB.getWidgetSmallStatus(context);
    }

    public static int fetchDue(Context context) {
        return MetaDB.getNotificationStatus(context);
    }

    private static class UpdateDeckStatusAsyncTask extends AsyncTask<Context, Void, Context> {
        private static final DeckStatus[] EMPTY_DECK_STATUS = new DeckStatus[0];

        private static DeckStatus[] mDecks = EMPTY_DECK_STATUS;

        @Override
        protected Context doInBackground(Context... params) {
            // Log.d(AnkiDroidApp.TAG, "WidgetStatus.UpdateDeckStatusAsyncTask.doInBackground()");
            Context context = params[0];

            if (!AnkiDroidApp.isSdCardMounted()) {
            	return context;
            }

            SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
            String deckPath = preferences.getString("deckPath",
                    AnkiDroidApp.getStorageDirectory() + "/AnkiDroid");

            File dir = new File(deckPath);

            File[] fileList = dir.listFiles(new AnkiFileFilter());

            if (fileList == null || fileList.length == 0) {
                mDecks = EMPTY_DECK_STATUS;
                return context;
            }

            // For the deck information
            ArrayList<DeckStatus> decks = new ArrayList<DeckStatus>(fileList.length);

            for (File file : fileList) {
            	String absPath = null;
                try {
                    // Run through the decks and get the information
                    absPath = file.getAbsolutePath();
                    String deckName = file.getName().replaceAll(".anki", "");

                    // Log.i(AnkiDroidApp.TAG, "Found deck: " + absPath);

                    Deck deck;
                    Deck currentDeck = AnkiDroidApp.deck();
                    if (currentDeck != null && currentDeck.getDeckPath().equals(absPath)) {
                    	deck = currentDeck;
                    } else {
                    	try {
                        	deck = Deck.openDeck(absPath, false);                    		
            			} catch (RuntimeException e) {
            				Log.w(AnkiDroidApp.TAG, "Widget: Could not open database " + absPath + ": " + e);
            				BackupManager.restoreDeckIfMissing(absPath);
            				deck = null;
            			}
                    }
                    if (deck == null) {
                        Log.e(AnkiDroidApp.TAG, "Widget: Skipping null deck: " + absPath);
                        // Use the data from the last time we updated the deck, if available.
//                        for (DeckStatus deckStatus : mDecks) {
//                            if (absPath.equals(deckStatus.mDeckPath)) {
//                                // Log.d(AnkiDroidApp.TAG, "Using previous value");
//                                decks.add(deckStatus);
//                                break;
//                            }
//                        }
                        continue;
                    }
                    int dueCards = 0;
                    int newCards = 0;
                    int failedCards = deck.getFailedSoonCount();
                    int eta = 0;
                    int reps = deck.getSessionFinishedCards();
		    if(!deck.hasFinishScheduler()) {
        	            dueCards = deck.getRevCount();
        	            newCards = deck.getNewCountToday();
        	            eta = deck.getETA();
		    }
                    // Close the database connection, but only if this is not the current database.
                    // Probably we need to make this atomic to be sure it will not cause a failure.
                    if (currentDeck != null && currentDeck.getDB() != deck.getDB()) {
                        deck.closeDeck();
                    }

                    // Add the information about the deck
                    decks.add(new DeckStatus(absPath, deckName, newCards, dueCards, failedCards, eta, reps));
                } catch (SQLException e) {
                    // Log.i(AnkiDroidApp.TAG, "Widget: Could not open deck");
                    Log.e(AnkiDroidApp.TAG, e.toString());
                    if (absPath != null) {
                        BackupManager.restoreDeckIfMissing(absPath);                    	
                    }
                }
            }

            if (!decks.isEmpty() && decks.size() > 1) {
                // Sort and reverse the list if there are decks
                // Log.i(AnkiDroidApp.TAG, "Sorting deck");

                // Ordered by reverse due cards number
                Collections.sort(decks, new ByDueComparator());
            }

            mDecks = decks.toArray(EMPTY_DECK_STATUS);
            return context;
        }

        @Override
        protected void onPostExecute(Context context) {
            // Log.d(AnkiDroidApp.TAG, "WidgetStatus.UpdateDeckStatusAsyncTask.onPostExecute()");
            MetaDB.storeWidgetStatus(context, mDecks);
            if (mediumWidget) {
            	Intent intent;
                intent = new Intent(context, AnkiDroidWidgetMedium.UpdateService.class);
                intent.setAction(AnkiDroidWidgetMedium.UpdateService.ACTION_UPDATE);
                context.startService(intent);
            }
            if (smallWidget) {
            	Intent intent;
                intent = new Intent(context, AnkiDroidWidgetSmall.UpdateService.class);            	
                context.startService(intent);
            }
            if (notification) {
            	Intent intent;
                intent = new Intent(context, NotificationService.class);
                context.startService(intent);
            }
        }

        /** Comparator that sorts instances of {@link DeckStatus} based on number of due cards. */
        private static class ByDueComparator implements java.util.Comparator<DeckStatus> {
            @Override
            public int compare(DeckStatus deck1, DeckStatus deck2) {
                // Reverse due cards number order
                return deck2.mDueCards - deck1.mDueCards;
            }
        }

        /** Filter for Anki files. */
        private static final class AnkiFileFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile() && pathname.getName().endsWith(".anki");
            }
        }
    }
}
