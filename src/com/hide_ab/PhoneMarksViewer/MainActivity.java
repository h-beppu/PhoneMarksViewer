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
	// �\������f�[�^�̃��X�g
	protected ArrayList<LinkInfo> List = null;
	// ListAdapter
	private LinkListAdapter linklistadapter = null;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		this.List = new ArrayList<LinkInfo>();

		// ��ʍ\����K�p
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
        GoogleHeaders headers = (GoogleHeaders)transport.defaultHeaders;
        headers.setApplicationName("Kokufu-GoogleDocsTest/1.0");
        headers.gdataVersion = "3";
        headers.setGoogleLogin(authToken); // �F�؃g�[�N���ݒ�

        // Parser����������Transport�ɃZ�b�g����
        AtomParser parser = new AtomParser();
        // ���Dictionary�łƂ肠�������Ȃ���
        parser.namespaceDictionary = new XmlNamespaceDictionary();
        transport.addParser(parser);

        // ���M&�h�L�������g�ꗗXML�擾
        Feed feed = null;
        try {
            HttpRequest request = transport.buildGetRequest();
            request.url = new GoogleUrl("https://docs.google.com/feeds/default/private/full"); // ��2 
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

                	// PlainText�_�E�����[�hURL
                	HttpRequest request = transport.buildGetRequest();
                	request.url = new GoogleUrl(content.src + "&format=txt");
                	
                	// PlainText�Ƃ��Ď擾
        			HttpResponse response = request.execute();
        			Items = response.parseAsString();
        			Items = Items.replaceAll("&", "&amp;");

            		//XML�p�[�T�[�𐶐�����
            		XmlPullParser parser2 = Xml.newPullParser();
            		//XML�p�[�T�ɉ�͂��������e��ݒ肷��
            		parser2.setInput(new StringReader(Items));
/*
            		//�t�@�C���o�̓X�g���[���̍쐬
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
            						// �u�b�N�}�[�N�����X�g�ɒǉ�
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

        // ���ʂ�\��
//        TextView text1 = (TextView)findViewById(R.id.text1);
//        text1.setText(Entries);

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
	    		if(linkInfo.getUrl() != "") {
	    			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(linkInfo.getUrl()));
	        		startActivity(intent);
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
				TextView tvTitle = (TextView)view.findViewById(R.id.Title);
				if(tvTitle != null) {
					tvTitle.setText(linkInfo.getTitle());
				}
			}
			return view;
		}
	}
}