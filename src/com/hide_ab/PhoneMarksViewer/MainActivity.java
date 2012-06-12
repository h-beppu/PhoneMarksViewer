package com.hide_ab.PhoneMarksViewer;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

import org.xmlpull.v1.XmlPullParser;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.apache.ApacheHttpTransport;
import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.GoogleTransport;
import com.google.api.client.googleapis.GoogleUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.xml.XmlNamespaceDictionary;
import com.google.api.client.xml.atom.AtomParser;

public class MainActivity extends Activity {
	//Handlerのインスタンス生成
	Handler mHandler = new Handler();

	// メニューアイテムID
	private static final int MENU_ITEM0 = 0;

	private static final String GOOGLE_DOCS_API_URL = "https://docs.google.com/feeds/";
	private static final String DOCS_AUTH_TOKEN_TYPE = "writely";
	private static final String TAG = "GoogleDocsTest";
	// 表示するデータのリスト
	protected ArrayList<LinkInfo> List = null;
	// ListAdapter
	private LinkListAdapter linklistadapter = null;
	// DB Helper
	protected DatabaseHelper DBHelper = new DatabaseHelper(this);
	// 認証のためのauth token
	private String authToken = "";
	private static HttpTransport transport;

	private String Parent;
	private ProgressDialog progressDialog;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// 画面構成を適用
		setContentView(R.layout.main);

		this.List = new ArrayList<LinkInfo>();

		// データベースオブジェクトの取得
		this.DBHelper = new DatabaseHelper(this);

		try {
			// 呼び出し元からパラメータ取得
			Intent intent = getIntent();
			Parent = intent.getStringExtra("Parent");
		} catch (Exception e) {
			Parent = null;
		}
		if(Parent == null)
			Parent = "";

