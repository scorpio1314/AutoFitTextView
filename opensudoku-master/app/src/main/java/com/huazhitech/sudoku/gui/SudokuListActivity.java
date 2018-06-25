/* 
 * Copyright (C) 2009 Roman Masek
 * 
 * This file is part of OpenSudoku.
 * 
 * OpenSudoku is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * OpenSudoku is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with OpenSudoku.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.huazhitech.sudoku.gui;

import java.text.DateFormat;
import java.util.Date;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.SimpleCursorAdapter.ViewBinder;

import com.sixth.adwoad.AdListener;
import com.sixth.adwoad.AdwoAdView;
import com.sixth.adwoad.ErrorCode;

import com.huazhitech.sudoku.R;
import com.huazhitech.sudoku.db.SudokuColumns;
import com.huazhitech.sudoku.db.SudokuDatabase;
import com.huazhitech.sudoku.game.FolderInfo;
import com.huazhitech.sudoku.game.CellCollection;
import com.huazhitech.sudoku.game.SudokuGame;
import com.huazhitech.sudoku.gui.FolderDetailLoader.FolderDetailCallback;
import com.huazhitech.sudoku.utils.AndroidUtils;

/**
 * List of puzzles in folder.
 *
 * @author romario
 */
public class SudokuListActivity extends ListActivity {

	public static final String EXTRA_FOLDER_ID = "folder_id";

	public static final int MENU_ITEM_INSERT = Menu.FIRST;
	public static final int MENU_ITEM_EDIT = Menu.FIRST + 1;
	public static final int MENU_ITEM_DELETE = Menu.FIRST + 2;
	public static final int MENU_ITEM_PLAY = Menu.FIRST + 3;
	public static final int MENU_ITEM_RESET = Menu.FIRST + 4;
	public static final int MENU_ITEM_EDIT_NOTE = Menu.FIRST + 5;
	public static final int MENU_ITEM_FILTER = Menu.FIRST + 6;
	public static final int MENU_ITEM_FOLDERS = Menu.FIRST + 7;

	private static final int DIALOG_DELETE_PUZZLE = 0;
	private static final int DIALOG_RESET_PUZZLE = 1;
	private static final int DIALOG_EDIT_NOTE = 2;
	private static final int DIALOG_FILTER = 3;

	private static final String FILTER_STATE_NOT_STARTED = "filter" + SudokuGame.GAME_STATE_NOT_STARTED;
	private static final String FILTER_STATE_PLAYING = "filter" + SudokuGame.GAME_STATE_PLAYING;
	private static final String FILTER_STATE_SOLVED = "filter" + SudokuGame.GAME_STATE_COMPLETED;

	private static final String TAG = "SudokuListActivity";

	private long mFolderID;

	// input parameters for dialogs
	private long mDeletePuzzleID;
	private long mResetPuzzleID;
	private long mEditNotePuzzleID;
	private TextView mEditNoteInput;
	private SudokuListFilter mListFilter;

	private TextView mFilterStatus;

	private SimpleCursorAdapter mAdapter;
	private Cursor mCursor;
	private SudokuDatabase mDatabase;
	private FolderDetailLoader mFolderDetailLoader;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// theme must be set before setContentView
		AndroidUtils.setThemeFromPreferences(this);

		setContentView(R.layout.sudoku_list);
		mFilterStatus = (TextView) findViewById(R.id.filter_status);

		setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

		mDatabase = new SudokuDatabase(getApplicationContext());
		mFolderDetailLoader = new FolderDetailLoader(getApplicationContext());

		Intent intent = getIntent();
		if (intent.hasExtra(EXTRA_FOLDER_ID)) {
			mFolderID = intent.getLongExtra(EXTRA_FOLDER_ID, 0);
		} else {
			Log.d(TAG, "No 'folder_id' extra provided, exiting.");
			finish();
			return;
		}

		final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		mListFilter = new SudokuListFilter(getApplicationContext());
		mListFilter.showStateNotStarted = settings.getBoolean(FILTER_STATE_NOT_STARTED, true);
		mListFilter.showStatePlaying = settings.getBoolean(FILTER_STATE_PLAYING, true);
		mListFilter.showStateCompleted = settings.getBoolean(FILTER_STATE_SOLVED, true);

