package com.shuvam.texteditor;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

/**
 * Created by Shuvam Ghosh on 9/8/2017.
 */

public class FileModel extends RealmObject {


    public String textContent;
    public String getTextContent() {
        return textContent;
    }
    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }
}
