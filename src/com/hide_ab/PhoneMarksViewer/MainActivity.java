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

        // AccountManager��ʂ���Google�A�J�E���g���擾
        AccountManager manager = AccountManager.get(this);
        Account[] accounts = manager.getAccountsByType("com.google");
        Bundle bundle = null;
        try {
            bundle = manager.getAuthToken(
                    accounts[0], // �e�X�g�Ȃ̂ŌŒ�
                    "writely",   // ��1
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
            // �F�؂��K�v�ȏꍇ
            Intent intent = bundle.getParcelable(AccountManager.KEY_INTENT);
            int flags = intent.getFlags();
            flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
            intent.setFlags(flags);
            startActivityForResult(intent, 0);
            // �{����Result���󂯂Ƃ�K�v�����邯�Ǌ���
            return;
        } else {
            // �F�ؗp�g�[�N���擾
            authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
        }

        // ���M����
        HttpTransport transport = GoogleTransport.create();
        GoogleHeaders headers = (GoogleHeaders) transport.defaultHeaders;
        headers.setApplicationName("Kokufu-GoogleDocsTest/1.0");
        headers.gdataVersion = "3";
        headers.setGoogleLogin(authToken); // �F�؃g�[�N���ݒ�

        // Parser����������Transport�ɃZ�b�g����
        AtomParser parser = new AtomParser();
        // ���Dictionary�łƂ肠�������Ȃ���
        parser.namespaceDictionary = new XmlNamespaceDictionary();
        transport.addParser(parser);

        // ���M
        Feed feed = null;
        try {
            HttpRequest request = transport.buildGetRequest();
            request.url = new GoogleUrl("https://docs.google.com/feeds/default/private/full"); // ��2 
			feed = request.execute().parseAs(Feed.class);
		} catch (IOException e) {
            Log.e(TAG, "", e);
            return;
        }

/*
        // ���ʂ�\��
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
     * Feed�^�O
     */
    private class Feed {
        @Key("entry")
        public List<Entry> entries = new ArrayList();
    }

    /**
     * Entry�^�O
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