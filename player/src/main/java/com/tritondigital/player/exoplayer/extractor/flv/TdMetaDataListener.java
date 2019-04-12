package com.tritondigital.player.exoplayer.extractor.flv;

import java.util.*;

/**
 * Created by mkabore on 31/10/2016.
 */
public interface TdMetaDataListener {

    public static final String NAME_CUEPOINT = "onCuePoint";
    public static final String NAME_METADATA = "onMetaData";
    public static final String KEY_DURATION = "duration";
    public static final String KEY_TIMESTAMP = "timeStamp";
    public static final String KEY_NAME = "name";


    public void onMetaDataReceived(Map<String, Object> metadata);
}
