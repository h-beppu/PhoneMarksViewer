package com.hide_ab.PhoneMarksViewer;

public class LinkInfo {
	public static final int ITEM_FOLDER = 1;
	public static final int ITEM_SITE   = 2;

	private int ForU;
	private String Folder;
	private String Title;
	private String Url;

	// éÊìæ
	public int getForU() {
		return this.ForU;
	}
	public String getFolder() {
		return this.Folder;
	}
	public String getTitle() {
		return this.Title;
	}
	public String getUrl() {
		return this.Url;
	}

	// ê›íË
	public void setForU(int ForU) {
		this.ForU = ForU;
	}
	public void setFolder(String Folder) {
		this.Folder = Folder;
	}
	public void setTitle(String Title) {
		this.Title = Title;
	}
	public void setUrl(String Url) {
		this.Url = Url;
	}
}