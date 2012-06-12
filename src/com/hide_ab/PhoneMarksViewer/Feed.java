package com.hide_ab.PhoneMarksViewer;

import java.util.List;

import com.google.api.client.util.Key;

public class Feed {
	@Key
	public String title;

	@Key
	public List<Entry> entry;
}