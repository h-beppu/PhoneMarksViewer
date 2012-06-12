package com.hide_ab.PhoneMarksViewer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;

import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.googleapis.GoogleTransport;
import com.google.api.client.googleapis.GoogleUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.util.Key;
import com.google.api.client.xml.XmlNamespaceDictionary;
import com.google.api.client.xml.atom.AtomParser;

public class MainActivity extends Activity {
	private static final String TAG = "GoogleDocsTest";

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        GoogleHeaders headers = (GoogleHeaders) transport.defaultHeaders;
        headers.setApplicationName("Kokufu-GoogleDocsTest/1.0");
        headers.gdataVersion = "3";
        headers.setGoogleLogin(authToken); // 認証トークン設定

        // Parserを準備してTransportにセットする
        AtomParser parser = new AtomParser();
        // 空のDictionaryでとりあえず問題なさげ
        parser.namespaceDictionary = new XmlNamespaceDictionary();
        transport.addParser(parser);

        // 送信
        Feed feed = null;
        try {
            HttpRequest request = transport.buildGetRequest();
            request.url = new GoogleUrl("https://docs.google.com/feeds/default/private/full"); // ※2 
			feed = request.execute().parseAs(Feed.class);
		} catch (IOException e) {
            Log.e(TAG, "", e);
            return;
        }

/*
        // 結果を表示
        String tmp = "abcdefghijklmnopqrstuvwxyz";
        for(Entry entry : feed.entries) {
            tmp += entry.title + "\n";
        }
        TextView v = new TextView(this);
        v.setText(tmp);
        this.addContentView(
                v,
                new LayoutParams(LayoutParams.WRAP_CONTENT,
                                 LayoutParams.WRAP_CONTENT));
*/
    }

    /**
     * Feedタグ
     */
    private class Feed {
        @Key("entry")
        public List<Entry> entries = new ArrayList();
    }

    /**
     * Entryタグ
     */
    private class Entry {
        @Key
        public String summary;

        @Key
        public String title;

        @Key
        public String updated;
	}
}