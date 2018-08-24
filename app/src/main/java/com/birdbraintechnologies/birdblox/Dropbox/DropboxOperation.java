package com.birdbraintechnologies.birdblox.Dropbox;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public enum DropboxOperation {

    DOWNLOAD("Download"),
    UPLOAD("Upload"),
    RENAME("Rename"),
    DELETE("Delete");

    private final String name;

    DropboxOperation(String s) {
        name = s;
    }

    public boolean equalsName(String otherName) {
        return name.equals(otherName);
    }

    public String toString() {
        return this.name;
    }

}
