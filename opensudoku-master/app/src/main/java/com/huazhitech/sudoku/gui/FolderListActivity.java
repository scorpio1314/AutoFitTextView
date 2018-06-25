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

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.TextView;

import com.huazhitech.sudoku.R;
import com.sixth.adwoad.AdListener;
import com.sixth.adwoad.AdwoAdView;
import com.sixth.adwoad.ErrorCode;

import com.huazhitech.sudoku.db.FolderColumns;
import com.huazhitech.sudoku.db.SudokuDatabase;
import com.huazhitech.sudoku.game.FolderInfo;
import com.huazhitech.sudoku.gui.FolderDetailLoader.FolderDetailCallback;

/**
 * List of puzzle's folder. This activity also serves as root activity of application.
 *
 * @author romario
 */
public class FolderListActivity extends ListActivity {

	public static final int MENU_ITEM_ADD = Menu.FIRST;
	public static final int MENU_ITEM_RENAME = Menu.FIRST + 1;
	public static final int MENU_ITEM_DELETE = Menu.FIRST + 2;
	public static final int MENU_ITEM_ABOUT = Menu.FIRST + 3;
	public static final int MENU_ITEM_EXPORT = Menu.FIRST + 4;
	public static final int MENU_ITEM_EXPORT_ALL = Menu.FIRST + 5;
	public static final int MENU_ITEM_IMPORT = Menu.FIRST + 6;

	private static final int DIALOG_ABOUT = 0;
	private static final int DIALOG_ADD_FOLDER = 1;
	private static final int DIALOG_RENAME_FOLDER = 2;
	private static final int DIALOG_DELETE_FOLDER = 3;

	private static final String TAG = "FolderListActivity";

	private Cursor mCursor;
	private SudokuDatabase mDatabase;
	private FolderListViewBinder mFolderListBinder;

	// input parameters for dialogs
	private TextView mAddFolderNameInput;
	private TextView mRenameFolderNameInput;
	private long mRenameFolderID;
	private long mDeleteFolderID;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.folder_list);
		View getMorePuzzles = (View) findViewById(R.id.get_more_puzzles);
		rl_main = (RelativeLayout) findViewById(R.id.rl_main);
		setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
		// Inform the list we provide context menus for items

		mDatabase = new SudokuDatabase(getApplicationContext());
		mCursor = mDatabase.getFolderList();
		startManagingCursor(mCursor);
		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.folder_list_item,
				mCursor, new String[]{FolderColumns.NAME, FolderColumns._ID},
				new int[]{R.id.name, R.id.detail});
		mFolderListBinder = new FolderListViewBinder(this);
		adapter.setViewBinder(mFolderListBinder);

		setListAdapter(adapter);

		// show changelog on first run
		Changelog changelog = new Changelog(this);
		changelog.showOnFirstRun();
		initAD();
	}

	@Override
	protected void onStart() {
		super.onStart();

		updateList();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDatabase.close();
		mFolderListBinder.destroy();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putLong("mRenameFolderID", mRenameFolderID);
		outState.putLong("mDeleteFolderID", mDeleteFolderID);
	}

	@Override
	protected void onRestoreInstanceState(Bundle state) {
		super.onRestoreInstanceState(state);

		mRenameFolderID = state.getLong("mRenameFolderID");
		mDeleteFolderID = state.getLong("mDeleteFolderID");
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Intent i = new Intent(this, SudokuListActivity.class);
		i.putExtra(SudokuListActivity.EXTRA_FOLDER_ID, id);
		startActivity(i);
	}

	private void updateList() {
		mCursor.requery();
	}

	private static class FolderListViewBinder implements ViewBinder {
		private Context mContext;
		private FolderDetailLoader mDetailLoader;


		public FolderListViewBinder(Context context) {
			mContext = context;
			mDetailLoader = new FolderDetailLoader(context);
		}

		@Override
		public boolean setViewValue(View view, Cursor c, int columnIndex) {

			switch (view.getId()) {
				case R.id.name:
					((TextView) view).setText(c.getString(columnIndex));
					break;
				case R.id.detail:
					final long folderID = c.getLong(columnIndex);
					final TextView detailView = (TextView) view;
					detailView.setText(mContext.getString(R.string.loading));
					mDetailLoader.loadDetailAsync(folderID, new FolderDetailCallback() {
						@Override
						public void onLoaded(FolderInfo folderInfo) {
							if (folderInfo != null)
								detailView.setText(folderInfo.getDetail(mContext));
						}
					});
			}

			return true;
		}

		public void destroy() {
			mDetailLoader.destroy();
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