		mAdapter = new SimpleCursorAdapter(this, R.layout.sudoku_list_item,
				null, new String[]{SudokuColumns.DATA, SudokuColumns.STATE,
				SudokuColumns.TIME, SudokuColumns.LAST_PLAYED,
				SudokuColumns.CREATED, SudokuColumns.PUZZLE_NOTE},
				new int[]{R.id.sudoku_board, R.id.state, R.id.time,
						R.id.last_played, R.id.created, R.id.note});
		mAdapter.setViewBinder(new SudokuListViewBinder(this));
		updateList();
		setListAdapter(mAdapter);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		mDatabase.close();
		mFolderDetailLoader.destroy();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putLong("mDeletePuzzleID", mDeletePuzzleID);
		outState.putLong("mResetPuzzleID", mResetPuzzleID);
		outState.putLong("mEditNotePuzzleID", mEditNotePuzzleID);
	}

	@Override
	protected void onRestoreInstanceState(Bundle state) {
		super.onRestoreInstanceState(state);

		mDeletePuzzleID = state.getLong("mDeletePuzzleID");
		mResetPuzzleID = state.getLong("mResetPuzzleID");
		mEditNotePuzzleID = state.getLong("mEditNotePuzzleID");
	}

	@Override
	protected void onResume() {
		super.onResume();
		// the puzzle list is naturally refreshed when the window
		// regains focus, so we only need to update the title
		updateTitle();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// if there is no activity in history and back button was pressed, go
		// to FolderListActivity, which is the root activity.
		if (isTaskRoot() && keyCode == KeyEvent.KEYCODE_BACK) {
			Intent i = new Intent();
			i.setClass(this, FolderListActivity.class);
			startActivity(i);
			finish();
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		playSudoku(id);
	}

	/**
	 * Updates whole list.
	 */
	private void updateList() {
		updateTitle();
		updateFilterStatus();

		if (mCursor != null) {
			stopManagingCursor(mCursor);
		}
		mCursor = mDatabase.getSudokuList(mFolderID, mListFilter);
		startManagingCursor(mCursor);
		mAdapter.changeCursor(mCursor);
	}

	private void updateFilterStatus() {

		if (mListFilter.showStateCompleted && mListFilter.showStateNotStarted && mListFilter.showStatePlaying) {
			mFilterStatus.setVisibility(View.GONE);
		} else {
			mFilterStatus.setText(getString(R.string.filter_active, mListFilter));
			mFilterStatus.setVisibility(View.VISIBLE);
		}
	}

	private void updateTitle() {
		FolderInfo folder = mDatabase.getFolderInfo(mFolderID);
		setTitle(folder.name);

		mFolderDetailLoader.loadDetailAsync(mFolderID, new FolderDetailCallback() {
			@Override
			public void onLoaded(FolderInfo folderInfo) {
				if (folderInfo != null)
					setTitle(folderInfo.name + " - " + folderInfo.getDetail(getApplicationContext()));
			}
		});
	}

	private void playSudoku(long sudokuID) {
		Intent i = new Intent(SudokuListActivity.this, SudokuPlayActivity.class);
		i.putExtra(SudokuPlayActivity.EXTRA_SUDOKU_ID, sudokuID);
		startActivity(i);
	}

	private static class SudokuListViewBinder implements ViewBinder {
		private Context mContext;
		private GameTimeFormat mGameTimeFormatter = new GameTimeFormat();
		private DateFormat mDateTimeFormatter = DateFormat.getDateTimeInstance(
				DateFormat.SHORT, DateFormat.SHORT);
		private DateFormat mTimeFormatter = DateFormat
				.getTimeInstance(DateFormat.SHORT);

		public SudokuListViewBinder(Context context) {
			mContext = context;
		}

		@Override
		public boolean setViewValue(View view, Cursor c, int columnIndex) {

			int state = c.getInt(c.getColumnIndex(SudokuColumns.STATE));

			TextView label = null;

			switch (view.getId()) {
				case R.id.sudoku_board:
					String data = c.getString(columnIndex);
					// TODO: still can be faster, I don't have to call initCollection and read notes
					CellCollection cells = null;
					;
					try {
						cells = CellCollection.deserialize(data);
					} catch (Exception e) {
						long id = c.getLong(c.getColumnIndex(SudokuColumns._ID));
						Log.e(TAG, String.format("Exception occurred when deserializing puzzle with id %s.", id), e);
					}
					SudokuBoardView board = (SudokuBoardView) view;
					board.setReadOnly(true);
					board.setFocusable(false);
					((SudokuBoardView) view).setCells(cells);
					break;
				case R.id.state:
					label = ((TextView) view);
					String stateString = null;
					switch (state) {
						case SudokuGame.GAME_STATE_COMPLETED:
							stateString = mContext.getString(R.string.solved);
							break;
						case SudokuGame.GAME_STATE_PLAYING:
							stateString = mContext.getString(R.string.playing);
							break;
					}
					label.setVisibility(stateString == null ? View.GONE
							: View.VISIBLE);
					label.setText(stateString);
					if (state == SudokuGame.GAME_STATE_COMPLETED) {
						// TODO: read colors from android resources
						label.setTextColor(Color.rgb(150, 150, 150));
					} else {
						label.setTextColor(Color.rgb(255, 255, 255));
						//label.setTextColor(SudokuListActivity.this.getResources().getColor(R.));
					}
					break;
				case R.id.time:
					long time = c.getLong(columnIndex);
					label = ((TextView) view);
					String timeString = null;
					if (time != 0) {
						timeString = mGameTimeFormatter.format(time);
					}
					label.setVisibility(timeString == null ? View.GONE
							: View.VISIBLE);
					label.setText(timeString);
					if (state == SudokuGame.GAME_STATE_COMPLETED) {
						// TODO: read colors from android resources
						label.setTextColor(Color.rgb(150, 150, 150));
					} else {
						label.setTextColor(Color.rgb(255, 255, 255));
					}
					break;
				case R.id.last_played:
					long lastPlayed = c.getLong(columnIndex);
					label = ((TextView) view);
					String lastPlayedString = null;
					if (lastPlayed != 0) {
						lastPlayedString = mContext.getString(R.string.last_played_at,
								getDateAndTimeForHumans(lastPlayed));
					}
					label.setVisibility(lastPlayedString == null ? View.GONE
							: View.VISIBLE);
					label.setText(lastPlayedString);
					break;
				case R.id.created:
					long created = c.getLong(columnIndex);
					label = ((TextView) view);
					String createdString = null;
					if (created != 0) {
						createdString = mContext.getString(R.string.created_at,
								getDateAndTimeForHumans(created));
					}
					// TODO: when GONE, note is not correctly aligned below last_played
					label.setVisibility(createdString == null ? View.GONE
							: View.VISIBLE);
					label.setText(createdString);
					break;
				case R.id.note:
					String note = c.getString(columnIndex);
					label = ((TextView) view);
					if (note == null || note.trim() == "") {
						((TextView) view).setVisibility(View.GONE);
					} else {
						((TextView) view).setText(note);
					}
					label
							.setVisibility((note == null || note.trim().equals("")) ? View.GONE
									: View.VISIBLE);
					label.setText(note);
					break;
			}

			return true;
		}

		private String getDateAndTimeForHumans(long datetime) {
			Date date = new Date(datetime);

			Date now = new Date(System.currentTimeMillis());
			Date today = new Date(now.getYear(), now.getMonth(), now.getDate());
			Date yesterday = new Date(System.currentTimeMillis()
					- (1000 * 60 * 60 * 24));

			if (date.after(today)) {
				return mContext.getString(R.string.at_time, mTimeFormatter.format(date));
			} else if (date.after(yesterday)) {
				return mContext.getString(R.string.yesterday_at_time, mTimeFormatter.format(date));
			} else {
				return mContext.getString(R.string.on_date, mDateTimeFormatter.format(date));
			}

		}
	}
	private RelativeLayout rl_main;
	static AdwoAdView adView = null;
	String Adwo_PID = "70b0f75ad39147a98ae3e1cfee4babda";
	RelativeLayout.LayoutParams params = null;
	private void initAD() {
		params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
//		当不设置广告条充满屏幕宽时建议放置在父容器中间
		params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);




		// 实例化广告对象
		adView = new AdwoAdView(this, Adwo_PID,false, 20);

//		adwo广告条的宽高默认20*50乘以屏幕密度，默认宽是不充满屏宽，如果您想设置设置广告条宽充满屏幕宽您可以在实例化广告对象之前调用AdwoAdView.setBannerMatchScreenWidth(true)
//		设置广告条充满屏幕宽
//		adView.setBannerMatchScreenWidth(true);
//		设置单次请求
//		adView.setRequestInterval(0);
		//如果你有合作渠道，请设置合作渠道id，具体id值请联系安沃工作人员 。可选择设置
//		adView.setMarketId((byte) 9);
		// 设置广告监听回调
		adView.setListener(new AdListener() {
			@Override
			public void onReceiveAd(Object o) {

			}

			@Override
			public void onFailedToReceiveAd(View view, ErrorCode errorCode) {

			}

			@Override
			public void onPresentScreen() {

			}

			@Override
			public void onDismissScreen() {

			}
		});
		// 把广告条加入界面布局
		rl_main.addView(adView, params);
	}

}
