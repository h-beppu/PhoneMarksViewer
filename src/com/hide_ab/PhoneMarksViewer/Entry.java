package com.hide_ab.PhoneMarksViewer;

import java.util.List;

import com.google.api.client.util.DateTime;
import com.google.api.client.util.Key;

public class Entry {
    @Key
    String title;

    @Key
	public List<Content> content;

    @Key
    public DateTime updated;
}