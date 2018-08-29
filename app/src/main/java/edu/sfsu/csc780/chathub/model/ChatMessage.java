/**
 * Copyright Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.sfsu.csc780.chathub.model;

import java.util.Date;

public class ChatMessage {
    private String text;
    private String name;
    private String photoUrl;
    private int mediaType = 1;

    public long getTimestamp() {
        return timestamp;
    }

    private long timestamp;
    public static final long NO_TIMESTAMP = -1;

    public String getMediaUrl() {
        return mediaUrl;
    }

    private String mediaUrl;

    public ChatMessage() {
    }

    public ChatMessage(String text, String name, String photoUrl) {
        this.text = text;
        this.name = name;
        this.photoUrl = photoUrl;
        this.timestamp = new Date().getTime();
    }

    public ChatMessage(String text, String name, String photoUrl, String mediaUrl, int mediaType) {
        this(text, name, photoUrl);
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
    }

    public int getMediaType() {
        return mediaType;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }
}
