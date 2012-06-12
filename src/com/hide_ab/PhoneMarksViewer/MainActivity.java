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
	//Handler�̃C���X�^���X����
	Handler mHandler = new Handler();

	// ���j���[�A�C�e��ID
	private static final int MENU_ITEM0 = 0;

	private static final String GOOGLE_DOCS_API_URL = "https://docs.google.com/feeds/";
	private static final String DOCS_AUTH_TOKEN_TYPE = "writely";
	private static final String TAG = "GoogleDocsTest";
	// �\������f�[�^�̃��X�g
	protected ArrayList<LinkInfo> List = null;
	// ListAdapter
	private LinkListAdapter linklistadapter = null;
	// DB Helper
	protected DatabaseHelper DBHelper = new DatabaseHelper(this);
	// �F�؂̂��߂�auth token
	private String authToken = "";
	private static HttpTransport transport;

	private String Parent;
	private ProgressDialog progressDialog;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// ��ʍ\����K�p
		setContentView(R.layout.main);

		this.List = new ArrayList<LinkInfo>();

		// �f�[�^�x�[�X�I�u�W�F�N�g�̎擾
		this.DBHelper = new DatabaseHelper(this);

		try {
			// �Ăяo��������p�����[�^�擾
			Intent intent = getIntent();
			Parent = intent.getStringExtra("Parent");
		} catch (Exception e) {
			Parent = null;
		}
		if(Parent == null)
			Parent = "";

		// �u�b�N�}�[�N�ꗗ�\��
		this.DrawBookmarks(Parent);
	}

	// GoogleDoc����PhoneMarks�̏ڍ�URL���擾����
	private String GetBookmarkURL() {
		// AccountManager���擾
		AccountManager manager = AccountManager.get(this);
		// Google�A�J�E���g�̈ꗗ���擾
		Account[] accounts = manager.getAccountsByType("com.google");
		// �T���v���Ȃ̂Ŏb��I��1�ڂ��擾
		Account acount = accounts[0];
		// �F�؂̂��߂�auth token���擾
		AccountManagerFuture<Bundle> f = manager.getAuthToken(acount, DOCS_AUTH_TOKEN_TYPE, null, this, null, null);
		try {
			Bundle bundle = f.getResult();
			if(bundle.containsKey(AccountManager.KEY_INTENT)) {
				// �F�؂��K�v�ȏꍇ
				Intent intent = bundle.getParcelable(AccountManager.KEY_INTENT);
				int flags = intent.getFlags();
				flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
				intent.setFlags(flags);
				startActivityForResult(intent, 0);
				// �{����Result���󂯂Ƃ�K�v�����邯�Ǌ���
				return null;
			} else {
				// �F�ؗp�g�[�N���擾
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

		// GoogleTransport�����
		transport = GoogleTransport.create();
		GoogleHeaders headers = (GoogleHeaders)transport.defaultHeaders;
		// "[company-id]-[app-name]-[app-version]"�Ƃ����`���ŃA�v���P�[�V���������Z�b�g
		headers.setApplicationName("Kokufu-GoogleDocsTest-1.0");
		// �o�[�W�������Z�b�g
		headers.gdataVersion = "3";
		// AtomParser�����
		AtomParser parser = new AtomParser();
		// GoogleDocumentsList�̃l�[���X�y�[�X���Z�b�g(���Dictionary�łƂ肠�������Ȃ���)
		parser.namespaceDictionary = new XmlNamespaceDictionary();
		// GoogleTransport��AtomParser���Z�b�g
		transport.addParser(parser);

		// HttpTransport��ApacheHttpTransport�̃C���X�^���X���Z�b�g(���������Ă����Ȃ���Exception���������܂�)
		HttpTransport.setLowLevelHttpTransport(ApacheHttpTransport.INSTANCE);

		// GoogleTransport�ɔF�؃g�[�N��(auth token)���Z�b�g(����ŔF�؃w�b�_�������I�ɕt���Ă���܂�)
		headers.setGoogleLogin(authToken);

		// ���M&�h�L�������g�ꗗXML�擾
		Feed feed = null;
		try {
			// GoogleTransport����GET���N�G�X�g�𐶐�
			HttpRequest request = transport.buildGetRequest();
			// URL���Z�b�g
			request.url = new GoogleUrl(GOOGLE_DOCS_API_URL + "default/private/full"); // ��2 
			// HTTP���N�G�X�g�����s���ă��X�|���X���p�[�X
			feed = request.execute().parseAs(Feed.class);
		} catch (IOException e) {
			Log.e(TAG, "", e);
			handleException(e);
			return null;
		}

		String ItemUrl = "";
		for(Entry entry : feed.entry) {
			if(entry.title.equals("PhoneMarks")) {
				// �����𐸍�
				for(Content content : entry.content) {
					ItemUrl = content.src;
				}
			}
		}
		return ItemUrl;
	}

	// �u�b�N�}�[�N���o�^
	private void CreateBookmark(String ItemUrl) {
		// DB Open
		SQLiteDatabase Db = this.DBHelper.getWritableDatabase();
		// ��U�N���A����
		Db.delete("Bookmarks", "", null);

		try {
			// PlainText�_�E�����[�hURL
			HttpRequest request = transport.buildGetRequest();
			request.url = new GoogleUrl(ItemUrl + "&format=txt");

			// PlainText�Ƃ��Ď擾
			HttpResponse response = request.execute();
			String PlainText = response.parseAsString();
			PlainText = PlainText.replaceAll("&", "&amp;");

			//XML�p�[�T�[�𐶐�����
			XmlPullParser parser = Xml.newPullParser();
			//XML�p�[�T�ɉ�͂��������e��ݒ肷��
			parser.setInput(new StringReader(PlainText));

			int no = 1;
			Hashtable Folders = new Hashtable();

			int eventType = parser.getEventType();
			while(eventType != XmlPullParser.END_DOCUMENT) {
				switch(eventType) {
					case XmlPullParser.START_TAG:
						if(parser.getName().equals("entry")) {
							// �u�b�N�}�[�N�����X�g�ɒǉ�
							LinkInfo linkInfo = new LinkInfo();
							linkInfo.setTitle(parser.getAttributeValue(null, "folder"));
							linkInfo.setTitle(parser.getAttributeValue(null, "title"));
							linkInfo.setUrl(parser.getAttributeValue(null, "url"));
							this.List.add(linkInfo);

							// URL�o�^
							ContentValues values = new ContentValues();
							values.put("no", no);
							values.put("foru", 2);
							values.put("folder", parser.getAttributeValue(null, "folder"));
							values.put("title", parser.getAttributeValue(null, "title"));
							values.put("url", parser.getAttributeValue(null, "url"));
							Db.insert("Bookmarks", null, values);
							no++;

							// �e�t�H���_������ΘA�z�z��ɓo�^���Ă���
							if(!parser.getAttributeValue(null, "folder").equals("")) {
								Folders.put(parser.getAttributeValue(null, "folder"), 1);
							}
						}
						break;
				}
				eventType = parser.next();
			}

			// �e�t�H���_��o�^�����A�z�z������Ƀt�H���_�\����DB�ɓo�^
			Enumeration keys = Folders.keys();
			while(keys.hasMoreElements()) {
				// �J���}�ŕ���
				String title = (String)keys.nextElement();
				String[] strAry = title.split(",");
				String Parent = "";
				for(int i = 0; i < strAry.length; i++) {
					//if(!strAry[i].equals("")) {
					// �ŏI�v�f�ȊO�̓J���}�t���ŘA�����Ă���
					if(i < strAry.length - 1) {
						Parent += strAry[i] + ","; 
					}
					// �ŏI�v�f�Ȃ炻��܂ł̏��ƍ��킹DB�ɓo�^
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

	// �u�b�N�}�[�N�ꗗ�\��
	public void DrawBookmarks(String Parent) {
		// DB Open
		SQLiteDatabase Db = this.DBHelper.getWritableDatabase();

		// ���X�g�N���A
		this.List.clear();

		// �Y���K�w��Bookmark�����擾
		String sql = "SELECT * FROM Bookmarks WHERE folder = '" + Parent + "' ORDER BY foru;";
		Cursor c = Db.rawQuery(sql, null);

		// ���X�g���쐬
		c.moveToFirst();
		for(int i = 0; i < c.getCount(); i++) {
			// �u�b�N�}�[�N�����X�g�ɒǉ�
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

		// ShopAdapter��ShopList.xml���ɂ���listview_results�ɓn���ē��e��\������
		ListView listview_results = (ListView)findViewById(R.id.listview_results);
		// List����ShopAdapter�𐶐�
		this.linklistadapter = new LinkListAdapter(this, R.layout.link_listrow, this.List);
		// listview_results��linklistadapter���Z�b�g
		listview_results.setAdapter(this.linklistadapter);

		// listview_results��OnItemClickListener��ݒ�
		listview_results.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) { 
				LinkInfo linkInfo = (LinkInfo)List.get(position);
				// �N���b�N�ӏ����t�H���_�Ȃ�K�w�ړ�
				if(linkInfo.getForU() == 1) {
					String Parent = linkInfo.getFolder();
					if(Parent.equals("")) {
						Parent = linkInfo.getTitle() + ",";
					} else {
						Parent = Parent + linkInfo.getTitle() + ",";
					}

					// ���K�w�̃��X�g��ʂ�\��
					Intent intent = new Intent(MainActivity.this, MainActivity.class);
					intent.putExtra("Parent", Parent);
					startActivity(intent);
				}
				// �N���b�N�ӏ���URL�Ȃ�u���E�U�N��
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
	// ���X�g�A�_�v�^
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
			// �r���[���󂯎��
			View view = convertView;
			// �󂯎�����r���[��null�Ȃ�V�����r���[�𐶐�
			if(view == null) {
				view = inflater.inflate(R.layout.link_listrow, null);
			}

			// �\�����ׂ��f�[�^�̎擾
			LinkInfo linkInfo = (LinkInfo)List.get(position);
			if(linkInfo != null) {
/*
				// �X�N���[���l�[�����r���[�ɃZ�b�g
				ImageView image_photo = (ImageView)view.findViewById(R.id.image_photo);
				if(image_photo != null) {
					Bitmap Photo = shopInfo.getPhoto();
					if(Photo == null) {
						Photo = shopinfos.getDefaultPhoto();
						// �摜�擾�^�X�N�������Ă��Ȃ����
						if(!shopInfo.getPhotoGetTask()) {
							// �o�b�N�O���E���h�ŉ摜���擾
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
				// AccountManager���擾
				AccountManager manager = AccountManager.get(this);
				// �L���b�V�����폜
				manager.invalidateAuthToken("com.google", authToken);
				Toast.makeText(getApplicationContext(),
							"�L���b�V�����폜���܂����B�A�v�����ċN�����Ă��������B",
							Toast.LENGTH_LONG).show();
			}
			return;
		}
		else {
			e.printStackTrace();
		}
	}

	//
	// �X�V���j���[
	//
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuItem item0 = menu.add(Menu.NONE, MENU_ITEM0, Menu.NONE, "�X�V");
		item0.setIcon(android.R.drawable.ic_menu_more);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// �_�C�A���O��\��
		this.progressDialog = new ProgressDialog(this);
		this.progressDialog.setTitle("�^�C�g��");
		this.progressDialog.setMessage("���������s���ł�...");
		this.progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		this.progressDialog.setCancelable(true);
		this.progressDialog.show();

		switch(item.getItemId()) {
			case MENU_ITEM0:
				// DB Open
				SQLiteDatabase Db = this.DBHelper.getWritableDatabase();
				// ��U�N���A����
				Db.delete("Bookmarks", "", null);
				// �u�b�N�}�[�N�ꗗ�\��
				this.DrawBookmarks(this.Parent);

				// �X���b�h���N�����ăo�b�N�O���E���h�ōX�V����
				new Thread(new Runnable() {
					@Override
					public void run() {
						try{
							Thread.sleep(10000);
						} catch (InterruptedException e) {
						}

						// GoogleDoc����PhoneMarks�̏ڍ�URL���擾����
						String ItemUrl = MainActivity.this.GetBookmarkURL();
						// �u�b�N�}�[�N���o�^
						MainActivity.this.CreateBookmark(ItemUrl);

						// Handler��post���\�b�h���g����UI�X���b�h�ɏ�����dispatch���܂�
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								// �u�b�N�}�[�N�ꗗ�\��
								MainActivity.this.DrawBookmarks(MainActivity.this.Parent);

								// ���ۂɍs�������������I�������_�C�A���O������
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