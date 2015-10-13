package ru.mirea.oop.practice.coursej.tg.entities;

import com.google.gson.annotations.SerializedName;

public final class Sticker extends File {
    @SerializedName("width")
    public Integer width;
    @SerializedName("height")
    public Integer hwight;
    @SerializedName("thumb")
    public PhotoSize thumb;
}
