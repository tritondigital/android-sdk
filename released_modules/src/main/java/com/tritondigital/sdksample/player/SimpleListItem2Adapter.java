package com.tritondigital.sdksample.player;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/**
* Basic list adapter for a 2D string array
*/
class SimpleListItem2Adapter extends ArrayAdapter<String[]>
{
    LayoutInflater mInflater;

    private class ViewHolder {
        TextView mTextView1;
        TextView mTextView2;
    }


    public SimpleListItem2Adapter(Context context, List<String[]> items)
    {
        super(context, android.R.layout.simple_list_item_2, items);
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        ViewHolder viewHolder;

        if (convertView == null)
        {
            viewHolder = new ViewHolder();

            convertView = mInflater.inflate(android.R.layout.simple_list_item_2, parent, false);
            viewHolder.mTextView1 = (TextView)convertView.findViewById(android.R.id.text1);
            viewHolder.mTextView2 = (TextView)convertView.findViewById(android.R.id.text2);
            convertView.setTag(viewHolder);
        }
        else
        {
            viewHolder = (ViewHolder)convertView.getTag();
        }

        String[] entry = getItem(position);
        viewHolder.mTextView1.setText(entry[0]);
        viewHolder.mTextView2.setText(entry[1]);
        return convertView;
    }
}
