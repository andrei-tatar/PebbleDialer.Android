package atatar.com.pebbledialer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;

/**
* Created by X550L-User1 on 08-Nov-14.
*/
abstract class EnhancedListAdapter<T> extends BaseAdapter {

    private final LayoutInflater mLayoutInflater;
    private final int layoutId;
    private List<T> mItems = new ArrayList<T>();

    public EnhancedListAdapter(LayoutInflater layoutInflater, int layoutId)
    {
        mLayoutInflater = layoutInflater;
        this.layoutId = layoutId;
    }

    public void remove(int position) {
        mItems.remove(position);
        notifyDataSetChanged();
    }

    public void insert(int position, T item) {
        mItems.add(position, item);
        notifyDataSetChanged();
    }

    public void update(int position, T item) {
        mItems.set(position, item);
        notifyDataSetChanged();
    }

    public void add(T item){
        mItems.add(item);
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public T getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView == null) {
            convertView = mLayoutInflater.inflate(layoutId, parent, false);
        }

        populateView(convertView, mItems.get(position));
        return convertView;
    }

    protected abstract void populateView(View view, T item);
}
