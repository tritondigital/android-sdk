package com.tritondigital.sdksample.player;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.tritondigital.player.CuePoint;
import com.tritondigital.player.CuePointHistory;
import com.tritondigital.sdksample.R;
import com.tritondigital.sdksample.Util;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;


/**
 * Shows how to get the cue point history of a mount.
 *
 * The ads might not be available depending on the mount configuration. The ads might also be
 * different than what the listener has heard if there is ad replacement with user targeting.
 */
public class CuePointHistoryActivity extends AppCompatActivity implements
        CuePointHistory.CuePointHistoryListener, CompoundButton.OnCheckedChangeListener,
        View.OnClickListener, TextView.OnEditorActionListener {

    // Default values
    private static final String DEFAULT_MOUNT     = "FLYFMAAC";
    private static final int    DEFAULT_MAX_ITEMS = 10;

    // Views
    private CheckBox mAdCheckBox;
    private CheckBox mTrackCheckBox;
    private EditText mMaxItemsEditText;
    private EditText mMountEditText;
    private ListView mListView;

    // Others
    private List<String[]>  mListItems = new LinkedList<>();
    private List<String>    mCueTypes = new ArrayList<>();

    private CuePointHistory mCuePointHistory;
    private Date mDate = new Date();
    private DateFormat mDateFormat;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.cue_point_history);

        mAdCheckBox = (CheckBox) findViewById(R.id.checkBox_ad);
        mAdCheckBox.setOnCheckedChangeListener(this);

        mTrackCheckBox = (CheckBox) findViewById(R.id.checkBox_track);
        mTrackCheckBox.setOnCheckedChangeListener(this);

        mMaxItemsEditText = (EditText) findViewById(R.id.editText_maxItems);
        mMaxItemsEditText.setOnEditorActionListener(this);

        mMountEditText = (EditText) findViewById(R.id.editText_mount);
        mMountEditText.setOnEditorActionListener(this);

        View refreshButton = findViewById(R.id.button_refresh);
        refreshButton.setOnClickListener(this);

        View resetButton = findViewById(R.id.button_reset);
        resetButton.setOnClickListener(this);

        mListView = (ListView)findViewById(android.R.id.list);
        mListView.setAdapter(new SimpleListItem2Adapter(this, mListItems));

        // Init time format
        mDateFormat = android.text.format.DateFormat.getTimeFormat(this);

        // Init parser
        mCuePointHistory = new CuePointHistory();
        mCuePointHistory.setListener(this);

        if (savedInstanceState == null) {
            reset();
        } else {
            refresh();
        }
    }


    @Override
    protected void onDestroy() {
        mCuePointHistory.cancelRequest();

        mAdCheckBox = null;
        mTrackCheckBox = null;
        mMaxItemsEditText = null;
        mMountEditText = null;

        super.onDestroy();
    }


    @Override
    public void onCuePointHistoryReceived(CuePointHistory src, List<Bundle> cuePoints) {
        mListItems.clear();

        if (cuePoints != null) {
            for (Bundle cuePoint : cuePoints) {
                // Time
                long timestamp = cuePoint.getLong(CuePoint.CUE_START_TIMESTAMP);
                mDate.setTime(timestamp);
                String timeStr = mDateFormat.format(mDate);

                // Cue point string
                String cuePointStr = cuePoint.toString();
                cuePointStr = cuePointStr.substring(8, (cuePointStr.length() - 2));

                mListItems.add(new String[]{timeStr, cuePointStr});
            }
        }

        if (mListItems.isEmpty()) {
            mListItems.add(new String[]{"No history", null});
        }

        // Update list view
        ((ArrayAdapter)mListView.getAdapter()).notifyDataSetChanged();
    }


    @Override
    public void onCuePointHistoryFailed(CuePointHistory src, int errorCode) {
        String errorDetail = CuePointHistory.debugErrorToStr(errorCode);
        mListItems.clear();
        mListItems.add(new String[]{"Error", errorDetail});
        ((ArrayAdapter)mListView.getAdapter()).notifyDataSetChanged();
    }


    private void reset() {
        mAdCheckBox.setChecked(false);
        mTrackCheckBox.setChecked(false);
        mMaxItemsEditText.setText(String.valueOf(DEFAULT_MAX_ITEMS));
        mMountEditText.setText(DEFAULT_MOUNT);

        refresh();
    }


    private void refresh() {
        Util.hideKeyboard(this);

        // Cue type filter
        mCueTypes.clear();

        if (mAdCheckBox.isChecked()) {
            mCueTypes.add(CuePoint.CUE_TYPE_VALUE_AD);
        }

        if (mTrackCheckBox.isChecked()) {
            mCueTypes.add(CuePoint.CUE_TYPE_VALUE_TRACK);
        }

        // Max items and mount
        int maxItems = getMaxItems();
        String mount = getMount();

        // Request
        mCuePointHistory.setCueTypeFilter(mCueTypes);
        mCuePointHistory.setMaxItems(maxItems);
        mCuePointHistory.setMount(mount);
        mCuePointHistory.request();
    }


    private int getMaxItems() {
        try {
            String maxItemsStr = mMaxItemsEditText.getText().toString().trim();
            return Integer.parseInt(maxItemsStr);
        } catch (Exception e) {
            mMaxItemsEditText.setText(String.valueOf(DEFAULT_MAX_ITEMS));
            return DEFAULT_MAX_ITEMS;
        }
    }


    private String getMount() {
        return mMountEditText.getText().toString().trim();
    }


    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        refresh();
    }


    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        refresh();
        return true;
    }


    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        if (viewId == R.id.button_refresh) {
            refresh();
        } else if (viewId == R.id.button_reset) {
            reset();
        }
    }
}
