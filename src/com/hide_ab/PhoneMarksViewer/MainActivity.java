package com.hide_ab.PhoneMarksViewer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xmlpull.v1.XmlPullParser;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.GoogleTransport;
import com.google.api.client.googleapis.GoogleUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.xml.XmlNamespaceDictionary;
import com.google.api.client.xml.atom.AtomParser;

public class MainActivity extends Activity {
	private static final String TAG = "GoogleDocsTest";
	// 表示するデータのリスト
	protected ArrayList<LinkInfo> List = null;
	// ListAdapter
	private LinkListAdapter linklistadapter = null;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		this.List = new ArrayList<LinkInfo>();

		// 画面構成を適用
        setContentView(R.layout.main);

        // AccountManagerを通じてGoogleアカウントを取得
        AccountManager manager = AccountManager.get(this);
        Account[] accounts = manager.getAccountsByType("com.google");
        Bundle bundle = null;
        try {
            bundle = manager.getAuthToken(
                    accounts[0], // テストなので固定
                    "writely",   // ※1
                    null,
                    this,
                    null,
                    null).getResult();
        } catch (OperationCanceledException e) {
            Log.e(TAG, "", e);
            return;
        } catch (AuthenticatorException e) {
            Log.e(TAG, "", e);
            return;
        } catch (IOException e) {
            Log.e(TAG, "", e);
            return;
        }

        String authToken = "";
        if(bundle.containsKey(AccountManager.KEY_INTENT)) {
            // 認証が必要な場合
            Intent intent = bundle.getParcelable(AccountManager.KEY_INTENT);
            int flags = intent.getFlags();
            flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
            intent.setFlags(flags);
            startActivityForResult(intent, 0);
            // 本当はResultを受けとる必要があるけど割愛
            return;
        } else {
            // 認証用トークン取得
            authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
        }

        // 送信準備
        HttpTransport transport = GoogleTransport.create();
        GoogleHeaders headers = (GoogleHeaders)transport.defaultHeaders;
        headers.setApplicationName("Kokufu-GoogleDocsTest/1.0");
        headers.gdataVersion = "3";
        headers.setGoogleLogin(authToken); // 認証トークン設定

        // Parserを準備してTransportにセットする
        AtomParser parser = new AtomParser();
        // 空のDictionaryでとりあえず問題なさげ
        parser.namespaceDictionary = new XmlNamespaceDictionary();
        transport.addParser(parser);

        // 送信&ドキュメント一覧XML取得
        Feed feed = null;
        try {
            HttpRequest request = transport.buildGetRequest();
            request.url = new GoogleUrl("https://docs.google.com/feeds/default/private/full"); // ※2 
			feed = request.execute().parseAs(Feed.class);
/*
            HttpResponse response = request.execute();
            TextView text1 = (TextView)findViewById(R.id.text1);
    		String a = response.parseAsString();
            text1.setText(a);
			feed = response.parseAs(Feed.class);
*/
		} catch (IOException e) {
//			TextView text1 = (TextView)findViewById(R.id.text1);
//			text1.setText(e.toString());
            Log.e(TAG, "", e);
            return;
        }

        String tmp = "";
        String Items = "";
        String Entries = "";
        for(Entry entry : feed.entry) {
            for(Content content : entry.content) {
            	try {
            		tmp += entry.title + " - " + content.src + "\n\n";

                	// PlainTextダウンロードURL
                	HttpRequest request = transport.buildGetRequest();
                	request.url = new GoogleUrl(content.src + "&format=txt");
                	
                	// PlainTextとして取得
        			HttpResponse response = request.execute();
        			Items = response.parseAsString();
        			Items = Items.replaceAll("&", "&amp;");

            		//XMLパーサーを生成する
            		XmlPullParser parser2 = Xml.newPullParser();
            		//XMLパーサに解析したい内容を設定する
            		parser2.setInput(new StringReader(Items));
/*
            		//ファイル出力ストリームの作成
            		String dst = "/sdcard/dst.txt";
            		BufferedWriter bufferedWriterObj = null;
            		bufferedWriterObj = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dst, true), "UTF-8"));
            		bufferedWriterObj.write(Items);
            		bufferedWriterObj.flush();
*/
            		int eventType = parser2.getEventType();
            		while(eventType != XmlPullParser.END_DOCUMENT) {
            			switch(eventType) {
        					case XmlPullParser.START_TAG:
            					if(parser2.getName().equals("entry")) {
            						// ブックマークをリストに追加
            						LinkInfo linkInfo = new LinkInfo();
            						linkInfo.setTitle(parser2.getAttributeValue(null, "title"));
            						linkInfo.setUrl(parser2.getAttributeValue(null, "url"));
            						this.List.add(linkInfo);
            					}
            					break;
            			}
            			eventType = parser2.next();
            		}
            	} catch (Exception e) {
//        			TextView text1 = (TextView)findViewById(R.id.text1);
//        			tmp += e.toString();
//        			text1.setText(tmp);
        			Log.e(TAG, "", e);
        			return;
        		}
            }
        }

/*
        Phonemarks phonemarks = null;
        for(Entry2 entry2 : pm.entry) {
        	Items += entry2.title + " - " + entry2.url + "\n\n";
        }
*/

        // 結果を表示
//        TextView text1 = (TextView)findViewById(R.id.text1);
//        text1.setText(Entries);

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
	    		if(linkInfo.getUrl() != "") {
	    			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(linkInfo.getUrl()));
	        		startActivity(intent);
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
				TextView tvTitle = (TextView)view.findViewById(R.id.Title);
				if(tvTitle != null) {
					tvTitle.setText(linkInfo.getTitle());
				}
			}
			return view;
		}
	}
}