		// ブックマーク一覧表示
		this.DrawBookmarks(Parent);
	}

	// GoogleDocからPhoneMarksの詳細URLを取得する
	private String GetBookmarkURL() {
		// AccountManagerを取得
		AccountManager manager = AccountManager.get(this);
		// Googleアカウントの一覧を取得
		Account[] accounts = manager.getAccountsByType("com.google");
		// サンプルなので暫定的に1つ目を取得
		Account acount = accounts[0];
		// 認証のためのauth tokenを取得
		AccountManagerFuture<Bundle> f = manager.getAuthToken(acount, DOCS_AUTH_TOKEN_TYPE, null, this, null, null);
		try {
			Bundle bundle = f.getResult();
			if(bundle.containsKey(AccountManager.KEY_INTENT)) {
				// 認証が必要な場合
				Intent intent = bundle.getParcelable(AccountManager.KEY_INTENT);
				int flags = intent.getFlags();
				flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
				intent.setFlags(flags);
				startActivityForResult(intent, 0);
				// 本当はResultを受けとる必要があるけど割愛
				return null;
			} else {
				// 認証用トークン取得
				authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
			}
//			Log.d(TAG, "authToken=" + authToken);
		} catch (OperationCanceledException e) {
			Log.e(TAG, "", e);
			return null;
		} catch (AuthenticatorException e) {
			Log.e(TAG, "", e);
			return null;
		} catch (IOException e) {
			Log.e(TAG, "", e);
			return null;
		}

		// GoogleTransportを作る
		transport = GoogleTransport.create();
		GoogleHeaders headers = (GoogleHeaders)transport.defaultHeaders;
		// "[company-id]-[app-name]-[app-version]"という形式でアプリケーション名をセット
		headers.setApplicationName("Kokufu-GoogleDocsTest-1.0");
		// バージョンをセット
		headers.gdataVersion = "3";
		// AtomParserを作る
		AtomParser parser = new AtomParser();
		// GoogleDocumentsListのネームスペースをセット(空のDictionaryでとりあえず問題なさげ)
		parser.namespaceDictionary = new XmlNamespaceDictionary();
		// GoogleTransportにAtomParserをセット
		transport.addParser(parser);

		// HttpTransportにApacheHttpTransportのインスタンスをセット(これをやっておかないとExceptionが発生します)
		HttpTransport.setLowLevelHttpTransport(ApacheHttpTransport.INSTANCE);

		// GoogleTransportに認証トークン(auth token)をセット(これで認証ヘッダを自動的に付けてくれます)
		headers.setGoogleLogin(authToken);

		// 送信&ドキュメント一覧XML取得
		Feed feed = null;
		try {
			// GoogleTransportからGETリクエストを生成
			HttpRequest request = transport.buildGetRequest();
			// URLをセット
			request.url = new GoogleUrl(GOOGLE_DOCS_API_URL + "default/private/full"); // ※2 
			// HTTPリクエストを実行してレスポンスをパース
			feed = request.execute().parseAs(Feed.class);
		} catch (IOException e) {
			Log.e(TAG, "", e);
			handleException(e);
			return null;
		}

		String ItemUrl = "";
		for(Entry entry : feed.entry) {
			if(entry.title.equals("PhoneMarks")) {
				// 内部を精査
				for(Content content : entry.content) {
					ItemUrl = content.src;
				}
			}
		}
		return ItemUrl;
	}

	// ブックマーク情報登録
	private void CreateBookmark(String ItemUrl) {
		// DB Open
		SQLiteDatabase Db = this.DBHelper.getWritableDatabase();
		// 一旦クリアする
		Db.delete("Bookmarks", "", null);

		try {
			// PlainTextダウンロードURL
			HttpRequest request = transport.buildGetRequest();
			request.url = new GoogleUrl(ItemUrl + "&format=txt");

			// PlainTextとして取得
			HttpResponse response = request.execute();
			String PlainText = response.parseAsString();
			PlainText = PlainText.replaceAll("&", "&amp;");

			//XMLパーサーを生成する
			XmlPullParser parser = Xml.newPullParser();
			//XMLパーサに解析したい内容を設定する
			parser.setInput(new StringReader(PlainText));

			int no = 1;
			Hashtable Folders = new Hashtable();

			int eventType = parser.getEventType();
			while(eventType != XmlPullParser.END_DOCUMENT) {
				switch(eventType) {
					case XmlPullParser.START_TAG:
						if(parser.getName().equals("entry")) {
							// ブックマークをリストに追加
							LinkInfo linkInfo = new LinkInfo();
							linkInfo.setTitle(parser.getAttributeValue(null, "folder"));
							linkInfo.setTitle(parser.getAttributeValue(null, "title"));
							linkInfo.setUrl(parser.getAttributeValue(null, "url"));
							this.List.add(linkInfo);

							// URL登録
							ContentValues values = new ContentValues();
							values.put("no", no);
							values.put("foru", 2);
							values.put("folder", parser.getAttributeValue(null, "folder"));
							values.put("title", parser.getAttributeValue(null, "title"));
							values.put("url", parser.getAttributeValue(null, "url"));
							Db.insert("Bookmarks", null, values);
							no++;

							// 親フォルダがあれば連想配列に登録していく
							if(!parser.getAttributeValue(null, "folder").equals("")) {
								Folders.put(parser.getAttributeValue(null, "folder"), 1);
							}
						}
						break;
				}
				eventType = parser.next();
			}

			// 親フォルダを登録した連想配列を元にフォルダ構造をDBに登録
			Enumeration keys = Folders.keys();
			while(keys.hasMoreElements()) {
				// カンマで分解
				String title = (String)keys.nextElement();
				String[] strAry = title.split(",");
				String Parent = "";
				for(int i = 0; i < strAry.length; i++) {
					//if(!strAry[i].equals("")) {
					// 最終要素以外はカンマ付きで連結していく
					if(i < strAry.length - 1) {
						Parent += strAry[i] + ","; 
					}
					// 最終要素ならそれまでの情報と合わせDBに登録
					else {
						ContentValues values = new ContentValues();
						values.put("no", no);
						values.put("foru", 1);
						values.put("folder", Parent);
						values.put("title", strAry[i]);
						values.put("url", "");
						Db.insert("Bookmarks", null, values);
						no++;
					}
					//}
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "", e);
			return;
		}
	}

	// ブックマーク一覧表示
	public void DrawBookmarks(String Parent) {
		// DB Open
		SQLiteDatabase Db = this.DBHelper.getWritableDatabase();

		// リストクリア
		this.List.clear();

		// 該当階層のBookmark情報を取得
		String sql = "SELECT * FROM Bookmarks WHERE folder = '" + Parent + "' ORDER BY foru;";
		Cursor c = Db.rawQuery(sql, null);

		// リストを作成
		c.moveToFirst();
		for(int i = 0; i < c.getCount(); i++) {
			// ブックマークをリストに追加
			LinkInfo linkInfo = new LinkInfo();
			linkInfo.setForU(c.getInt(1));
			linkInfo.setFolder(c.getString(2));
			linkInfo.setTitle(c.getString(3));
			linkInfo.setUrl(c.getString(4));
			this.List.add(linkInfo);

			c.moveToNext();
		}
		c.close();
		Db.close();

		// ShopAdapterをShopList.xml内にあるlistview_resultsに渡して内容を表示する
		ListView listview_results = (ListView)findViewById(R.id.listview_results);
		// ListからShopAdapterを生成
		this.linklistadapter = new LinkListAdapter(this, R.layout.link_listrow, this.List);
		// listview_resultsにlinklistadapterをセット
		listview_results.setAdapter(this.linklistadapter);

		// listview_resultsにOnItemClickListenerを設定
		listview_results.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) { 
				LinkInfo linkInfo = (LinkInfo)List.get(position);
				// クリック箇所がフォルダなら階層移動
				if(linkInfo.getForU() == 1) {
					String Parent = linkInfo.getFolder();
					if(Parent.equals("")) {
						Parent = linkInfo.getTitle() + ",";
					} else {
						Parent = Parent + linkInfo.getTitle() + ",";
					}

					// 下階層のリスト画面を表示
					Intent intent = new Intent(MainActivity.this, MainActivity.class);
					intent.putExtra("Parent", Parent);
					startActivity(intent);
				}
				// クリック箇所がURLならブラウザ起動
				else {
					if(linkInfo.getUrl() != "") {
						Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(linkInfo.getUrl()));
						startActivity(intent);
					}
				}
			}
		}); 
	}

	//
	// リストアダプタ
	//
	class LinkListAdapter extends ArrayAdapter<LinkInfo> {
		private ArrayList<LinkInfo> List;
		private LayoutInflater inflater;

		@SuppressWarnings("unchecked")
		public LinkListAdapter(Context context, int textViewResourceId, ArrayList<LinkInfo> List) {
			super(context, textViewResourceId, List);
			this.List = List;
			this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			// ビューを受け取る
			View view = convertView;
			// 受け取ったビューがnullなら新しくビューを生成
			if(view == null) {
				view = inflater.inflate(R.layout.link_listrow, null);
			}

			// 表示すべきデータの取得
			LinkInfo linkInfo = (LinkInfo)List.get(position);
			if(linkInfo != null) {
/*
				// スクリーンネームをビューにセット
				ImageView image_photo = (ImageView)view.findViewById(R.id.image_photo);
				if(image_photo != null) {
					Bitmap Photo = shopInfo.getPhoto();
					if(Photo == null) {
						Photo = shopinfos.getDefaultPhoto();
						// 画像取得タスクが走っていなければ
						if(!shopInfo.getPhotoGetTask()) {
							// バックグラウンドで画像を取得
							PhotoGetTask task = new PhotoGetTask(shopInfo);
							task.execute("");
						}
					}
					image_photo.setImageBitmap(Photo);
				}
*/
				TextView tvTitle = (TextView)view.findViewById(R.id.TvTitle);
				if(tvTitle != null) {
					tvTitle.setText(linkInfo.getTitle());
				}
				TextView tvUrl = (TextView)view.findViewById(R.id.TvUrl);
				if(tvUrl != null) {
					tvUrl.setText(linkInfo.getUrl());
				}
				ImageView ivIcon = (ImageView)view.findViewById(R.id.IvIcon);
				if(ivIcon != null) {
					if(linkInfo.getForU() == 1) {
						ivIcon.setImageResource(R.drawable.folder);
					}
					else {
						ivIcon.setImageResource(android.R.drawable.ic_menu_more);
					}
				}
			}
			return view;
		}
	}

	private void handleException(IOException e) {
		if(e instanceof HttpResponseException) {
			int statusCode = ((HttpResponseException) e).response.statusCode;
			if(statusCode == 401 || statusCode == 403) {
				// AccountManagerを取得
				AccountManager manager = AccountManager.get(this);
				// キャッシュを削除
				manager.invalidateAuthToken("com.google", authToken);
				Toast.makeText(getApplicationContext(),
							"キャッシュを削除しました。アプリを再起動してください。",
							Toast.LENGTH_LONG).show();
			}
			return;
		}
		else {
			e.printStackTrace();
		}
	}

	//
	// 更新メニュー
	//
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuItem item0 = menu.add(Menu.NONE, MENU_ITEM0, Menu.NONE, "更新");
		item0.setIcon(android.R.drawable.ic_menu_more);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// ダイアログを表示
		this.progressDialog = new ProgressDialog(this);
		this.progressDialog.setTitle("タイトル");
		this.progressDialog.setMessage("処理を実行中です...");
		this.progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		this.progressDialog.setCancelable(true);
		this.progressDialog.show();

		switch(item.getItemId()) {
			case MENU_ITEM0:
				// DB Open
				SQLiteDatabase Db = this.DBHelper.getWritableDatabase();
				// 一旦クリアする
				Db.delete("Bookmarks", "", null);
				// ブックマーク一覧表示
				this.DrawBookmarks(this.Parent);

				// スレッドを起動してバックグラウンドで更新処理
				new Thread(new Runnable() {
					@Override
					public void run() {
						try{
							Thread.sleep(10000);
						} catch (InterruptedException e) {
						}

						// GoogleDocからPhoneMarksの詳細URLを取得する
						String ItemUrl = MainActivity.this.GetBookmarkURL();
						// ブックマーク情報登録
						MainActivity.this.CreateBookmark(ItemUrl);

						// Handlerのpostメソッドを使ってUIスレッドに処理をdispatchします
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								// ブックマーク一覧表示
								MainActivity.this.DrawBookmarks(MainActivity.this.Parent);

								// 実際に行いたい処理が終わったらダイアログを消去
								progressDialog.dismiss();
							}
						});
					}
				}).start();
				break;
		}
		return true;
	}
}